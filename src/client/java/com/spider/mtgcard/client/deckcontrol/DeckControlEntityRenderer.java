package com.spider.mtgcard.client.deckcontrol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class DeckControlEntityRenderer implements BlockEntityRenderer<DeckControlBlockEntity, DeckControlEntityRenderer.State> {

    // ✅ Exists in 1.21.11: enchanting-table glyph sheet
    private static final Identifier SGA_TEX = Identifier.fromNamespaceAndPath("minecraft", "textures/font/ascii_sga.png");
    // 26 real SGA glyph textures (the ones enchant.json references)
    private static final Identifier[] SGA = new Identifier[26];
    static {
        for (int i = 0; i < 26; i++) {
            char c = (char) ('a' + i);
            SGA[i] = Identifier.fromNamespaceAndPath("minecraft", "textures/particle/sga_" + c + ".png");
        }
    }

    // 16x16 glyph grid in ascii_sga.png
    private static final float CELL = 1.0f / 16.0f;

    public static class State extends BlockEntityRenderState {
        public BlockPos pos = BlockPos.ZERO;
        public boolean hasDeckbox;
        public boolean hasGraveyard;
        public double time;

        public Direction facing = Direction.UP; // NEW
    }

    public DeckControlEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            DeckControlBlockEntity be,
            State state,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(be, state, tickProgress, cameraPos, crumblingOverlay);

        Level world = be.getLevel();
        if (world == null) return;

        BlockPos pos = be.getBlockPos();
        state.pos = pos;

        state.hasDeckbox = hasAdjacentDeckbox(world, pos);
        state.hasGraveyard = hasAdjacent(world, pos, ModBlocks.GRAVEYARD);

        BlockState bs = world.getBlockState(pos);
        if (bs.hasProperty(com.spider.mtgcard.deckcontrol.DeckControlBlock.FACING)) {
            state.facing = bs.getValue(com.spider.mtgcard.deckcontrol.DeckControlBlock.FACING);
        } else {
            state.facing = Direction.UP;
        }

        // smooth animation time
        double seed = (pos.asLong() & 0xFFL) * 0.01;
        state.time = world.getGameTime() + tickProgress + seed;
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!state.hasDeckbox && !state.hasGraveyard) return;

        int fullBright = LightTexture.FULL_BRIGHT;

        matrices.pushPose();
        try {
            if (state.hasDeckbox) {
                renderRing(queue, cameraState, matrices, fullBright, state.time,
                        state.facing,
                        0.30, 0.80, 28, -0.08);
            }

            if (state.hasGraveyard) {
                renderRing(queue, cameraState, matrices, fullBright, state.time,
                        state.facing,
                        0.60, 0.30, 40, +0.06);
            }
        } finally {
            matrices.popPose();
        }
    }

    private static boolean hasAdjacent(Level world, BlockPos pos, net.minecraft.world.level.block.Block block) {
        for (Direction d : Direction.values()) { // includes UP + DOWN
            if (world.getBlockState(pos.relative(d)).is(block)) return true;
        }
        return false;
    }

    private static boolean hasAdjacentDeckbox(Level world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (ModBlocks.isDeckbox(world.getBlockState(pos.relative(d)))) return true;
        }
        return false;
    }

    private static void renderRing(
            SubmitNodeCollector queue,
            CameraRenderState cameraState,
            PoseStack matrices,
            int light,
            double time,
            Direction facing,
            double radius,
            double y,
            int count,
            double omega
    ) {
        final double step = Mth.TWO_PI / (double) count;
        final double base = time * omega;

        final double chord = 2.0 * radius * Math.sin(step * 0.5);
        float size = (float) (chord * 0.42);
        size = Mth.clamp(size, 0.02f, 0.20f);

        for (int i = 0; i < count; i++) {
            final double a = base + i * step;

            float px = (float) (Math.cos(a) * radius);
            float pz = (float) (Math.sin(a) * radius);

            // Build the full point in local block space (same as your original intent)
            // Local center of ring is (0.5, y, 0.5)
            float lx = 0.5f + px;
            float ly = (float) y;
            float lz = 0.5f + pz;

            // Rotate THAT point around block center, so it stays glued to the block
            F3 p = rotatePointAroundCenter(lx, ly, lz, facing);

            Identifier glyphTex = SGA[i % 26];
            RenderType layer = RenderTypes.entityCutoutNoCull(glyphTex);

            matrices.pushPose();
            matrices.translate(p.x, p.y, p.z);

            // Billboard in world space (correct for all facings)
            matrices.mulPose(cameraState.orientation);

            matrices.scale(size, size, size);

            queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.pose();
                int overlay = OverlayTexture.NO_OVERLAY;
                int alpha = 220;

                put(vc, mat, -1f, -1f, 0f, 0f, 1f, light, overlay, alpha);
                put(vc, mat, -1f,  1f, 0f, 0f, 0f, light, overlay, alpha);
                put(vc, mat,  1f,  1f, 0f, 1f, 0f, light, overlay, alpha);
                put(vc, mat,  1f, -1f, 0f, 1f, 1f, light, overlay, alpha);
            });

            matrices.popPose();
        }
    }

    private record F3(float x, float y, float z) {}

    private static F3 rotatePointAroundCenter(float x, float y, float z, Direction facing) {
        // translate point into center-relative coordinates
        float dx = x - 0.5f;
        float dy = y - 0.5f;
        float dz = z - 0.5f;

        // rotate the delta so local +Y maps to the facing direction
        F3 r = rotateDelta(dx, dy, dz, facing);

        // translate back
        return new F3(0.5f + r.x, 0.5f + r.y, 0.5f + r.z);
    }

    private static F3 rotateDelta(float x, float y, float z, Direction facing) {
        return switch (facing) {
            case UP -> new F3(x, y, z);
            case DOWN -> new F3(x, -y, -z);

            // +Y -> -Z
            case NORTH -> new F3(x, z, -y);
            // +Y -> +Z
            case SOUTH -> new F3(x, -z, y);

            // +Y -> -X
            case WEST -> new F3(-y, x, z);
            // +Y -> +X
            case EAST -> new F3(y, -x, z);
        };
    }

    private static void put(VertexConsumer vc, Matrix4f mat,
                            float x, float y, float z,
                            float u, float v,
                            int light, int overlay,
                            int alpha) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0f, 0f, 1f);
    }
}
