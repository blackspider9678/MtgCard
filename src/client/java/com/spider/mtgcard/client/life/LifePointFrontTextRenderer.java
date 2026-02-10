package com.spider.mtgcard.client.life;

import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;

public final class LifePointFrontTextRenderer
        implements BlockEntityRenderer<LifePointBlockEntity, LifePointFrontTextRenderer.State> {

    public static final class State extends BlockEntityRenderState {
        public boolean render;
        public int life;
        public int rgb;          // 0xRRGGBB
        public Direction facing; // front face
    }

    private final TextRenderer textRenderer;

    public LifePointFrontTextRenderer(BlockEntityRendererFactory.Context ctx) {
        // ctx.getTextRenderer() wasn't available in your mapping errors, so use MC directly (stable).
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    public void updateRenderState(LifePointBlockEntity be, State st, float tickDelta) {
        // Only show during a running game (your requirement)
        st.render = be.isGameStarted();

        st.life = be.getLife();
        st.rgb = be.getLifeColor() & 0xFFFFFF;

        BlockState bs = be.getCachedState();
        Direction f = Direction.NORTH;

        // Prefer horizontal facing if present
        if (bs != null) {
            if (bs.contains(Properties.HORIZONTAL_FACING)) {
                f = bs.get(Properties.HORIZONTAL_FACING);
            } else if (bs.contains(Properties.FACING)) {
                f = bs.get(Properties.FACING);
            }
        }

        st.facing = f;
    }

    @Override
    public void render(State st, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (!st.render) return;

        // Text to render
        Text t = Text.literal(Integer.toString(st.life));
        OrderedText ot = t.asOrderedText();

        int textW = this.textRenderer.getWidth(ot);

        matrices.push();

        // Center of block
        matrices.translate(0.5, 0.5, 0.5);

        // Rotate to face the block "front"
        float yawDeg = yawForFacing(st.facing);
        matrices.multiply(new Quaternionf().rotationY((float) Math.toRadians(yawDeg)));

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
                TextRenderer.TextLayerType.NORMAL,
                argb,
                bg,
                light,
                overlay
        );

        matrices.pop();
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
