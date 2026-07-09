package com.spider.mtgcard.client.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Optional;

/**
 * Compatibility wrapper for code written against the pre-26 GuiGraphics API.
 */
public final class GuiGraphics {
    private final GuiGraphicsExtractor delegate;

    public GuiGraphics(GuiGraphicsExtractor delegate) {
        this.delegate = delegate;
    }

    public GuiGraphicsExtractor unwrap() {
        return delegate;
    }

    public Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    public Font getTextRenderer() {
        return Minecraft.getInstance().font;
    }

    public int guiWidth() {
        return delegate.guiWidth();
    }

    public int guiHeight() {
        return delegate.guiHeight();
    }

    public Matrix3x2fStack pose() {
        return delegate.pose();
    }

    public void nextStratum() {
        delegate.nextStratum();
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        delegate.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        delegate.disableScissor();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        delegate.fill(x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int from, int to) {
        delegate.fillGradient(x1, y1, x2, y2, from, to);
    }

    public void hLine(int x1, int x2, int y, int color) {
        delegate.horizontalLine(x1, x2, y, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        delegate.verticalLine(x, y1, y2, color);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return font.width(text);
    }

    public int drawTextWithShadow(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawTextWithShadow(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        delegate.centeredText(font, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        delegate.centeredText(font, text, x, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        delegate.centeredText(font, text, x, y, color);
    }

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        delegate.textWithWordWrap(font, text, x, y, width, color);
    }

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color, boolean shadow) {
        delegate.textWithWordWrap(font, text, x, y, width, color, shadow);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        delegate.item(stack, x, y);
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        delegate.fakeItem(stack, x, y);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        delegate.itemDecorations(font, stack, x, y);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        delegate.itemDecorations(font, stack, x, y, text);
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        delegate.setTooltipForNextFrame(font, stack, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        delegate.setTooltipForNextFrame(font, text, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> text, int x, int y) {
        delegate.setTooltipForNextFrame(font, text, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<Component> text, Optional<TooltipComponent> component, int x, int y) {
        delegate.setTooltipForNextFrame(font, text, component, x, y);
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> text, int x, int y) {
        delegate.setComponentTooltipForNextFrame(font, text, x, y);
    }
}
