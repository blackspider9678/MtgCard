package com.spider.mtgcard.client.display;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.client.gui.Font;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

public class CardDisplayEntityRenderer extends EntityRenderer<CardDisplayEntity, CardDisplayEntityRenderer.State> {

    // Use constants instead of resource probing / image decode (avoids early RenderSystem/device issues)
    private static final Identifier TEX_WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final Identifier TEX_BACK  = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card.png");

    // Pick a stable aspect ratio for card back. (These are the values you already used as defaults.)
    private static final int BACK_W = 488;
    private static final int BACK_H = 680;

    private final Font textRenderer;

    private static final int FULL_BRIGHT = 0x00F000F0;

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

    public CardDisplayEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.textRenderer = ctx.getFont();
        Mtgcard.LOGGER.info("[CardDisplay] CardDisplayEntityRenderer constructed");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CardDisplayEntity entity, State s, float tickDelta) {
        super.extractRenderState(entity, s, tickDelta); // REQUIRED in 1.21.11+

        s.stack = entity.getStack();
        s.facing = entity.getNearestViewDirection();
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
            PoseStack matrices,
            Font tr,
            SubmitNodeCollector queue
    ) {
        if (s.stack == null || s.stack.isEmpty()) return;

        CompoundTag counters = readCounters(s.stack);
        if (counters.keySet().isEmpty()) return;

        // Collect nonzero keys
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String k : counters.keySet()) {
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

        matrices.pushPose();
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
                Identifier iconTex = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png");
                var layer = RenderTypes.entityCutout(iconTex);

                queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    int fullLight = FULL_BRIGHT;

                    float x0 = x;
                    float y0 = y - icon; // bottom
                    float x1 = x + icon;
                    float y1 = y;        // top

                    // Vertex order: bottom-left, top-left, top-right, bottom-right
                    // UVs:          (0,1)      (0,0)     (1,0)     (1,1)
                    put(vc, mat, x0, y0, 0f, 0f, 1f, fullLight, OverlayTexture.NO_OVERLAY);
                    put(vc, mat, x0, y1, 0f, 0f, 0f, fullLight, OverlayTexture.NO_OVERLAY);
                    put(vc, mat, x1, y1, 0f, 1f, 0f, fullLight, OverlayTexture.NO_OVERLAY);
                    put(vc, mat, x1, y0, 0f, 1f, 1f, fullLight, OverlayTexture.NO_OVERLAY);

                });
            }

            // (Optional later) number rendering can go here, but avoid MinecraftClient.getInstance() calls.
        }

        matrices.popPose();
    }

    @Override
    public void submit(State s, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (s.stack == null || s.stack.isEmpty()) return;

        var layer = RenderTypes.entityCutout(s.texId);

        matrices.pushPose();

        // Build a quad in XY plane facing +Z, then rotate it to match the entity face
        orientQuadToFace(matrices, s.facing);
        if (s.facing == Direction.UP || s.facing == Direction.DOWN) {
            matrices.mulPose(Axis.ZP.rotationDegrees(-s.flatYawStep * 90f));
        }

        // tiny push away from the block face to avoid z-fighting
        matrices.translate(0f, 0f, 0.01f);

        // rotate around the face normal (local Z axis after orient)
        float degrees = (s.rotStep == 1) ? 90f : 0f;
        matrices.mulPose(Axis.ZP.rotationDegrees(-degrees));

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

        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            Matrix4f mat = entry.pose();
            int fullLight = FULL_BRIGHT;

            put(vc, mat, -halfW, -halfH, 0f, u0, v0, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat, -halfW,  halfH, 0f, u0, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW,  halfH, 0f, u1, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW, -halfH, 0f, u1, v0, fullLight, OverlayTexture.NO_OVERLAY);
        });

        // After drawing the big card quad
        renderCounterStripOnCard(s, matrices, this.textRenderer, queue);

        matrices.popPose();
    }

    private static void put(VertexConsumer vc, Matrix4f mat,
                            float x, float y, float z,
                            float u, float v,
                            int light, int overlay) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0f, 0f, 1f);
    }

    private static Identifier fallbackItemTexture(ItemStack stack) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
    }

    private static void orientQuadToFace(PoseStack matrices, Direction facing) {
        // Quad starts facing +Z (SOUTH) in local space
        switch (facing) {
            case SOUTH -> { /* no rotation */ }
            case NORTH -> matrices.mulPose(Axis.YP.rotationDegrees(180f));
            case WEST  -> matrices.mulPose(Axis.YP.rotationDegrees(90f));
            case EAST  -> matrices.mulPose(Axis.YP.rotationDegrees(-90f));
            case UP    -> matrices.mulPose(Axis.XP.rotationDegrees(-90f)); // +Z -> +Y
            case DOWN  -> matrices.mulPose(Axis.XP.rotationDegrees(90f));  // +Z -> -Y
        }
    }

    private static CompoundTag readMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    private static CompoundTag readCounters(ItemStack st) {
        return readMeta(st).getCompound("counters").orElseGet(CompoundTag::new);
    }

    private static String readCounterIcon(ItemStack st, String key) {
        CompoundTag icons = readMeta(st).getCompound("counter_icons").orElse(null);
        if (icons == null) return "none";
        return icons.getString(key).orElse("none");
    }

    private static String formatCounterValue(int v) {
        if (v <= 0) return "";
        if (v > 99) return "99+";
        return String.valueOf(v);
    }

    private static boolean readHiddenFlagFromStack(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        return meta.getBoolean("mtg_hidden").orElse(false);
    }

    private static int readFaceIndex(ItemStack stack) {
        var comp = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        return meta.getInt("mtg_face").orElse(0);
    }
}
