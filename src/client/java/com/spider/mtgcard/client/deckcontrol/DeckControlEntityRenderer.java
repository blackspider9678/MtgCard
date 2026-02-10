package com.spider.mtgcard.client.deckcontrol;

import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class DeckControlEntityRenderer implements BlockEntityRenderer<DeckControlBlockEntity, DeckControlEntityRenderer.State> {

    // ✅ Exists in 1.21.11: enchanting-table glyph sheet
    private static final Identifier SGA_TEX = Identifier.of("minecraft", "textures/font/ascii_sga.png");
    // 26 real SGA glyph textures (the ones enchant.json references)
    private static final Identifier[] SGA = new Identifier[26];
    static {
        for (int i = 0; i < 26; i++) {
            char c = (char) ('a' + i);
            SGA[i] = Identifier.of("minecraft", "textures/particle/sga_" + c + ".png");
        }
    }

    // 16x16 glyph grid in ascii_sga.png
    private static final float CELL = 1.0f / 16.0f;

    public static class State extends BlockEntityRenderState {
        public BlockPos pos = BlockPos.ORIGIN;
        public boolean hasDeckbox;
        public boolean hasGraveyard;
        public double time;
    }

    public DeckControlEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(
            DeckControlBlockEntity be,
            State state,
            float tickProgress,
            Vec3d cameraPos,
            @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay
    ) {
        BlockEntityRenderer.super.updateRenderState(be, state, tickProgress, cameraPos, crumblingOverlay);

        World world = be.getWorld();
        if (world == null) return;

        BlockPos pos = be.getPos();
        state.pos = pos;

        state.hasDeckbox = hasAdjacent(world, pos, ModBlocks.DECKBOX);
        state.hasGraveyard = hasAdjacent(world, pos, ModBlocks.GRAVEYARD);

        // smooth animation time
        double seed = (pos.asLong() & 0xFFL) * 0.01;
        state.time = world.getTime() + tickProgress + seed;
    }

    @Override
    public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (!state.hasDeckbox && !state.hasGraveyard) return;

        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        // cutout looks crisp; translucent is softer. Pick one.
        RenderLayer layer = RenderLayers.entityCutoutNoCull(SGA_TEX);

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);

        if (state.hasDeckbox) {
            renderRing(queue, cameraState, matrices, layer, fullBright, state.time,
                    0.30, 0.80, 28, -0.08);
        }

        if (state.hasGraveyard) {
            renderRing(queue, cameraState, matrices, layer, fullBright, state.time,
                    0.60, 0.30, 40, +0.06);
        }

        matrices.pop();
    }

    private static boolean hasAdjacent(World world, BlockPos pos, net.minecraft.block.Block block) {
        for (Direction d : Direction.values()) { // includes UP + DOWN
            if (world.getBlockState(pos.offset(d)).isOf(block)) return true;
        }
        return false;
    }

    private static void renderRing(
            OrderedRenderCommandQueue queue,
            CameraRenderState cameraState,
            MatrixStack matrices,
            RenderLayer unusedLayer, // keep param so your calls don't change
            int light,
            double time,
            double radius,
            double y,
            int count,
            double omega
    ) {
        // Even angular spacing
        final double step = MathHelper.TAU / (double) count;
        final double base = time * omega;

        // Chord distance between adjacent points on the circle
        final double chord = 2.0 * radius * Math.sin(step * 0.5);

        // Quad is -1..+1 => width in world is about (2 * size).
        // Keep 2*size < chord for guaranteed no overlap.
        float size = (float) (chord * 0.42);       // tune 0.38–0.46
        size = MathHelper.clamp(size, 0.02f, 0.20f);

        for (int i = 0; i < count; i++) {
            final double a = base + i * step;

            final double px = Math.cos(a) * radius;
            final double pz = Math.sin(a) * radius;

            // Use only the real 26 glyph textures (no blanks)
            Identifier glyphTex = SGA[i % 26];
            RenderLayer layer = RenderLayers.entityCutoutNoCull(glyphTex); // or entityTranslucent if you want softer

            matrices.push();
            matrices.translate(px, y, pz);

            // Billboard to camera (this is fine; do NOT add extra rotation if you want "perfectly uniform")
            matrices.multiply(cameraState.orientation);

            matrices.scale(size, size, size);

            queue.submitCustom(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.getPositionMatrix();
                int overlay = OverlayTexture.DEFAULT_UV;
                int alpha = 220;

                // full texture UVs (each glyph is its own texture)
                put(vc, mat, -1f, -1f, 0f, 0f, 1f, light, overlay, alpha);
                put(vc, mat, -1f,  1f, 0f, 0f, 0f, light, overlay, alpha);
                put(vc, mat,  1f,  1f, 0f, 1f, 0f, light, overlay, alpha);
                put(vc, mat,  1f, -1f, 0f, 1f, 1f, light, overlay, alpha);
            });

            matrices.pop();
        }
    }

    private static void put(VertexConsumer vc, Matrix4f mat,
                            float x, float y, float z,
                            float u, float v,
                            int light, int overlay,
                            int alpha) {
        vc.vertex(mat, x, y, z)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(0f, 0f, 1f);
    }
}
