package com.spider.mtgcard.client.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.util.NbtCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import com.spider.mtgcard.client.compat.render.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.spider.mtgcard.client.compat.render.entity.state.EntityRenderState;
import com.spider.mtgcard.client.compat.render.rendertype.RenderTypes;
import com.spider.mtgcard.client.compat.render.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CardDisplayEntityRenderer extends EntityRenderer<CardDisplayEntity> {

    // Use constants instead of resource probing / image decode (avoids early RenderSystem/device issues)
    private static final ResourceLocation TEX_WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final ResourceLocation TEX_BACK  = ResourceLocation.fromNamespaceAndPath("mtgcard", "textures/gui/card.png");

    // Pick a stable aspect ratio for card back. (These are the values you already used as defaults.)
    private static final int BACK_W = 488;
    private static final int BACK_H = 680;
    private static final int TEXTURE_REFRESH_TICKS = 10;
    private static final long CACHE_EXPIRE_TICKS = 200L;
    private static final double COUNTER_RENDER_DISTANCE_SQR = 12.0D * 12.0D;

    private record CounterIcon(ResourceLocation texture) {}

    private static final class CachedRenderData {
        ItemStack stack = ItemStack.EMPTY;
        ResourceLocation texId = TEX_WHITE;
        int texW = 16;
        int texH = 16;
        int faceIndex = 0;
        boolean foil = false;
        boolean hidden = false;
        List<CounterIcon> counters = List.of();
        long nextTextureRefreshTick = Long.MIN_VALUE;
        long lastSeenTick = 0L;
    }

    private final Map<Integer, CachedRenderData> renderCache = new HashMap<>();
    private long nextCachePruneTick = 0L;

    public static class State extends EntityRenderState {
        public ItemStack stack = ItemStack.EMPTY;
        public ResourceLocation texId = TEX_WHITE;
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
    public void render(CardDisplayEntity entity, float entityYaw, float tickDelta, PoseStack matrices, MultiBufferSource buffers, int packedLight) {
        State state = createRenderState();
        extractRenderState(entity, state, tickDelta);
        submit(state, matrices, new SubmitNodeCollector(buffers), new CameraRenderState());
        super.render(entity, entityYaw, tickDelta, matrices, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CardDisplayEntity entity) {
        return TEX_WHITE;
    }

    public State createRenderState() {
        return new State();
    }

    public void extractRenderState(CardDisplayEntity entity, State s, float tickDelta) {
        long gameTime = entity.level().getGameTime();
        pruneRenderCache(gameTime);

        s.stack = entity.getStack();
        s.facing = entity.getDirection();
        s.rotStep = entity.getRotStep();
        s.flatYawStep = entity.getFlatYawStep();
        s.cameraDistanceSq = getCameraDistanceSq(entity);

        if (s.stack == null || s.stack.isEmpty()) {
            renderCache.remove(entity.getId());
            s.texId = TEX_WHITE;
            s.texW = 16;
            s.texH = 16;
            s.faceIndex = 0;
            s.counters = List.of();
            return;
        }

        CachedRenderData cached = renderCache.computeIfAbsent(entity.getId(), id -> new CachedRenderData());
        cached.lastSeenTick = gameTime;

        if (!ItemStack.matches(s.stack, cached.stack)) {
            refreshStaticData(cached, s.stack);
            cached.nextTextureRefreshTick = Long.MIN_VALUE;
        }

        refreshTextureData(cached, s.stack, gameTime);

        s.faceIndex = cached.faceIndex;
        s.texId = cached.texId;
        s.texW = cached.texW;
        s.texH = cached.texH;
        s.foil = cached.foil;
        s.counters = cached.counters;
    }

    private void renderCounterStripOnCard(
            State s,
            PoseStack matrices,
            SubmitNodeCollector queue
    ) {
        if (s.counters == null || s.counters.isEmpty()) return;

        // Layout in local quad space
        final float padX = 0.04f;
        final float padY = 0.05f;
        final float icon = 0.10f;
        final float gapY = 0.02f;

        float startX = -0.5f + padX;
        float startY =  0.5f - padY;

        matrices.pushPose();
        matrices.translate(0f, 0f, 0.012f);

        for (int i = 0; i < s.counters.size(); i++) {
            float x = startX;
            float y = startY - i * (icon + gapY);

            ResourceLocation iconTex = s.counters.get(i).texture();
            var layer = RenderTypes.entityCutoutNoCull(iconTex);

            queue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
                Matrix4f mat = entry.pose();
                int fullLight = 0x00F000F0;

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

    public void submit(State s, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (s.stack == null || s.stack.isEmpty()) return;

        var layer = RenderTypes.entityCutoutNoCull(s.texId);

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
            int fullLight = 0x00F000F0;

            put(vc, mat, -halfW, -halfH, 0f, u0, v0, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat, -halfW,  halfH, 0f, u0, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW,  halfH, 0f, u1, v1, fullLight, OverlayTexture.NO_OVERLAY);
            put(vc, mat,  halfW, -halfH, 0f, u1, v0, fullLight, OverlayTexture.NO_OVERLAY);
        });

        if (s.foil) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), s.texW);
            if (sweep != null) {
                float overlayX0 = -halfW + (halfW * 2f * sweep.u0());
                float overlayX1 = -halfW + (halfW * 2f * sweep.u1());

                matrices.pushPose();
                matrices.translate(0f, 0f, 0.001f);

                var foilLayer = RenderTypes.entityTranslucent(s.texId);
                queue.submitCustomGeometry(matrices, foilLayer, (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    int fullLight = 0x00F000F0;

                    put(vc, mat, overlayX0, -halfH, 0f, sweep.u0(), v0, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX0,  halfH, 0f, sweep.u0(), v1, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX1,  halfH, 0f, sweep.u1(), v1, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                    put(vc, mat, overlayX1, -halfH, 0f, sweep.u1(), v0, fullLight, OverlayTexture.NO_OVERLAY, com.spider.mtgcard.client.render.CardFoilUtil.WORLD_SWEEP_ALPHA);
                });

                matrices.popPose();
            }
        }

        // After drawing the big card quad
        if (s.cameraDistanceSq <= COUNTER_RENDER_DISTANCE_SQR) {
            renderCounterStripOnCard(s, matrices, queue);
        }

        matrices.popPose();
    }

    private void pruneRenderCache(long gameTime) {
        if (gameTime < nextCachePruneTick) {
            return;
        }

        nextCachePruneTick = gameTime + CACHE_EXPIRE_TICKS;

        Iterator<Map.Entry<Integer, CachedRenderData>> it = renderCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CachedRenderData> entry = it.next();
            if (gameTime - entry.getValue().lastSeenTick > CACHE_EXPIRE_TICKS) {
                it.remove();
            }
        }
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
        cached.faceIndex = meta.getInt("mtg_face");
        cached.foil = com.spider.mtgcard.client.render.CardFoilUtil.isFoil(stack);
        cached.hidden = meta.getBoolean("mtg_hidden");
        cached.counters = buildCounterIcons(meta);
    }

    private static void refreshTextureData(CachedRenderData cached, ItemStack stack, long gameTime) {
        if (cached.hidden) {
            var sleeve = com.spider.mtgcard.api.CardSleeves.get(stack);
            cached.texId = sleeve.map(com.spider.mtgcard.api.CardSleeve::backTexture).orElse(TEX_BACK);
            cached.texW = sleeve.isPresent() ? 16 : BACK_W;
            cached.texH = sleeve.isPresent() ? 16 : BACK_H;
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
            cached.texId = fallbackItemTexture(stack);
            cached.texW = 256;
            cached.texH = 256;
        }
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

    private static ResourceLocation fallbackItemTexture(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
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
        return root.getCompound("mtg_meta");
    }

    private static List<CounterIcon> buildCounterIcons(CompoundTag meta) {
        CompoundTag counters = meta.getCompound("counters");
        if (counters == null || counters.getAllKeys().isEmpty()) {
            return List.of();
        }

        ArrayList<String> keys = new ArrayList<>();
        for (String key : counters.getAllKeys()) {
            if (counters.getInt(key) > 0) {
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
                    ResourceLocation.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png")
            ));
        }

        return icons.isEmpty() ? List.of() : List.copyOf(icons);
    }

    private static String readCounterIcon(CompoundTag meta, String key) {
        CompoundTag icons = meta.getCompound("counter_icons");
        if (icons == null) return "none";
        return NbtCompat.getString(icons, key, "none");
    }
}
