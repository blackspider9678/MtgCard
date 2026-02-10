package com.spider.mtgcard.client.deckcontrol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spider.mtgcard.deckcontrol.DeckControlBlock;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.registry.ModBlocks;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class DeckControlEntityRenderer implements BlockEntityRenderer<DeckControlBlockEntity, DeckControlEntityRenderer.State> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Identifier[] SGA = new Identifier[26];

    static {
        for (int i = 0; i < 26; i++) {
            char c = (char) ('a' + i);
            SGA[i] = Identifier.fromNamespaceAndPath("minecraft", "textures/particle/sga_" + c + ".png");
        }
    }

    public static class State extends BlockEntityRenderState {
        public BlockPos pos = BlockPos.ZERO;
        public boolean hasDeckbox;
        public boolean hasGraveyard;
        public double time;
        public Direction facing = Direction.UP;
    }

    public DeckControlEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

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

        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        state.pos = pos;
        state.hasDeckbox = hasAdjacent(level, pos, ModBlocks.DECKBOX);
        state.hasGraveyard = hasAdjacent(level, pos, ModBlocks.GRAVEYARD);

        BlockState blockState = level.getBlockState(pos);
        state.facing = blockState.hasProperty(DeckControlBlock.FACING)
                ? blockState.getValue(DeckControlBlock.FACING)
                : Direction.UP;

        double seed = (pos.asLong() & 0xFFL) * 0.01;
        state.time = level.getGameTime() + tickProgress + seed;
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!state.hasDeckbox && !state.hasGraveyard) {
            return;
        }

        matrices.pushPose();
        try {
            if (state.hasDeckbox) {
                renderRing(queue, cameraState, matrices, state.time, state.facing, 0.30, 0.80, 28, -0.08);
            }
            if (state.hasGraveyard) {
                renderRing(queue, cameraState, matrices, state.time, state.facing, 0.60, 0.30, 40, 0.06);
            }
        } finally {
            matrices.popPose();
        }
    }

    private static boolean hasAdjacent(Level level, BlockPos pos, Block block) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(block)) {
                return true;
            }
        }
        return false;
    }

    private static void renderRing(
            SubmitNodeCollector queue,
            CameraRenderState cameraState,
            PoseStack matrices,
            double time,
            Direction facing,
            double radius,
            double y,
            int count,
            double omega
    ) {
        double step = (Math.PI * 2.0) / (double) count;
        double base = time * omega;

        double chord = 2.0 * radius * Math.sin(step * 0.5);
        float size = (float) (chord * 0.42);
        size = Math.max(0.02f, Math.min(0.20f, size));

        for (int i = 0; i < count; i++) {
            double angle = base + i * step;
            float px = (float) (Math.cos(angle) * radius);
            float pz = (float) (Math.sin(angle) * radius);

            F3 point = rotatePointAroundCenter(0.5f + px, (float) y, 0.5f + pz, facing);
            RenderType layer = RenderTypes.entityCutoutNoCull(SGA[i % SGA.length]);

            matrices.pushPose();
            matrices.translate(point.x, point.y, point.z);
            matrices.mulPose(cameraState.orientation);
            matrices.scale(size, size, size);

            queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.pose();
                int overlay = OverlayTexture.NO_OVERLAY;
                int alpha = 220;

                put(vc, mat, -1f, -1f, 0f, 0f, 1f, overlay, alpha);
                put(vc, mat, -1f, 1f, 0f, 0f, 0f, overlay, alpha);
                put(vc, mat, 1f, 1f, 0f, 1f, 0f, overlay, alpha);
                put(vc, mat, 1f, -1f, 0f, 1f, 1f, overlay, alpha);
            });

            matrices.popPose();
        }
    }

    private record F3(float x, float y, float z) {
    }

    private static F3 rotatePointAroundCenter(float x, float y, float z, Direction facing) {
        F3 rotated = rotateDelta(x - 0.5f, y - 0.5f, z - 0.5f, facing);
        return new F3(0.5f + rotated.x, 0.5f + rotated.y, 0.5f + rotated.z);
    }

    private static F3 rotateDelta(float x, float y, float z, Direction facing) {
        return switch (facing) {
            case UP -> new F3(x, y, z);
            case DOWN -> new F3(x, -y, -z);
            case NORTH -> new F3(x, z, -y);
            case SOUTH -> new F3(x, -z, y);
            case WEST -> new F3(-y, x, z);
            case EAST -> new F3(y, -x, z);
        };
    }

    private static void put(
            VertexConsumer vc,
            Matrix4f mat,
            float x,
            float y,
            float z,
            float u,
            float v,
            int overlay,
            int alpha
    ) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(FULL_BRIGHT)
                .setNormal(0f, 0f, 1f);
    }
}
