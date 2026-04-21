package net.minecraft.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

public final class GuiGraphics {
    private final GuiGraphicsExtractor delegate;

    public GuiGraphics(GuiGraphicsExtractor delegate) {
        this.delegate = delegate;
    }

    public static GuiGraphics wrap(GuiGraphicsExtractor delegate) {
        return new GuiGraphics(delegate);
    }

    public GuiGraphicsExtractor unwrap() {
        return delegate;
    }

    public Minecraft client() {
        return Minecraft.getInstance();
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

    public void enableScissor(int x1, int y1, int x2, int y2) {
        delegate.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        delegate.disableScissor();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        delegate.fill(x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        delegate.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    public void hLine(int x1, int x2, int y, int color) {
        delegate.horizontalLine(x1, x2, y, color);
    }

    public void vLine(int x, int y1, int y2, int color) {
        delegate.verticalLine(x, y1, y2, color);
    }

    public int drawString(Font font, String text, int x, int y, int color) {
        delegate.text(font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        delegate.text(font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        delegate.text(font, text, x, y, color);
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        delegate.text(font, text, x, y, color, shadow);
        return x + font.width(text);
    }

    public void drawCenteredString(Font font, String text, int centerX, int y, int color) {
        delegate.centeredText(font, text, centerX, y, color);
    }

    public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        delegate.centeredText(font, text, centerX, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int centerX, int y, int color) {
        delegate.centeredText(font, text, centerX, y, color);
    }

    public int drawTextWithShadow(Font font, Component text, int x, int y, int color) {
        delegate.text(font, text, x, y, color, true);
        return x + font.width(text);
    }

    public int drawTextWithShadow(Font font, FormattedCharSequence text, int x, int y, int color) {
        delegate.text(font, text, x, y, color, true);
        return x + font.width(text);
    }

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        delegate.textWithWordWrap(font, text, x, y, width, color);
    }

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color, boolean shadow) {
        delegate.textWithWordWrap(font, text, x, y, width, color, shadow);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        delegate.blit(pipeline, texture, x, y, (float) u, (float) v, width, height, texWidth, texHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight, int color) {
        delegate.blit(pipeline, texture, x, y, (float) u, (float) v, width, height, texWidth, texHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight, int color) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int regionWidth, int regionHeight, int texWidth, int texHeight) {
        delegate.blit(pipeline, texture, x, y, (float) u, (float) v, width, height, regionWidth, regionHeight, texWidth, texHeight);
    }

    public void blit(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int texWidth, int texHeight) {
        delegate.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, texWidth, texHeight);
    }

    public void renderItem(ItemStack stack, int x, int y) {
        delegate.item(stack, x, y);
    }

    public void renderItem(ItemStack stack, int x, int y, int seed) {
        delegate.item(stack, x, y, seed);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        delegate.itemDecorations(font, stack, x, y);
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String text) {
        delegate.itemDecorations(font, stack, x, y, text);
    }

    public void setTooltipForNextFrame(Component text, int mouseX, int mouseY) {
        delegate.setTooltipForNextFrame(text, mouseX, mouseY);
    }

    public void setTooltipForNextFrame(Font font, Component text, int mouseX, int mouseY) {
        delegate.setTooltipForNextFrame(text, mouseX, mouseY);
    }

    public void setComponentTooltipForNextFrame(Font font, java.util.List<Component> lines, int mouseX, int mouseY) {
        delegate.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
