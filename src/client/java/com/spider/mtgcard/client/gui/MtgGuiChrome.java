package com.spider.mtgcard.client.gui;

import net.minecraft.client.gui.GuiGraphics;

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
    public static void drawDimBackground(GuiGraphics ctx, int w, int h) {
        ctx.fill(0, 0, w, h, 0xAA000000);
    }

    /** Top + bottom HUD strips like LifePointScreen. */
    public static void drawHudStrips(GuiGraphics ctx, int w, int h) {
        int topY1 = Math.min(h, TOP_HUD_STRIP_H);
        ctx.fill(0, 0, w, topY1, HUD_STRIP_BG);
        ctx.fill(0, topY1 - 1, w, topY1, HUD_STRIP_LINE);

        int botY0 = Math.max(0, h - BOTTOM_HUD_STRIP_H);
        ctx.fill(0, botY0, w, h, HUD_STRIP_BG);
        ctx.fill(0, botY0, w, botY0 + 1, HUD_STRIP_LINE);
    }

    /** Column panels like LifePointScreen midCols. */
    public static void drawColumnPanels(GuiGraphics ctx, Rect[] cols) {
        if (cols == null) return;
        for (Rect r : cols) {
            if (r == null) continue;
            ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), COL_BG);
            ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, COL_BG_EDGE);
        }
    }

    /** Generic panel box (used for viewports). */
    public static void drawPanelBox(GuiGraphics ctx, Rect r) {
        if (r == null) return;
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), PANEL_BG);
        // top/bottom edges (match your screens)
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, PANEL_EDGE);
        ctx.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), PANEL_EDGE);
    }

    /** Flat row highlight style (select/hover) like LifePointScreen. */
    public static void drawFlatRow(GuiGraphics ctx, Rect r, boolean selected, boolean hovered) {
        if (r == null) return;
        int bg = selected ? ROW_BG_SEL : (hovered ? ROW_BG_HOV : ROW_BG);
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), bg);
        ctx.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, ROW_TOPLINE);
    }

    /** Scrollbar rendering like LifePointScreen (track + thumb). */
    public static Scrollbar drawScrollbar(GuiGraphics ctx, Rect vp, int contentH, int scroll) {
        if (vp == null || contentH <= vp.h()) return null;

        Rect track = new Rect(vp.x() + vp.w() - 8, vp.y() + 2, 6, vp.h() - 4);
        Scrollbar scrollbar = layoutScrollbar(track, contentH, vp.h(), scroll, 12);
        drawScrollbar(ctx, scrollbar, 0x66222222, 0xAA888888, 0x66222222);
        return scrollbar;
    }

    public static Scrollbar layoutScrollbar(Rect track, int contentUnits, int viewUnits, int scroll, int minThumb) {
        if (track == null || track.h() <= 0 || viewUnits <= 0) return null;

        int safeView = Math.max(1, viewUnits);
        int safeContent = Math.max(safeView, contentUnits);
        int maxScroll = Math.max(0, safeContent - safeView);

        if (maxScroll <= 0) {
            return new Scrollbar(track, track, 0);
        }

        int thumbH = clamp((int) Math.round(track.h() * (safeView / (double) safeContent)), minThumb, track.h());
        int thumbTravel = Math.max(1, track.h() - thumbH);
        int clampedScroll = clamp(scroll, 0, maxScroll);
        int thumbY = track.y() + (int) Math.round((clampedScroll / (double) maxScroll) * thumbTravel);

        return new Scrollbar(track, new Rect(track.x(), thumbY, track.w(), thumbH), maxScroll);
    }

    public static void drawScrollbar(GuiGraphics ctx, Scrollbar scrollbar, int trackColor, int thumbColor, int disabledTrackColor) {
        if (ctx == null || scrollbar == null) return;

        Rect track = scrollbar.track();
        ctx.fill(track.x(), track.y(), track.x() + track.w(), track.y() + track.h(), scrollbar.enabled() ? trackColor : disabledTrackColor);

        if (!scrollbar.enabled()) return;

        Rect thumb = scrollbar.thumb();
        ctx.fill(thumb.x(), thumb.y(), thumb.x() + thumb.w(), thumb.y() + thumb.h(), thumbColor);
    }

    public static int scrollFromThumb(Scrollbar scrollbar, int mouseY, int dragOffsetY) {
        if (scrollbar == null || !scrollbar.enabled()) return 0;

        Rect track = scrollbar.track();
        Rect thumb = scrollbar.thumb();
        int minThumbY = track.y();
        int maxThumbY = track.y() + track.h() - thumb.h();
        int newThumbY = clamp(mouseY - dragOffsetY, minThumbY, maxThumbY);
        double ratio = (newThumbY - minThumbY) / (double) Math.max(1, maxThumbY - minThumbY);
        return (int) Math.round(ratio * scrollbar.maxScroll());
    }

    public record Scrollbar(Rect track, Rect thumb, int maxScroll) {
        public boolean enabled() {
            return maxScroll > 0;
        }
    }

    public static boolean ptIn(Rect r, double mx, double my) {
        return r != null && mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h();
    }

    public static boolean ptInExpanded(Rect r, double mx, double my, int padX, int padY) {
        return r != null
                && mx >= r.x() - padX && mx < r.x() + r.w() + padX
                && my >= r.y() - padY && my < r.y() + r.h() + padY;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
