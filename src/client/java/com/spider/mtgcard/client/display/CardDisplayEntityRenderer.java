package com.spider.mtgcard.client.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.api.CardBackTextureRegistry;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayAttachmentData;
import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CardDisplayEntityRenderer extends EntityRenderer<CardDisplayEntity, CardDisplayEntityRenderer.State> {

    // Use constants instead of resource probing / image decode (avoids early RenderSystem/device issues)
    private static final Identifier TEX_WHITE = Identifier.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    // Pick a stable aspect ratio for card back. (These are the values you already used as defaults.)
    private static final int BACK_W = 488;
    private static final int BACK_H = 680;
    private static final int TEXTURE_REFRESH_TICKS = 10;
    private static final long CACHE_EXPIRE_TICKS = 200L;
    private static final double COUNTER_RENDER_DISTANCE_SQR = 12.0D * 12.0D;

    public record CounterIcon(Identifier texture) {}

    public record CardState(UUID id, ItemStack stack, Identifier texId, int texW, int texH,
                             int rotStep, int faceIndex, boolean foil, List<CounterIcon> counters) {}

    private static final class CachedRenderData {
        ItemStack stack = ItemStack.EMPTY;
        Identifier texId = TEX_WHITE;
        int texW = 16;
        int texH = 16;
        int faceIndex = 0;
        boolean foil = false;
        boolean hidden = false;
        List<CounterIcon> counters = List.of();
        long nextTextureRefreshTick = Long.MIN_VALUE;
        long lastSeenTick = 0L;
    }

    private final Map<String, CachedRenderData> renderCache = new HashMap<>();
    private long nextCachePruneTick = 0L;

    public static class State extends EntityRenderState {
        public ItemStack stack = ItemStack.EMPTY;
        public List<CardState> cards = List.of();
        public Identifier texId = TEX_WHITE;
        public int texW = BACK_W;
        public int texH = BACK_H;
        public Direction facing = Direction.UP;
        public int rotStep = 0;
        public int faceIndex = 0;
        public int flatYawStep = 0;
        public boolean foil = false;
        public List<CounterIcon> counters = List.of();
        public double cameraDistanceSq = Double.MAX_VALUE;
    }

    public CardDisplayEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        Mtgcard.LOGGER.info("[CardDisplay] CardDisplayEntityRenderer constructed");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CardDisplayEntity entity, State s, float tickDelta) {
        super.extractRenderState(entity, s, tickDelta); // REQUIRED in 1.21.11+

        long gameTime = entity.level().getGameTime();
        pruneRenderCache(gameTime);

        s.stack = entity.getStack();
        s.facing = entity.getDirection();
        s.rotStep = entity.getRotStep();
        s.flatYawStep = entity.getFlatYawStep();
        s.cameraDistanceSq = getCameraDistanceSq(entity);

        if (s.stack == null || s.stack.isEmpty()) {
            removeEntityCache(entity.getId());
            s.texId = TEX_WHITE;
            s.texW = 16;
            s.texH = 16;
            s.faceIndex = 0;
            s.counters = List.of();
            s.cards = List.of();
            return;
        }

        ArrayList<CardState> cards = new ArrayList<>();
        appendCardState(cards, entity.getId(), entity.getStackKey(), s.stack, s.rotStep, gameTime);
        for (CardDisplayAttachmentData.Attachment attachment : entity.getCardAttachments()) {
            appendCardState(cards, entity.getId(), attachment.id(), attachment.stack(), attachment.rotStep(), gameTime);
        }

        s.cards = List.copyOf(cards);
        if (cards.isEmpty()) {
            s.texId = TEX_WHITE;
            s.texW = 16;
            s.texH = 16;
            s.faceIndex = 0;
            s.foil = false;
            s.counters = List.of();
        } else {
            CardState host = cards.getFirst();
            s.texId = host.texId();
            s.texW = host.texW();
            s.texH = host.texH();
            s.faceIndex = host.faceIndex();
            s.foil = host.foil();
            s.counters = host.counters();
        }
    }

    private void appendCardState(ArrayList<CardState> out, int entityId, UUID cardId, ItemStack stack, int rotStep, long gameTime) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        UUID safeId = cardId == null ? new UUID(0L, 0L) : cardId;
        String cacheKey = cacheKey(entityId, safeId);
        CachedRenderData cached = renderCache.computeIfAbsent(cacheKey, id -> new CachedRenderData());
        cached.lastSeenTick = gameTime;

        if (!ItemStack.matches(stack, cached.stack)) {
            refreshStaticData(cached, stack);
            cached.nextTextureRefreshTick = Long.MIN_VALUE;
        }

        refreshTextureData(cached, stack, gameTime);
        out.add(new CardState(
                safeId,
                stack.copy(),
                cached.texId,
                cached.texW,
                cached.texH,
                rotStep & 1,
                cached.faceIndex,
                cached.foil,
                cached.counters
        ));
    }

    private void renderCounterStripOnCard(
            List<CounterIcon> counters,
            PoseStack matrices,
            SubmitNodeCollector queue
    ) {
        if (counters == null || counters.isEmpty()) return;

        // Layout in local quad space
        final float padX = 0.04f;
        final float padY = 0.05f;
        final float icon = 0.10f;
        final float gapY = 0.02f;

        float startX = -0.5f + padX;
        float startY =  0.5f - padY;

        matrices.pushPose();
        matrices.translate(0f, 0f, 0.012f);

        for (int i = 0; i < counters.size(); i++) {
            float x = startX;
            float y = startY - i * (icon + gapY);

            Identifier iconTex = counters.get(i).texture();
            var layer = RenderTypes.entityCutoutNoCull(iconTex);

            queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.pose();
                int fullLight = LightTexture.FULL_BRIGHT;

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

        matrices.popPose();
    }

    @Override
    public void submit(State s, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (s.cards == null || s.cards.isEmpty()) return;

        int attachmentCount = Math.max(0, s.cards.size() - 1);
        for (int i = s.cards.size() - 1; i >= 0; i--) {
            renderStackCard(s, s.cards.get(i), i, attachmentCount, matrices, queue);
        }
    }

    private void renderStackCard(State s, CardState card, int displayIndex, int attachmentCount,
                                 PoseStack matrices, SubmitNodeCollector queue) {
        var layer = RenderTypes.entityCutoutNoCull(card.texId());

        matrices.pushPose();

        orientQuadToFace(matrices, s.facing);
        if (s.facing == Direction.UP || s.facing == Direction.DOWN) {
            matrices.mulPose(Axis.ZP.rotationDegrees(-s.flatYawStep * 90f));
        }

        float baseNormalOffset = (s.facing == Direction.UP || s.facing == Direction.DOWN) ? 0.002f : 0.01f;
        float layerOffset = 0.0015f * (s.cards.size() - displayIndex);
        float yOffset = (float) CardDisplayEntity.stackLocalYOffset(displayIndex, attachmentCount);
        matrices.translate(0f, yOffset, baseNormalOffset + layerOffset);

        float degrees = (card.rotStep() == 1) ? 90f : 0f;
        matrices.mulPose(Axis.ZP.rotationDegrees(-degrees));

        float ar = (float) card.texW() / (float) card.texH();
        float halfW, halfH;
        if (ar >= 1f) {
            halfW = 0.5f;
            halfH = 0.5f / ar;
        } else {
            halfW = 0.5f * ar;
            halfH = 0.5f;
        }

        final float u0 = 0f, u1 = 1f;
        final float v0 = 1f, v1 = 0f;

        queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            Matrix4f mat = entry.pose();
            int fullLight = LightTexture.FULL_BRIGHT;

            put(vc, mat, -halfW, -halfH, 0f, u0, v0, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat, -halfW,  halfH, 0f, u0, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW,  halfH, 0f, u1, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW, -halfH, 0f, u1, v0, fullLight, OverlayTexture.NO_OVERLAY);
        });

        if (card.foil()) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), card.texW());
            if (sweep != null) {
                float overlayX0 = -halfW + (halfW * 2f * sweep.u0());
                float overlayX1 = -halfW + (halfW * 2f * sweep.u1());

                matrices.pushPose();
                matrices.translate(0f, 0f, 0.001f);

                var foilLayer = RenderTypes.entityTranslucent(card.texId());
                queue.submitCustomGeometry(matrices, foilLayer, (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    int fullLight = LightTexture.FULL_BRIGHT;

                    put(vc, mat, overlayX0, -halfH, 0f, sweep.u0(), v0, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX0,  halfH, 0f, sweep.u0(), v1, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX1,  halfH, 0f, sweep.u1(), v1, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX1, -halfH, 0f, sweep.u1(), v0, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                });

                matrices.popPose();
            }
        }

        if (s.cameraDistanceSq <= COUNTER_RENDER_DISTANCE_SQR) {
            renderCounterStripOnCard(card.counters(), matrices, queue);
        }

        matrices.popPose();
    }

    private void pruneRenderCache(long gameTime) {
        if (gameTime < nextCachePruneTick) {
            return;
        }

        nextCachePruneTick = gameTime + CACHE_EXPIRE_TICKS;

        Iterator<Map.Entry<String, CachedRenderData>> it = renderCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedRenderData> entry = it.next();
            if (gameTime - entry.getValue().lastSeenTick > CACHE_EXPIRE_TICKS) {
                it.remove();
            }
        }
    }

    private void removeEntityCache(int entityId) {
        String prefix = entityId + ":";
        renderCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String cacheKey(int entityId, UUID cardId) {
        return entityId + ":" + cardId;
    }

    private static double getCameraDistanceSq(CardDisplayEntity entity) {
        var cameraEntity = Minecraft.getInstance().getCameraEntity();
        if (cameraEntity == null) {
            return Double.MAX_VALUE;
        }
        return cameraEntity.distanceToSqr(entity);
    }

    private static void refreshStaticData(CachedRenderData cached, ItemStack stack) {
        cached.stack = stack.copy();

        CompoundTag meta = readMeta(stack);
        cached.faceIndex = meta.getInt("mtg_face").orElse(0);
        cached.foil = com.spider.mtgcard.client.render.CardFoilUtil.isFoil(stack);
        cached.hidden = meta.getBoolean("mtg_hidden").orElse(false);
        cached.counters = buildCounterIcons(meta);
    }

    private static void refreshTextureData(CachedRenderData cached, ItemStack stack, long gameTime) {
        if (cached.hidden) {
            useCardBack(cached, stack);
            return;
        }

        if (gameTime < cached.nextTextureRefreshTick) {
            return;
        }

        cached.nextTextureRefreshTick = gameTime + TEXTURE_REFRESH_TICKS;

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(stack, cached.faceIndex);
        if (ref != null && ref.id() != null) {
            cached.texId = ref.id();
            cached.texW = (ref.texW() > 0) ? ref.texW() : 256;
            cached.texH = (ref.texH() > 0) ? ref.texH() : 256;
        } else {
            useCardBack(cached, stack);
        }
    }

    private static void useCardBack(CachedRenderData cached, ItemStack stack) {
        cached.texId = CardBackTextureRegistry.textureForStackOrDefault(stack);
        cached.texW = BACK_W;
        cached.texH = BACK_H;
    }

    private static void put(VertexConsumer vc, Matrix4f mat,
                            float x, float y, float z,
                            float u, float v,
                            int light, int overlay) {
        put(vc, mat, x, y, z, u, v, light, overlay, 255);
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

    private static void orientQuadToFace(PoseStack matrices, Direction facing) {
        // Quad starts facing +Z (SOUTH) in local space
        switch (facing) {
            case SOUTH -> { /* no rotation */ }
            case NORTH -> matrices.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST  -> matrices.mulPose(Axis.YP.rotationDegrees(90f));
            case WEST  -> matrices.mulPose(Axis.YP.rotationDegrees(-90f));
            case UP    -> matrices.mulPose(Axis.XP.rotationDegrees(-90f)); // +Z -> +Y
            case DOWN  -> matrices.mulPose(Axis.XP.rotationDegrees(90f));  // +Z -> -Y
        }
    }

    private static CompoundTag readMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    private static List<CounterIcon> buildCounterIcons(CompoundTag meta) {
        CompoundTag counters = meta.getCompound("counters").orElse(null);
        if (counters == null || counters.keySet().isEmpty()) {
            return List.of();
        }

        ArrayList<String> keys = new ArrayList<>();
        for (String key : counters.keySet()) {
            if (counters.getInt(key).orElse(0) > 0) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return List.of();
        }

        keys.sort(String::compareToIgnoreCase);
        if (keys.size() > 6) {
            keys.subList(6, keys.size()).clear();
        }

        ArrayList<CounterIcon> icons = new ArrayList<>(keys.size());
        for (String key : keys) {
            String iconKey = readCounterIcon(meta, key);
            if (iconKey == null || iconKey.equals("none")) {
                continue;
            }
            icons.add(new CounterIcon(
                    Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png")
            ));
        }

        return icons.isEmpty() ? List.of() : List.copyOf(icons);
    }

    private static String readCounterIcon(CompoundTag meta, String key) {
        CompoundTag icons = meta.getCompound("counter_icons").orElse(null);
        if (icons == null) return "none";
        return icons.getString(key).orElse("none");
    }
}
