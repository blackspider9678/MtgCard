package com.spider.mtgcard.client.guidebook;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.compat.LegacyScreen;
import com.spider.mtgcard.client.gui.MtgGuiChrome;
import com.spider.mtgcard.guidebook.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GuideBookScreen extends LegacyScreen {

    private static final Identifier DEFAULT_ICON = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/gui/guidebook/book.png");

    private EditBox search;
    private GuideCategory selectedCategory = GuideCategory.HOME;
    private GuideChapter selectedChapter;

    private int leftX, topY, fullW, fullH;
    private int sidebarW = 170;
    private int padding = 8;

    // simple scroll for the chapter list
    private int listScroll = 0;

    // NEW: scroll for main chapter content
    private int contentScroll = 0;
    private int contentScrollMax = 0;

    public GuideBookScreen(Identifier startChapterId) {
        super(Component.translatable("guide.mtgcard.title"));
        if (startChapterId != null) {
            this.selectedChapter = GuideChapterRegistry.get(startChapterId);
            if (this.selectedChapter != null) {
                this.selectedCategory = this.selectedChapter.category();
            }
        }
        if (this.selectedChapter == null) {
            // default to first in HOME
            var home = GuideChapterRegistry.byCategory(GuideCategory.HOME);
            this.selectedChapter = home.isEmpty() ? null : home.getFirst();
            if (this.selectedChapter != null) {
                this.selectedCategory = this.selectedChapter.category();
            }
        }
    }

    private static final GuideCategory[] TAB_ORDER = new GuideCategory[] {
            GuideCategory.HOME,
            GuideCategory.BLOCKS,
            GuideCategory.ITEMS,
            GuideCategory.GENERAL
    };

    private int tabIndex(GuideCategory cat) {
        for (int i = 0; i < TAB_ORDER.length; i++) {
            if (TAB_ORDER[i] == cat) return i;
        }
        return 0;
    }

    private int tabY(GuideCategory cat) {
        return tabsTopY() + tabIndex(cat) * tabH();
    }

    private int tabsTopY() {
        return topY + padding + 50;
    }

    private int listTopY() {
        return tabsTopY() + (tabH() * TAB_ORDER.length) + 8;
    }

    private int listX() {
        return leftX + padding;
    }

    private int listW() {
        return sidebarW - padding * 2;
    }

    private int listH() {
        return fullH - listTopY() - padding;
    }

    private int tabX() { return leftX + padding; }
    private int tabW() { return sidebarW - padding * 2; }
    private int tabH() { return 16; } // bigger, easier to click

    @Override
    protected void init() {
        fullW = this.width;
        fullH = this.height;
        leftX = 0;
        topY = 0;

        int searchX = leftX + padding;
        int searchY = topY + padding + 10;
        int searchW = sidebarW - padding * 2;

        search = new EditBox(this.font, searchX, searchY, searchW, 18, Component.translatable("guide.mtgcard.search"));
        search.setMaxLength(64);
        search.setResponder(s -> listScroll = 0);
        addWidget(search);
        setInitialFocus(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // background
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        // panels
        int sidebarX = leftX;
        int sidebarY = topY;
        int sidebarH = fullH;

        int mainX = leftX + sidebarW;
        int mainY = topY;
        int mainW = fullW - sidebarW;
        int mainH = fullH;

        // Sidebar background
        ctx.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, 0xAA101010);
        // Main background
        ctx.fill(mainX, mainY, mainX + mainW, mainY + mainH, 0xAA0A0A0A);

        // Title
        ctx.drawString(font, Component.translatable("guide.mtgcard.title"), sidebarX + padding, sidebarY + padding, 0xFFFFFFFF);

        // Search label + widget
        ctx.drawString(font, Component.translatable("guide.mtgcard.search"), sidebarX + padding, sidebarY + padding + 20, 0xFFB0B0B0);
        search.render(ctx, mouseX, mouseY, delta);

        // Category tabs (simple text buttons)
        for (GuideCategory cat : TAB_ORDER) {
            drawCategoryTab(ctx, cat, tabX(), tabY(cat), tabW(), tabH());
        }

        // Chapter list
        drawChapterList(ctx, listX(), listTopY(), listW(), listH(), mouseX, mouseY);

        // Main content
        drawMain(ctx, mainX, mainY, mainW, mainH);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCategoryTab(GuiGraphics ctx, GuideCategory cat, int x, int y, int w, int h) {
        boolean active = (cat == selectedCategory);
        ctx.fill(x, y, x + w, y + h, active ? 0x55333333 : 0x22000000);

        Component label = Component.literal(cat.displayName);
        ctx.drawString(font, label, x + 4, y + 4, active ? 0xFFFFFFFF : 0xFFB0B0B0);
    }

    private void drawChapterList(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // clip region
        ctx.enableScissor(x, y, x + w, y + h);

        List<GuideChapter> chapters = filteredChapters();

        int rowH = 20;
        int yOff = y - listScroll;

        for (int i = 0; i < chapters.size(); i++) {
            GuideChapter ch = chapters.get(i);

            int rowY = yOff + i * rowH;
            if (rowY + rowH < y || rowY > y + h) continue;

            boolean selected = (selectedChapter != null && selectedChapter.id().equals(ch.id()));
            int bg = selected ? 0x55333333 : 0x22000000;

            ctx.fill(x, rowY, x + w, rowY + rowH, bg);

            // icon (16x16)
            int iconX = x + 2;
            int iconY = rowY + 2;

            ItemStack itemIcon = ch.itemIcon();
            if (itemIcon != null && !itemIcon.isEmpty()) {
                ctx.renderItem(itemIcon, iconX, iconY);
            } else {
                Identifier tex = ch.textureIcon();
                if (tex == null) tex = DEFAULT_ICON;
                ctx.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0f, 0f, 16, 16, 16, 16);

            }

            Component title = Component.translatable(ch.titleKey());
            ctx.drawString(font, title, x + 22, rowY + 6, 0xFFFFFFFF);
        }

        ctx.disableScissor();
    }

    private List<GuideChapter> filteredChapters() {
        List<GuideChapter> base = GuideChapterRegistry.byCategory(selectedCategory);
        String q = (search.getValue() == null ? "" : search.getValue()).trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) return base;

        List<GuideChapter> out = new ArrayList<>();
        for (GuideChapter c : base) {
            String title = Component.translatable(c.titleKey()).getString().toLowerCase(Locale.ROOT);
            if (title.contains(q)) out.add(c);
        }
        return out;
    }

    private void drawMain(GuiGraphics ctx, int x, int y, int w, int h) {
        if (selectedChapter == null) {
            ctx.drawString(font, Component.translatable("guide.mtgcard.empty"), x + padding, y + padding, 0xFFFFFFFF);
            return;
        }

        // Header
        int headerY = y + padding;
        int iconX = x + padding;
        int iconY = headerY;

        ItemStack itemIcon = selectedChapter.itemIcon();
        if (itemIcon != null && !itemIcon.isEmpty()) {
            ctx.renderItem(itemIcon, iconX, iconY);
        } else {
            Identifier tex = selectedChapter.textureIcon();
            if (tex == null) tex = DEFAULT_ICON;
            ctx.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0f, 0f, 32, 32, 32, 32);
        }

        Component title = Component.translatable(selectedChapter.titleKey());
        ctx.drawString(font, title, iconX + 40, headerY + 10, 0xFFFFFFFF);

        int contentX = x + padding;
        int contentY = headerY + 40;
        int contentW = w - padding * 2;
        int contentBottom = y + h - padding;

        int contentH = contentBottom - contentY;

        // Compute total content height (for scroll max)
        int totalH = measureSectionsHeight(selectedChapter.sections(), contentW);
        contentScrollMax = Math.max(0, totalH - contentH);
        contentScroll = clamp(contentScroll, 0, contentScrollMax);

        // Clip to content region
        ctx.enableScissor(contentX, contentY, contentX + contentW, contentBottom);
        int cy = contentY - contentScroll;


        var sections = selectedChapter.sections();
        if (sections == null || sections.isEmpty()) {
            ctx.drawString(font, Component.translatable("guide.mtgcard.chapter_blank"), contentX, contentY, 0xFFB0B0B0);
            ctx.disableScissor();
            return;
        }

        for (var section : sections) {
            // If it's far above, we still need to advance cy correctly (handled by drawWrapped returning cy)
            if (section instanceof GuideSection.Heading hd) {
                cy = drawWrapped(ctx, hd.text(), contentX, cy, contentW, 0xFFFFFFFF) + 6;
            }
            else if (section instanceof GuideSection.Paragraph p) {
                cy = drawWrapped(ctx, p.text(), contentX, cy, contentW, 0xFFDDDDDD) + 10;
            }
            else if (section instanceof GuideSection.Bullets b) {
                for (Component bullet : b.bullets()) {
                    cy = drawWrapped(ctx, Component.literal("• ").append(bullet), contentX, cy, contentW, 0xFFDDDDDD) + 4;
                }
                cy += 6;
            }
        }

        ctx.disableScissor();

        // Optional: draw a scrollbar if needed
        if (contentScrollMax > 0) {
            var scrollbar = MtgGuiChrome.layoutScrollbar(
                    new MtgGuiChrome.Rect(contentX + contentW - 4, contentY, 4, contentH),
                    contentH + contentScrollMax,
                    contentH,
                    contentScroll,
                    12
            );
            MtgGuiChrome.drawScrollbar(ctx, scrollbar, 0x33000000, 0x88FFFFFF, 0x33000000);
        }
    }

    private int measureSectionsHeight(List<GuideSection> sections, int width) {
        if (sections == null || sections.isEmpty()) return 0;

        int h = 0;
        for (var section : sections) {
            if (section instanceof GuideSection.Heading hd) {
                h += measureWrappedHeight(hd.text(), width) + 6;
            } else if (section instanceof GuideSection.Paragraph p) {
                h += measureWrappedHeight(p.text(), width) + 10;
            } else if (section instanceof GuideSection.Bullets b) {
                for (Component bullet : b.bullets()) {
                    h += measureWrappedHeight(Component.literal("• ").append(bullet), width) + 4;
                }
                h += 6;
            }
        }
        return h;
    }

    private int measureWrappedHeight(Component text, int width) {
        var lines = font.split(text, width);
        return lines.size() * 10; // matches your drawWrapped line height
    }

    private int drawWrapped(GuiGraphics ctx, Component text, int x, int y, int width, int color) {
        // Wrap into multiple OrderedText lines
        var lines = font.split(text, width);
        int cy = y;
        for (var line : lines) {
            ctx.drawString(font, line, x, cy, color);
            cy += 10; // line height
        }
        return cy;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean down) {

        // Only process press
        if (down) return false;

        // Let children (TextFieldWidget) have first dibs
        if (super.mouseClicked(click, true)) return true;

        double mouseX = click.x();
        double mouseY = click.y();

        int tx = tabX();
        int tw = tabW();
        int th = tabH();

        for (GuideCategory cat : TAB_ORDER) {
            if (hit(tx, tabY(cat), tw, th, mouseX, mouseY)) {
                setCategory(cat);
                return true;
            }
        }

        // Chapter list clicks
        int lx = listX();
        int ly = listTopY();
        int lw = listW();
        int lh = listH();

        List<GuideChapter> chapters = filteredChapters();
        int rowH = 20;
        int idx = (int) ((mouseY - (ly - listScroll)) / rowH);

        if (hit(lx, ly, lw, lh, mouseX, mouseY) && idx >= 0 && idx < chapters.size()) {
            selectedChapter = chapters.get(idx);
            contentScroll = 0;          // NEW
            contentScrollMax = 0;       // NEW (recomputed next render)
            return true;
        }

        return false;
    }

    private void setCategory(GuideCategory cat) {
        selectedCategory = cat;
        listScroll = 0;
        contentScroll = 0;          // NEW
        contentScrollMax = 0;       // NEW
        var list = GuideChapterRegistry.byCategory(selectedCategory);
        selectedChapter = list.isEmpty() ? null : list.getFirst();
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int lx = listX();
        int ly = listTopY();
        int lw = listW();
        int lh = listH();

        if (hit(lx, ly, lw, lh, mouseX, mouseY)) {
            int delta = (int)(-verticalAmount * 12);
            listScroll = Math.max(0, listScroll + delta);
            return true;
        }

        // NEW: scroll main content (only if overflow exists)
        int mainX = leftX + sidebarW;
        int mainY = topY;
        int mainW = fullW - sidebarW;
        int mainH = fullH;

        int contentX = mainX + padding;
        int contentY = mainY + padding + 40; // matches drawMain
        int contentW = mainW - padding * 2;
        int contentBottom = mainY + mainH - padding;
        int contentH = contentBottom - contentY;

        if (hit(contentX, contentY, contentW, contentH, mouseX, mouseY) && contentScrollMax > 0) {
            int delta = (int)(-verticalAmount * 14);
            contentScroll = clamp(contentScroll + delta, 0, contentScrollMax);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static boolean hit(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
