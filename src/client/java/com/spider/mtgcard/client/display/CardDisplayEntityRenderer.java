package com.spider.mtgcard.client.display;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

public class CardDisplayEntityRenderer extends EntityRenderer<CardDisplayEntity, CardDisplayEntityRenderer.State> {

    // Use constants instead of resource probing / image decode (avoids early RenderSystem/device issues)
    private static final Identifier TEX_WHITE = Identifier.of("minecraft", "textures/misc/white.png");
    private static final Identifier TEX_BACK  = Identifier.of("mtgcard", "textures/gui/card.png");

    // Pick a stable aspect ratio for card back. (These are the values you already used as defaults.)
    private static final int BACK_W = 488;
    private static final int BACK_H = 680;

    private final TextRenderer textRenderer;

    public static class State extends EntityRenderState {
        public ItemStack stack = ItemStack.EMPTY;
        public Identifier texId = TEX_WHITE;
        public int texW = BACK_W;
        public int texH = BACK_H;
        public Direction facing = Direction.UP;
        public int rotStep = 0;
        public int faceIndex = 0;
        public int flatYawStep = 0;
    }

    public CardDisplayEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.textRenderer = ctx.getTextRenderer();
        Mtgcard.LOGGER.info("[CardDisplay] CardDisplayEntityRenderer constructed");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(CardDisplayEntity entity, State s, float tickDelta) {
        super.updateRenderState(entity, s, tickDelta); // REQUIRED in 1.21.11+

        s.stack = entity.getStack();
        s.facing = entity.getFacing();
        s.rotStep = entity.getRotStep();
        s.flatYawStep = entity.getFlatYawStep();

        if (s.stack == null || s.stack.isEmpty()) {
            s.texId = TEX_WHITE;
            s.texW = 16;
            s.texH = 16;
            s.faceIndex = 0;
            return;
        }

        s.faceIndex = readFaceIndex(s.stack);

        boolean hidden = readHiddenFlagFromStack(s.stack);
        if (hidden) {
            // IMPORTANT: do NOT decode the texture here (can run during early init on some systems)
            s.texId = TEX_BACK;
            s.texW = BACK_W;
            s.texH = BACK_H;
            s.faceIndex = 0;
            return;
        }

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(s.stack, s.faceIndex);
        if (ref != null && ref.id() != null) {
            s.texId = ref.id();
            s.texW = (ref.texW() > 0) ? ref.texW() : 256;
            s.texH = (ref.texH() > 0) ? ref.texH() : 256;
        } else {
            s.texId = fallbackItemTexture(s.stack);
            s.texW = 256;
            s.texH = 256;
        }
    }

    private void renderCounterStripOnCard(
            State s,
            MatrixStack matrices,
            TextRenderer tr,
            OrderedRenderCommandQueue queue
    ) {
        if (s.stack == null || s.stack.isEmpty()) return;

        NbtCompound counters = readCounters(s.stack);
        if (counters.getKeys().isEmpty()) return;

        // Collect nonzero keys
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String k : counters.getKeys()) {
            int v = counters.getInt(k).orElse(0);
            if (v > 0) keys.add(k);
        }
        if (keys.isEmpty()) return;

        keys.sort(String::compareToIgnoreCase);
        if (keys.size() > 6) keys = keys.subList(0, 6);

        // Layout in local quad space
        final float padX = 0.04f;
        final float padY = 0.05f;
        final float icon = 0.10f;
        final float gapY = 0.02f;

        float startX = -0.5f + padX;
        float startY =  0.5f - padY;

        matrices.push();
        matrices.translate(0f, 0f, 0.012f);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int v = counters.getInt(key).orElse(0);
            String txt = formatCounterValue(v);
            if (txt.isEmpty()) continue;

            float x = startX;
            float y = startY - i * (icon + gapY);

