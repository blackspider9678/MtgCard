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

    @Override
    public void removed() {
        MtgGuiScaleHelper.restoreGuiScale(this);
        super.removed();
    }
}
