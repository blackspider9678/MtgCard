// src/main/java/com/spider/mtgcard/client/UnpackHud.java
package com.spider.mtgcard.client;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class UnpackHud implements HudElement {
    private static volatile int progress = 0;
    private static volatile long lastUpdateNs = 0L;
    private static volatile boolean active = false;

    private static final long KEEP_ALIVE_AFTER_DONE_NS = 2_000_000_000L;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "unpack_progress"), new UnpackHud());
    }

    public static void setProgressFromServer(int percent) {
        int next = Math.max(0, Math.min(100, percent));
        if (next == 0) {
            if (active) {
                reset();
                return;
            }

            active = true;
            progress = 0;
            lastUpdateNs = System.nanoTime();
            return;
        }

        progress = next;
        lastUpdateNs = System.nanoTime();
        active = next < 100;
    }

    /** NEW: call this on disconnect/world leave to stop the HUD immediately. */
    public static void reset() {
        active = false;
        progress = 0;
        lastUpdateNs = 0L;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        onHudRender(new GuiGraphics(graphics), tickCounter);
    }

    public void onHudRender(GuiGraphics ctx, DeltaTracker tickCounter) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (lastUpdateNs == 0L) return;

        long sinceNs = System.nanoTime() - lastUpdateNs;
        if (!active) {
            if (progress < 100 || sinceNs >= KEEP_ALIVE_AFTER_DONE_NS) {
                reset();
                return;
            }
        }

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();

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
        var tm = mc.font;
        int tw = tm.width(text);
        int tx = x + (barWidth - tw) / 2;
        int ty = y - 10;
        ctx.drawString(tm, text, tx, ty, 0xFFFFFFFF, false);
    }
}
