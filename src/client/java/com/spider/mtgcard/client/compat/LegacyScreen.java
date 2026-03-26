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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        render(GuiGraphics.wrap(graphics), mouseX, mouseY, delta);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics.unwrap(), mouseX, mouseY, delta);
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.unwrap(), mouseX, mouseY, delta);
    }

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
