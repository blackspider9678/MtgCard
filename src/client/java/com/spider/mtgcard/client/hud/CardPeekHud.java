package com.spider.mtgcard.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.input.ModKeybinds;
import com.spider.mtgcard.item.CardItem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class CardPeekHud implements HudRenderCallback {

    // 0..1 animation progress
    private float t = 0f;

    // seconds to fully slide in/out (tuned feel)
    private static final float IN_TIME_SEC  = 0.12f; // ~120ms
    private static final float OUT_TIME_SEC = 0.10f; // ~100ms

    // HUD preview size
    private static final int PREVIEW_W = 90;
    private static final int PREVIEW_H = 125;

    private static final int PAD = 8;
    private static final int HOTBAR_LIFT = 24; // keeps it off the hotbar area

    public static void init() {
        HudRenderCallback.EVENT.register(new CardPeekHud());
    }

    @Override
    public void onHudRender(GuiGraphics ctx, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        // Never show when ANY screen is open
        if (client.screen != null) {
            stepToward(0f, tickCounter);
            return;
        }

        if (!ModKeybinds.isCardPeekEnabled()) {
            stepToward(0f, tickCounter);
            return;
        }

        ItemStack held = pickHeldCard(client);
        boolean shouldShow = !held.isEmpty();

        stepToward(shouldShow ? 1f : 0f, tickCounter);

        if (t <= 0.001f) return;

        renderPeek(ctx, client, held);
    }

    private static ItemStack pickHeldCard(Minecraft client) {
        ItemStack main = client.player.getMainHandItem();
        if (isCard(main)) return main;

        ItemStack off = client.player.getOffhandItem();
        if (isCard(off)) return off;

        return ItemStack.EMPTY;
    }

    private static boolean isCard(ItemStack st) {
        return !st.isEmpty() && st.getItem() instanceof CardItem;
    }

    private void stepToward(float target, DeltaTracker tickCounter) {
        // Convert render-tick delta to seconds.
        // tickCounter gives partial tick; 20 ticks/sec.
        float dt = getDeltaSeconds(tickCounter);

        if (target > t) {
            float speed = 1f / Math.max(0.001f, IN_TIME_SEC);   // t-units per second
            t = Math.min(target, t + speed * dt);
        } else if (target < t) {
            float speed = 1f / Math.max(0.001f, OUT_TIME_SEC);
            t = Math.max(target, t - speed * dt);
        }
    }

    private static float getDeltaSeconds(DeltaTracker tickCounter) {
        // Different mappings expose this differently across minor versions.
        // We’ll try a couple known accessors and fall back to 1/60.
        try {
            // Common: tickCounter.getTickDelta(false) or tickCounter.getTickDelta()
            var m = tickCounter.getClass().getMethod("getTickDelta");
            Object v = m.invoke(tickCounter);
            if (v instanceof Float f) return (f / 20f);
        } catch (Throwable ignored) {}

        try {
            var m = tickCounter.getClass().getMethod("getTickDelta", boolean.class);
            Object v = m.invoke(tickCounter, false);
            if (v instanceof Float f) return (f / 20f);
        } catch (Throwable ignored) {}

        // Last resort: assume ~60fps
        return 1f / 60f;
    }

    private void renderPeek(GuiGraphics ctx, Minecraft client, ItemStack stack) {
        int face = readFaceIndex(stack);

        CardArtManager.TextureRef texRef = CardArtManager.getOrRequestFace(stack, face);
        if (texRef == null || texRef.id() == null) return;

        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();

        float eased = smoothstep(t);

        // Slide in from offscreen right, clear of Minecraft's bottom-left chat area.
        float x = lerp(sw + PAD, sw - PREVIEW_W - PAD, eased);

        // Bottom-right, lifted above hotbar
        float y = sh - PREVIEW_H - PAD - HOTBAR_LIFT;

        // Fade with slide
        float alpha = Mth.clamp(eased, 0f, 1f);

        drawBackdrop(ctx, (int) x, (int) y, PREVIEW_W, PREVIEW_H, alpha);

        // Preserve aspect ratio from the texture ref
        float aspect = texRef.texW() / (float) texRef.texH();
        int drawW = PREVIEW_W;
        int drawH = Math.round(PREVIEW_W / aspect);
        if (drawH > PREVIEW_H) {
            drawH = PREVIEW_H;
            drawW = Math.round(PREVIEW_H * aspect);
        }

        int dx = Math.round(x) + (PREVIEW_W - drawW) / 2;
        int dy = Math.round(y) + (PREVIEW_H - drawH) / 2;

        // Apply alpha for the draw (best-effort across versions)
        setCtxShaderColor(ctx, 1f, 1f, 1f, alpha);

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                texRef.id(),
                dx, dy,
                0f, 0f,
                drawW, drawH,
                drawW, drawH
        );

        if (com.spider.mtgcard.client.render.CardFoilUtil.isFoil(stack)) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), drawW);
            if (sweep != null) {
                int shimmerColor = com.spider.mtgcard.client.render.CardFoilUtil.guiShimmerColor(1f);
                int shimmerAlpha = Math.round(((shimmerColor >>> 24) & 0xFF) * alpha);
                ctx.fill(
                        dx + sweep.drawU(),
                        dy,
                        dx + sweep.drawU() + sweep.clipW(),
                        dy + drawH,
                        (shimmerAlpha << 24) | 0x00FFFFFF
                );
            }
        }

        // Reset color back to default
        setCtxShaderColor(ctx, 1f, 1f, 1f, 1f);
    }

    private static void setCtxShaderColor(GuiGraphics ctx, float r, float g, float b, float a) {
        // Newer MC versions: DrawContext has setShaderColor(float,float,float,float)
        try {
            var m = ctx.getClass().getMethod("setShaderColor", float.class, float.class, float.class, float.class);
            m.invoke(ctx, r, g, b, a);
            return;
        } catch (Throwable ignored) {}

        // Some versions expose it as setColor or similar (rare, but cheap to check)
        try {
            var m = ctx.getClass().getMethod("setColor", float.class, float.class, float.class, float.class);
            m.invoke(ctx, r, g, b, a);
        } catch (Throwable ignored) {
            // If neither exists, we still get slide animation; fade just won't apply.
        }
    }

    private static void drawBackdrop(GuiGraphics ctx, int x, int y, int w, int h, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 140f);
        int bg = (a << 24);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, bg);
    }

    private static float smoothstep(float v) {
        v = Mth.clamp(v, 0f, 1f);
        return v * v * (3f - 2f * v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // --- NBT reads to match your CardLargeViewScreen ---
    private static int readFaceIndex(ItemStack st) {
        CompoundTag meta = getMeta(st);
        return meta.getInt("mtg_face").orElse(0);
    }

    private static CompoundTag getMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }
}
