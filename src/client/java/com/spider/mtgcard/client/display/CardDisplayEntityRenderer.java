package com.spider.mtgcard.client.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class CardDisplayEntityRenderer extends EntityRenderer<CardDisplayEntity, CardDisplayEntityRenderer.State> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Identifier TEX_WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final Identifier TEX_BACK = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card.png");
    private static final int BACK_W = 488;
    private static final int BACK_H = 680;

    public static class State extends EntityRenderState {
        public ItemStack stack = ItemStack.EMPTY;
        public Identifier texId = TEX_WHITE;
        public int texW = BACK_W;
        public int texH = BACK_H;
        public Direction facing = Direction.NORTH;
        public int rotStep = 0;
        public int faceIndex = 0;
        public int flatYawStep = 0;
    }

    public CardDisplayEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
        this.shadowStrength = 0.0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CardDisplayEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);

        state.stack = entity.getStack();
        state.facing = entity.getDirection();
        state.rotStep = entity.getRotStep();
        state.flatYawStep = entity.getFlatYawStep();

        if (state.stack.isEmpty()) {
            state.texId = TEX_WHITE;
            state.texW = 16;
            state.texH = 16;
            state.faceIndex = 0;
            return;
        }

        state.faceIndex = readFaceIndex(state.stack);

        if (readHiddenFlagFromStack(state.stack)) {
            state.texId = TEX_BACK;
            state.texW = BACK_W;
            state.texH = BACK_H;
            state.faceIndex = 0;
            return;
        }

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(state.stack, state.faceIndex);
        if (ref != null && ref.id() != null) {
            state.texId = ref.id();
            state.texW = ref.texW() > 0 ? ref.texW() : 256;
            state.texH = ref.texH() > 0 ? ref.texH() : 256;
        } else {
            state.texId = fallbackItemTexture(state.stack);
            state.texW = 256;
            state.texH = 256;
        }
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (state.stack.isEmpty()) {
            return;
        }

        RenderType layer = RenderTypes.entityCutoutNoCull(state.texId);

        matrices.pushPose();
        orientQuadToFace(matrices, state.facing);
        if (state.facing == Direction.UP || state.facing == Direction.DOWN) {
            matrices.mulPose(Axis.ZP.rotationDegrees(-state.flatYawStep * 90f));
        }

        matrices.translate(0f, 0f, 0.01f);
        if (state.rotStep == 1) {
            matrices.mulPose(Axis.ZP.rotationDegrees(-90f));
        }

        float aspect = (float) state.texW / (float) state.texH;
        float halfW;
        float halfH;
        if (aspect >= 1f) {
            halfW = 0.5f;
            halfH = 0.5f / aspect;
        } else {
            halfW = 0.5f * aspect;
            halfH = 0.5f;
        }

        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            Matrix4f mat = entry.pose();
            put(vc, mat, -halfW, -halfH, 0f, 0f, 1f);
            put(vc, mat, -halfW, halfH, 0f, 0f, 0f);
            put(vc, mat, halfW, halfH, 0f, 1f, 0f);
            put(vc, mat, halfW, -halfH, 0f, 1f, 1f);
        });

        renderCounterStripOnCard(state, matrices, queue);
        matrices.popPose();
    }

    private void renderCounterStripOnCard(State state, PoseStack matrices, SubmitNodeCollector queue) {
        if (state.stack.isEmpty()) {
            return;
        }

        CompoundTag counters = readCounters(state.stack);
        if (counters.isEmpty()) {
            return;
        }

        List<String> keys = new ArrayList<>();
        for (String key : counters.keySet()) {
            if (counters.getIntOr(key, 0) > 0) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return;
        }

        keys.sort(String::compareToIgnoreCase);
        if (keys.size() > 6) {
            keys = keys.subList(0, 6);
        }

        final float padX = 0.04f;
        final float padY = 0.05f;
        final float icon = 0.10f;
        final float gapY = 0.02f;

        float startX = -0.5f + padX;
        float startY = 0.5f - padY;

        matrices.pushPose();
        matrices.translate(0f, 0f, 0.012f);

        for (int i = 0; i < keys.size(); i++) {
            String iconKey = readCounterIcon(state.stack, keys.get(i));
            if (iconKey == null || iconKey.equals("none")) {
                continue;
            }

            float x = startX;
            float y = startY - i * (icon + gapY);
            Identifier iconTex = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png");
            RenderType layer = RenderTypes.entityCutoutNoCull(iconTex);

            queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.pose();
                float x0 = x;
                float y0 = y - icon;
                float x1 = x + icon;
                float y1 = y;

                put(vc, mat, x0, y0, 0f, 0f, 1f);
                put(vc, mat, x0, y1, 0f, 0f, 0f);
                put(vc, mat, x1, y1, 0f, 1f, 0f);
                put(vc, mat, x1, y0, 0f, 1f, 1f);
            });
        }

        matrices.popPose();
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float u, float v) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0f, 0f, 1f);
    }

    private static Identifier fallbackItemTexture(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
    }

    private static void orientQuadToFace(PoseStack matrices, Direction facing) {
        switch (facing) {
            case SOUTH -> {
            }
            case NORTH -> matrices.mulPose(Axis.YP.rotationDegrees(180f));
            case WEST -> matrices.mulPose(Axis.YP.rotationDegrees(90f));
            case EAST -> matrices.mulPose(Axis.YP.rotationDegrees(-90f));
            case UP -> matrices.mulPose(Axis.XP.rotationDegrees(-90f));
            case DOWN -> matrices.mulPose(Axis.XP.rotationDegrees(90f));
        }
    }

    private static CompoundTag readMeta(ItemStack stack) {
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = comp == null ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    private static CompoundTag readCounters(ItemStack stack) {
        return readMeta(stack).getCompound("counters").orElseGet(CompoundTag::new);
    }

    private static String readCounterIcon(ItemStack stack, String key) {
        CompoundTag icons = readMeta(stack).getCompound("counter_icons").orElse(null);
        return icons == null ? "none" : icons.getStringOr(key, "none");
    }

    private static boolean readHiddenFlagFromStack(ItemStack stack) {
        return readMeta(stack).getBooleanOr("mtg_hidden", false);
    }

    private static int readFaceIndex(ItemStack stack) {
        return readMeta(stack).getIntOr("mtg_face", 0);
    }
}
