package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class LegacyWidget extends AbstractWidget {
    protected LegacyWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected final void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderWidget(new GuiGraphics(graphics), mouseX, mouseY, delta);
    }

    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    /** Bridge pre-26.3 custom-widget input callbacks onto the MouseButtonEvent API. */
    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        onClick(click.x(), click.y());
    }

    public void onClick(double mouseX, double mouseY) {
    }

    @Override
    public void onRelease(MouseButtonEvent click) {
        onRelease(click.x(), click.y());
    }

    public void onRelease(double mouseX, double mouseY) {
    }

    @Override
    protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        onDrag(click.x(), click.y(), deltaX, deltaY);
    }

    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
    }
}
