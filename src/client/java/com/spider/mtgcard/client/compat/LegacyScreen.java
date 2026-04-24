package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LegacyScreen extends Screen {
    protected LegacyScreen(Component title) {
        super(title);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        render(new GuiGraphics(graphics), mouseX, mouseY, delta);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics.unwrap(), mouseX, mouseY, delta);
    }

    protected void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.unwrap(), mouseX, mouseY, delta);
    }

    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
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
