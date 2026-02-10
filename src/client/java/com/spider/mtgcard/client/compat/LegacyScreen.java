package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LegacyScreen extends Screen {
    protected LegacyScreen(Component title) {
        super(title);
    }
}
