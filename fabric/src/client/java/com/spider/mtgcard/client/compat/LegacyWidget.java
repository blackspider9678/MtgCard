package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class LegacyWidget extends AbstractWidget {
    protected LegacyWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(new MouseButtonEvent(mouseX, mouseY, button), false);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click.x(), click.y(), click.button());
    }
}
