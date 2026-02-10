package com.spider.mtgcard.client.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Shared chrome/styling helpers so all GUIs match LifePointScreen.
 */
public final class MtgGuiChrome {
    private MtgGuiChrome() {}

    // Match LifePointScreen constants
    public static final int TOP_HUD_STRIP_H = 62;
    public static final int BOTTOM_HUD_STRIP_H = 34;

    public static final int HUD_STRIP_BG   = 0xCC101010;
    public static final int HUD_STRIP_LINE = 0xFF2B2B2B;

    public static final int COL_BG      = 0xAA141414;
    public static final int COL_BG_EDGE = 0xFF2B2B2B;

    public static final int PANEL_BG   = 0xFF0F0F0F;
    public static final int PANEL_EDGE = 0xFF3A3A3A;

    public static final int ROW_BG      = 0xFF141414;
    public static final int ROW_BG_HOV  = 0xFF161616;
    public static final int ROW_BG_SEL  = 0xFF1B1B1B;
    public static final int ROW_TOPLINE = 0xFF2C2C2C;

    public record Rect(int x, int y, int w, int h) {}

    /** Dim background overlay like LifePointScreen. */
    public static void drawDimBackground(DrawContext ctx, int w, int h) {
        ctx.fill(0, 0, w, h, 0xAA000000);
    }

    /** Top + bottom HUD strips like LifePointScreen. */
    public static void drawHudStrips(DrawContext ctx, int w, int h) {
        int topY1 = Math.min(h, TOP_HUD_STRIP_H);
        ctx.fill(0, 0, w, topY1, HUD_STRIP_BG);
        ctx.fill(0, topY1 - 1, w, topY1, HUD_STRIP_LINE);

        int botY0 = Math.max(0, h - BOTTOM_HUD_STRIP_H);
        ctx.fill(0, botY0, w, h, HUD_STRIP_BG);
        ctx.fill(0, botY0, w, botY0 + 1, HUD_STRIP_LINE);
    }

    /** Column panels like LifePointScreen midCols. */
    public static void drawColumnPanels(DrawContext ctx, Rect[] cols) {
        if (cols == null) return;
        for (Rect r : cols) {
            if (r == null) continue;
            ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), COL_BG);
            ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, COL_BG_EDGE);
        }
    }

    /** Generic panel box (used for viewports). */
    public static void drawPanelBox(DrawContext ctx, Rect r) {
        if (r == null) return;
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), PANEL_BG);
        // top/bottom edges (match your screens)
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, PANEL_EDGE);
        ctx.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), PANEL_EDGE);
    }

    /** Flat row highlight style (select/hover) like LifePointScreen. */
    public static void drawFlatRow(DrawContext ctx, Rect r, boolean selected, boolean hovered) {
        if (r == null) return;
        int bg = selected ? ROW_BG_SEL : (hovered ? ROW_BG_HOV : ROW_BG);
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), bg);
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, ROW_TOPLINE);
    }

    /** Scrollbar rendering like LifePointScreen (track + thumb). */
    public static Scrollbar drawScrollbar(DrawContext ctx, Rect vp, int contentH, int scroll) {
        if (vp == null) return null;
        if (contentH <= vp.h()) return null;

        int maxScroll = Math.max(0, contentH - vp.h());

        int barW = 6;
        int barX = vp.x() + vp.w() - barW - 2;
        int barY = vp.y() + 2;
        int barH = vp.h() - 4;

        int thumbH = Math.max(12, (int)(barH * (vp.h() / (float)contentH)));
        int trackSpan = Math.max(1, barH - thumbH);

        float t = (maxScroll <= 0) ? 0f : (scroll / (float)maxScroll);
        int thumbY = barY + (int)(trackSpan * t);

        Rect track = new Rect(barX, barY, barW, barH);
        Rect thumb = new Rect(barX, thumbY, barW, thumbH);

        ctx.fill(track.x(), track.y(), track.x() + track.w(), track.y() + track.h(), 0x66222222);
        ctx.fill(thumb.x(), thumb.y(), thumb.x() + thumb.w(), thumb.y() + thumb.h(), 0xAA888888);

        return new Scrollbar(track, thumb, maxScroll);
    }

    public record Scrollbar(Rect track, Rect thumb, int maxScroll) {}

    public static boolean ptIn(Rect r, double mx, double my) {
        return r != null && mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h();
    }
}
