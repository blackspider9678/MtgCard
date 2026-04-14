package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LegacyScreen extends Screen {
    protected LegacyScreen(Component title) {
        super(title);
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
