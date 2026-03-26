package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.lang.reflect.Field;

public abstract class LegacyContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final Field SUPER_IMAGE_WIDTH;
    private static final Field SUPER_IMAGE_HEIGHT;

    static {
        try {
            SUPER_IMAGE_WIDTH = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            SUPER_IMAGE_WIDTH.setAccessible(true);
            SUPER_IMAGE_HEIGHT = AbstractContainerScreen.class.getDeclaredField("imageHeight");
            SUPER_IMAGE_HEIGHT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected int imageWidth;
    protected int imageHeight;

    protected LegacyContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = super.imageWidth;
        this.imageHeight = super.imageHeight;
    }

    protected LegacyContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
        this.imageWidth = width;
        this.imageHeight = height;
    }

    @Override
    protected void init() {
        syncImageSize();
        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        render(GuiGraphics.wrap(graphics), mouseX, mouseY, delta);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        renderBg(GuiGraphics.wrap(graphics), delta, mouseX, mouseY);
        super.extractContents(graphics, mouseX, mouseY, delta);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        renderLabels(GuiGraphics.wrap(graphics), mouseX, mouseY);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics.unwrap(), mouseX, mouseY, delta);
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.unwrap(), mouseX, mouseY, delta);
    }

    public void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void syncImageSize() {
        try {
            SUPER_IMAGE_WIDTH.setInt(this, imageWidth);
            SUPER_IMAGE_HEIGHT.setInt(this, imageHeight);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to sync container image size", e);
        }
    }
}
