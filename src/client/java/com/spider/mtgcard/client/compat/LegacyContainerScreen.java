package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class LegacyContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected LegacyContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    protected LegacyContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title);
        this.imageWidth = width;
        this.imageHeight = height;
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
