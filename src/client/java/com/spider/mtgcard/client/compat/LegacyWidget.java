package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public abstract class LegacyWidget extends AbstractWidget {
    protected LegacyWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
}
