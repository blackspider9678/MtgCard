// src/main/java/com/spider/mtgcard/client/UnpackHud.java
package com.spider.mtgcard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

@Environment(EnvType.CLIENT)
public final class UnpackHud implements HudRenderCallback {
    private static volatile int progress = 0;
    private static volatile long lastUpdateNs = 0L;

    private static final long KEEP_ALIVE_AFTER_DONE_NS = 2_000_000_000L;

    public static void init() {
        HudRenderCallback.EVENT.register(new UnpackHud());
    }

    public static void setProgressFromServer(int percent) {
        progress = Math.max(0, Math.min(100, percent));
        lastUpdateNs = System.nanoTime();
    }

    /** NEW: call this on disconnect/world leave to stop the HUD immediately. */
    public static void reset() {
        progress = 0;
        lastUpdateNs = 0L;
    }

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        if (lastUpdateNs == 0L) return;

        long sinceNs = System.nanoTime() - lastUpdateNs;
        boolean visible = (progress < 100) || (sinceNs < KEEP_ALIVE_AFTER_DONE_NS);
        if (!visible) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barWidth  = Math.min(180, (int)(w * 0.45f));
        int barHeight = 3;
        int x = (w - barWidth) / 2;
        int y = h - 28;

        int bg = 0xAA000000;
        int fg = 0xFF3DA1FF;
        int br = 0xFFFFFFFF;

        ctx.fill(x, y, x + barWidth, y + barHeight, bg);

        int fillW = (int) Math.round(barWidth * (progress / 100.0));
        if (fillW > 0) ctx.fill(x, y, x + fillW, y + barHeight, fg);

        ctx.fill(x - 1, y - 1, x + barWidth + 1, y, br);
        ctx.fill(x - 1, y + barHeight, x + barWidth + 1, y + barHeight + 1, br);
        ctx.fill(x - 1, y, x, y + barHeight, br);
        ctx.fill(x + barWidth, y, x + barWidth + 1, y + barHeight, br);

        var text = progress + "%";
        var tm = mc.textRenderer;
        int tw = tm.getWidth(text);
        int tx = x + (barWidth - tw) / 2;
        int ty = y - 10;
        ctx.drawText(tm, text, tx, ty, 0xFFFFFFFF, false);
    }
}
