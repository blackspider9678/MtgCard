package com.spider.mtgcard.client.ui;

/**
 * Shared preview layout math for:
 * - GUI previews (DrawContext)
 * - World render previews (BER)
 *
 * This class ONLY computes geometry. It does not render anything.
 *
 * Locked rules (no header bar):
 * - Icon in the background (iconBox fills available space, square-fit)
 * - Life large in the front (centered on iconBox)
 * - Name at the bottom (reserved name strip)
 *
 * Recommended usage:
 * - GUI: call computeNoHeader(...)
 * - DisplayBlock: panelW/H = blocks * PX_PER_BLOCK; then divide px->block units for rendering
 */
public final class PreviewLayout {

    /** Pixels per 1 block of screen for world previews (DisplayBlock). */
    public static final int PX_PER_BLOCK = 96;

    /** Default padding inside the panel (in pixels). */
    public static final int DEFAULT_PAD = 10;

    /** Reserved height for the name line at bottom (in pixels). */
    public static final int DEFAULT_NAME_LINE_H = 16;

    /** Simple integer rect. */
    public record Rect(int x, int y, int w, int h) {
        public int x2() { return x + w; }
        public int y2() { return y + h; }
        public int cx() { return x + (w / 2); }
        public int cy() { return y + (h / 2); }
    }

    /** Output of the layout solver. */
    public record Layout(
            Rect panel,        // full panel rect you provided
            Rect content,      // content rect (inside padding)
            Rect nameStrip,    // reserved strip for the name (bottom)
            Rect iconBox,      // where the icon should be drawn (square fit, centered in content-without-name)
            int  nameBaselineY,// y position for name text baseline-ish
            int  lifeCenterX,  // center for the big life text
            int  lifeCenterY,  // center for the big life text
            float lifeScale    // suggested scale factor for life text
    ) {}

    private PreviewLayout() {}

    /**
     * Convenience: no header bar, with your locked defaults.
     */
    public static Layout computeNoHeader(int panelX, int panelY, int panelW, int panelH) {
        return computeNoHeader(panelX, panelY, panelW, panelH, DEFAULT_PAD, DEFAULT_NAME_LINE_H);
    }

    /**
     * Convenience: no header bar, custom padding / name strip.
     */
    public static Layout computeNoHeader(int panelX, int panelY, int panelW, int panelH, int pad, int nameLineH) {
        return compute(panelX, panelY, panelW, panelH, 0, pad, nameLineH);
    }

    /**
     * Compute layout for a preview panel.
     *
     * @param panelX outer panel x
     * @param panelY outer panel y
     * @param panelW outer panel width
     * @param panelH outer panel height
     * @param headerH header strip height (set to 0 for "no header bar")
     * @param pad padding inside panel (typically 10)
     * @param nameLineH reserved height for name line at bottom (typically 16)
     */
    public static Layout compute(int panelX, int panelY, int panelW, int panelH,
                                 int headerH, int pad, int nameLineH) {

        // guard panel itself
        if (panelW < 1) panelW = 1;
        if (panelH < 1) panelH = 1;

        Rect panel = new Rect(panelX, panelY, panelW, panelH);

        // Content area (inside padding, below header)
        headerH = Math.max(0, headerH);
        pad = Math.max(0, pad);
        nameLineH = Math.max(0, nameLineH);

        int cx0 = panelX + pad;
        int cy0 = panelY + headerH + pad;
        int cw  = panelW - pad * 2;
        int ch  = panelH - headerH - pad * 2;

        // guard against tiny panels
        if (cw < 1) cw = 1;
        if (ch < 1) ch = 1;

        Rect content = new Rect(cx0, cy0, cw, ch);

        // Reserve a name strip at the bottom of the content
        int nameStripH = Math.min(nameLineH, ch); // don't exceed content height
        Rect nameStrip = new Rect(cx0, cy0 + (ch - nameStripH), cw, nameStripH);

        // Remaining height for icon area (content excluding name strip)
        int contentHNoName = Math.max(1, ch - nameStripH);

        // Square-fit icon into available space (content excluding name strip)
        int iconBoxSize = Math.min(cw, contentHNoName);
        int ix = cx0 + (cw - iconBoxSize) / 2;
        int iy = cy0 + (contentHNoName - iconBoxSize) / 2;

        Rect iconBox = new Rect(ix, iy, iconBoxSize, iconBoxSize);

        // Name baseline inside the reserved strip
        // (baseline-ish: near the bottom but not flush)
        int nameBaselineY = nameStrip.y() + nameStrip.h() - 4;

        // Life text centered on the icon box
        int lifeCenterX = iconBox.cx();
        int lifeCenterY = iconBox.cy();

        // Scale suggestion: tuned to “stretch-to-fit” feel without exploding
        // (iconBox ~ 96px per block -> 1 block ~ 1.37 scale with this)
        float lifeScale = clamp(2.0f, 6.0f, iconBoxSize / 70f);

        return new Layout(panel, content, nameStrip, iconBox, nameBaselineY, lifeCenterX, lifeCenterY, lifeScale);
    }

    private static float clamp(float lo, float hi, float v) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
