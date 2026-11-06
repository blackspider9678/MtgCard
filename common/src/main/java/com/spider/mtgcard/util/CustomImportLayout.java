package com.spider.mtgcard.util;

/** Shared preview/grid geometry, independent of Minecraft's version-specific UI APIs. */
public final class CustomImportLayout {
    public record Columns(int previewWidth, int gridLeft, int gridRight) {}
    private CustomImportLayout() {}

    public static Columns columns(int editorLeft, int cardWidth) {
        int right = Math.max(10, editorLeft - 12);
        int available = Math.max(0, right - 10);
        int preview = Math.min(240, Math.min(Math.max(0, (editorLeft - 30) / 4),
                Math.max(0, available - cardWidth - 16)));
        // At very small GUI sizes, prioritize one usable grid column.
        if (preview < 64) preview = 0;
        return new Columns(preview, 10 + (preview == 0 ? 0 : preview + 12), right);
    }

    public static int scrollToEntry(int viewIndex, int columns, int rowHeight, int viewportHeight,
                                    int currentScroll, int maxScroll) {
        int scroll = currentScroll;
        if (viewIndex >= 0) {
            int top = (viewIndex / Math.max(1, columns)) * rowHeight;
            int bottom = top + rowHeight;
            if (top < scroll) scroll = top;
            else if (bottom > scroll + viewportHeight) scroll = bottom - viewportHeight;
        }
        return Math.max(0, Math.min(Math.max(0, maxScroll), scroll));
    }
}
