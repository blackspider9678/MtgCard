package com.spider.mtgcard.client.life;

import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class LifePointFrontTextRenderer
        implements BlockEntityRenderer<LifePointBlockEntity, LifePointFrontTextRenderer.State> {

    public static final class State extends BlockEntityRenderState {
        public boolean render;
        public int life;
        public int rgb;          // 0xRRGGBB
        public Direction facing; // front face
    }

    private final Font textRenderer;

    public LifePointFrontTextRenderer(BlockEntityRendererProvider.Context ctx) {
        // ctx.getTextRenderer() wasn't available in your mapping errors, so use MC directly (stable).
        this.textRenderer = Minecraft.getInstance().font;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            LifePointBlockEntity be,
            State st,
            float tickDelta,
            Vec3 cameraPos,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(be, st, tickDelta, cameraPos, crumblingOverlay);

        // Only show during a running game (your requirement)
        st.render = be.isGameStarted();

        st.life = be.getLife();
        st.rgb = be.getLifeColor() & 0xFFFFFF;

        BlockState bs = be.getBlockState();
        Direction f = Direction.NORTH;

        // Prefer horizontal facing if present
        if (bs != null) {
            if (bs.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                f = bs.getValue(BlockStateProperties.HORIZONTAL_FACING);
            } else if (bs.hasProperty(BlockStateProperties.FACING)) {
                f = bs.getValue(BlockStateProperties.FACING);
            }
        }

        st.facing = f;
    }

    @Override
    public void submit(State st, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
        if (!st.render) return;

        // Text to render
        Component t = Component.literal(Integer.toString(st.life));
        FormattedCharSequence ot = t.getVisualOrderText();

        int textW = this.textRenderer.width(ot);

        matrices.pushPose();

        // Center of block
        matrices.translate(0.5, 0.5, 0.5);

        // Rotate to face the block "front"
        float yawDeg = yawForFacing(st.facing);
        matrices.mulPose(new Quaternionf().rotationY((float) Math.toRadians(yawDeg)));

        // Push slightly out in front of the face
        matrices.translate(0.0, 0.0, 0.501);

        // Scale down (minecraft font is big in world space)
        float s = 0.0105f; // tweak if you want bigger/smaller
        matrices.scale(-s, -s, s); // flip X/Y so it isn't mirrored/upside down

        // Center text
        float x = -textW / 2.0f;
        float y = -4.0f;

        int argb = 0xFF000000 | (st.rgb & 0xFFFFFF);
        int light = 0x00F000F0; // fullbright
        int bg = 0;             // no background
        int overlay = 0;

        queue.submitText(
                matrices,
                x, y,
                ot,
                false,
                Font.DisplayMode.NORMAL,
                argb,
                bg,
                light,
                overlay
        );

        matrices.popPose();
    }

    private static float yawForFacing(Direction f) {
        // “Front face” yaw mapping (adjust if your model’s notion of “front” differs)
        return switch (f) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case EAST  -> -90f;
            default    -> 180f;
        };
    }
}
