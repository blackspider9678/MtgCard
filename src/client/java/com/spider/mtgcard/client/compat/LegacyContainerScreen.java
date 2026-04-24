package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class LegacyContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected LegacyContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected LegacyContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        render(new GuiGraphics(graphics), mouseX, mouseY, delta);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        GuiGraphicsExtractor extractor = graphics.unwrap();
        renderBg(graphics, delta, mouseX, mouseY);
        super.extractContents(extractor, mouseX, mouseY, delta);
        super.extractCarriedItem(extractor, mouseX, mouseY);
        super.extractSnapbackItem(extractor);
    }

    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        renderLabels(new GuiGraphics(graphics), mouseX, mouseY);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics.unwrap(), mouseX, mouseY);
    }

    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics.unwrap(), mouseX, mouseY);
    }

    protected void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.unwrap(), mouseX, mouseY, delta);
    }

    protected final boolean applyDefaultGuiScale() {
        return MtgGuiScaleHelper.applyPreferredGuiScale(this, MtgGuiScaleHelper.BLOCK_GUI_SCALE);
    }

    protected final boolean applyPreferredGuiScale(int preferredScale) {
        return MtgGuiScaleHelper.applyPreferredGuiScale(this, preferredScale);
    }

    protected final boolean applyFixedGuiScale(int fixedScale) {
        return MtgGuiScaleHelper.applyFixedGuiScale(this, fixedScale);
    }

    protected final boolean applyAutoFitGuiScale(int guiWidth, int guiHeight) {
        return MtgGuiScaleHelper.applyAutoFitGuiScale(this, MtgGuiScaleHelper.BLOCK_GUI_SCALE, guiWidth, guiHeight);
    }

    protected final boolean applyAutoFitGuiScaleWithSidePreview(int guiWidth, int guiHeight, int previewWidth, int previewHeight) {
        return MtgGuiScaleHelper.applyAutoFitGuiScaleWithSidePreview(
                this,
                MtgGuiScaleHelper.BLOCK_GUI_SCALE,
                guiWidth,
                guiHeight,
                previewWidth,
                previewHeight
        );
    }

    @Override
    public void removed() {
        MtgGuiScaleHelper.restoreGuiScale(this);
        super.removed();
    }
}
