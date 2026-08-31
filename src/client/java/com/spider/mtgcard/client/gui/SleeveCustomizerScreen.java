package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.api.SleeveRegistry;
import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.sleeve.SleeveCustomizerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Sleeve picker using the vanilla Stonecutter GUI and its interaction layout. */
public final class SleeveCustomizerScreen extends LegacyContainerScreen<SleeveCustomizerMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
    private static final Identifier RECIPE = Identifier.withDefaultNamespace("textures/gui/sprites/container/stonecutter/recipe.png");
    private static final Identifier RECIPE_HIGHLIGHTED = Identifier.withDefaultNamespace("textures/gui/sprites/container/stonecutter/recipe_highlighted.png");
    private static final Identifier RECIPE_SELECTED = Identifier.withDefaultNamespace("textures/gui/sprites/container/stonecutter/recipe_selected.png");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("textures/gui/sprites/container/stonecutter/scroller.png");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("textures/gui/sprites/container/stonecutter/scroller_disabled.png");
    private static final int COLS = 4, VISIBLE_ROWS = 3;
    private float scrollOffs;
    private int startIndex;
    private boolean scrolling;

    public SleeveCustomizerScreen(SleeveCustomizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = 73;
        titleLabelY--;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        renderSleeveTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float delta, int mouseX, int mouseY) {
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        renderScroller(g);
        renderGrid(g, mouseX, mouseY);
    }

    private void renderScroller(GuiGraphics g) {
        int y = topPos + 15 + (int) (41.0F * scrollOffs);
        g.blit(RenderPipelines.GUI_TEXTURED, isScrollBarActive() ? SCROLLER : SCROLLER_DISABLED,
                leftPos + 119, y, 0, 0, 12, 15, 12, 15);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.input().isEmpty()) return;
        int first = startIndex, total = SleeveRegistry.values().size() + 1;
        for (int local = 0; local < COLS * VISIBLE_ROWS; local++) {
            int index = first + local;
            if (index >= total) break;
            int x = leftPos + 52 + local % COLS * 16;
            int rowY = topPos + 14 + local / COLS * 18;
            int buttonY = rowY + 1;
            boolean hovered = inside(mouseX, mouseY, x, rowY + 2, 16, 18);
            Identifier sprite = menu.selectedIndex() == index ? RECIPE_SELECTED : hovered ? RECIPE_HIGHLIGHTED : RECIPE;
            g.blit(RenderPipelines.GUI_TEXTURED, sprite, x, buttonY, 0, 0, 16, 18, 16, 18);
            if (index == 0) g.renderItem(new ItemStack(Items.BARRIER), x, rowY + 2);
            else g.blit(RenderPipelines.GUI_TEXTURED, SleeveRegistry.values().get(index - 1).backTexture(),
                    x, rowY + 2, 0, 0, 16, 16, 16, 16, 16, 16);
        }
    }

    private void renderSleeveTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = hoveredSleeve(mouseX, mouseY);
        if (inside(mouseX, mouseY, leftPos + 143, topPos + 33, 16, 16)) index = menu.selectedIndex();
        if (index < 0) return;
        if (index == 0) {
            g.setTooltipForNextFrame(font, Component.literal("No Sleeve"), mouseX, mouseY);
            return;
        }
        var sleeve = SleeveRegistry.values().get(index - 1);
        Component tooltip = sleeve.artist() == null ? sleeve.displayName()
                : sleeve.displayName().copy().append("\nArtwork by ").append(sleeve.artist());
        g.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    @Override public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0) {
            int index = hoveredSleeve(click.x(), click.y());
            if (index >= 0) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                clickButton(index);
                return true;
            }
            if (inside(click.x(), click.y(), leftPos + 119, topPos + 9, 12, 54) && isScrollBarActive()) {
                scrolling = true;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (scrolling && isScrollBarActive()) {
            int top = topPos + 14;
            scrollOffs = Mth.clamp(((float) click.y() - top - 7.5F) / 39.0F, 0.0F, 1.0F);
            startIndex = (int) (scrollOffs * offscreenRows() + 0.5F) * COLS;
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent click) {
        scrolling = false;
        return super.mouseReleased(click);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (super.mouseScrolled(x, y, horizontal, vertical)) return true;
        if (isScrollBarActive()) {
            int rows = offscreenRows();
            scrollOffs = Mth.clamp(scrollOffs - (float) vertical / rows, 0.0F, 1.0F);
            startIndex = (int) (scrollOffs * rows + 0.5F) * COLS;
        }
        return true;
    }

    private int hoveredSleeve(double mouseX, double mouseY) {
        if (menu.input().isEmpty()) return -1;
        int first = startIndex, total = SleeveRegistry.values().size() + 1;
        for (int local = 0; local < COLS * VISIBLE_ROWS; local++) {
            int index = first + local;
            if (index >= total) break;
            int x = leftPos + 52 + local % COLS * 16, y = topPos + 14 + local / COLS * 18;
            if (inside(mouseX, mouseY, x, y, 16, 18)) return index;
        }
        return -1;
    }

    private int offscreenRows() {
        int rows = (SleeveRegistry.values().size() + 1 + COLS - 1) / COLS;
        return Math.max(0, rows - VISIBLE_ROWS);
    }

    private boolean isScrollBarActive() {
        return !menu.input().isEmpty() && SleeveRegistry.values().size() + 1 > COLS * VISIBLE_ROWS;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private void clickButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
}