            String iconKey = readCounterIcon(s.stack, key);
            if (iconKey != null && !iconKey.equals("none")) {
                Identifier iconTex = Identifier.of("mtgcard", "textures/gui/counters/" + iconKey + ".png");
                var layer = RenderLayers.entityCutoutNoCull(iconTex);

                queue.submitCustom(matrices, layer, (entry, vc) -> {
                    Matrix4f mat = entry.getPositionMatrix();
                    int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

                    float x0 = x;
                    float y0 = y - icon; // bottom
                    float x1 = x + icon;
                    float y1 = y;        // top

                    // Vertex order: bottom-left, top-left, top-right, bottom-right
                    // UVs:          (0,1)      (0,0)     (1,0)     (1,1)
                    put(vc, mat, x0, y0, 0f, 0f, 1f, fullLight, OverlayTexture.DEFAULT_UV);
                    put(vc, mat, x0, y1, 0f, 0f, 0f, fullLight, OverlayTexture.DEFAULT_UV);
                    put(vc, mat, x1, y1, 0f, 1f, 0f, fullLight, OverlayTexture.DEFAULT_UV);
                    put(vc, mat, x1, y0, 0f, 1f, 1f, fullLight, OverlayTexture.DEFAULT_UV);

                });
            }

            // (Optional later) number rendering can go here, but avoid MinecraftClient.getInstance() calls.
        }

        matrices.pop();
    }

    @Override
    public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (s.stack == null || s.stack.isEmpty()) return;

        var layer = RenderLayers.entityCutoutNoCull(s.texId);

        matrices.push();

        // Build a quad in XY plane facing +Z, then rotate it to match the entity face
        orientQuadToFace(matrices, s.facing);
        if (s.facing == Direction.UP || s.facing == Direction.DOWN) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s.flatYawStep * 90f));
        }

        // tiny push away from the block face to avoid z-fighting
        matrices.translate(0f, 0f, 0.01f);

        // rotate around the face normal (local Z axis after orient)
        float degrees = (s.rotStep == 1) ? 45f : (s.rotStep == 2 ? 90f : 0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-degrees));

        // aspect ratio sizing (XY plane)
        float ar = (float) s.texW / (float) s.texH;
        float halfW, halfH;
        if (ar >= 1f) {
            halfW = 0.5f;
            halfH = 0.5f / ar;
        } else {
            halfW = 0.5f * ar;
            halfH = 0.5f;
        }

        // Flip V so textures are not upside down
        final float u0 = 0f, u1 = 1f;
        final float v0 = 1f, v1 = 0f;

        queue.submitCustom(matrices, layer, (entry, vc) -> {
            Matrix4f mat = entry.getPositionMatrix();
            int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

            put(vc, mat, -halfW, -halfH, 0f, u0, v0, fullLight, OverlayTexture.DEFAULT_UV);
            put(vc, mat, -halfW,  halfH, 0f, u0, v1, fullLight, OverlayTexture.DEFAULT_UV);
            put(vc, mat,  halfW,  halfH, 0f, u1, v1, fullLight, OverlayTexture.DEFAULT_UV);
            put(vc, mat,  halfW, -halfH, 0f, u1, v0, fullLight, OverlayTexture.DEFAULT_UV);
        });

        // After drawing the big card quad
        renderCounterStripOnCard(s, matrices, this.textRenderer, queue);

        matrices.pop();
    }

    private static void put(VertexConsumer vc, Matrix4f mat,
                            float x, float y, float z,
                            float u, float v,
                            int light, int overlay) {
        vc.vertex(mat, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(overlay)
                .light(light)
                .normal(0f, 0f, 1f);
    }

    private static Identifier fallbackItemTexture(ItemStack stack) {
        Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        return Identifier.of(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
    }

    private static void orientQuadToFace(MatrixStack matrices, Direction facing) {
        // Quad starts facing +Z (SOUTH) in local space
        switch (facing) {
            case SOUTH -> { /* no rotation */ }
            case NORTH -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            case WEST  -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
            case EAST  -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
            case UP    -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f)); // +Z -> +Y
            case DOWN  -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));  // +Z -> -Y
        }
    }

    private static NbtCompound readMeta(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        return root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
    }

    private static NbtCompound readCounters(ItemStack st) {
        return readMeta(st).getCompound("counters").orElseGet(NbtCompound::new);
    }

    private static String readCounterIcon(ItemStack st, String key) {
        NbtCompound icons = readMeta(st).getCompound("counter_icons").orElse(null);
        if (icons == null) return "none";
        return icons.getString(key).orElse("none");
    }

    private static String formatCounterValue(int v) {
        if (v <= 0) return "";
        if (v > 99) return "99+";
        return String.valueOf(v);
    }

    private static boolean readHiddenFlagFromStack(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        return meta.getBoolean("mtg_hidden").orElse(false);
    }

    private static int readFaceIndex(ItemStack stack) {
        var comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        return meta.getInt("mtg_face").orElse(0);
    }
}
