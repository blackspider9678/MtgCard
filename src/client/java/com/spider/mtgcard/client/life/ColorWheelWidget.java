package com.spider.mtgcard.client.life;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;

import java.util.function.IntConsumer;

public final class ColorWheelWidget extends ClickableWidget {
    private final IntConsumer onPick;
    private boolean dragging = false;

    public ColorWheelWidget(int x, int y, int w, int h, IntConsumer onPick) {
        super(x, y, w, h, Text.empty());
        this.onPick = onPick;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // simple panel
        ctx.fill(x, y, x + w, y + h, 0xAA000000);
        ctx.drawHorizontalLine(x, x + w - 1, y, 0xFF404040);
        ctx.drawHorizontalLine(x, x + w - 1, y + h - 1, 0xFF404040);
        ctx.drawVerticalLine(x, y, y + h - 1, 0xFF404040);
        ctx.drawVerticalLine(x + w - 1, y, y + h - 1, 0xFF404040);

        // label
        ctx.drawTextWithShadow(
                net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                "Color",
                x + 4, y + 4,
                0xFFFFFFFF
        );

        // preview crosshair when hovering
        if (isMouseOver(mouseX, mouseY)) {
            ctx.drawHorizontalLine(mouseX - 2, mouseX + 2, mouseY, 0xFFFFFFFF);
            ctx.drawVerticalLine(mouseX, mouseY - 2, mouseY + 2, 0xFFFFFFFF);
        }
    }

    public void onClick(double mouseX, double mouseY) {
        pick(mouseX, mouseY);
        dragging = true;
    }

    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (dragging) pick(mouseX, mouseY);
    }

    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void pick(double mouseX, double mouseY) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        double u = (mouseX - x) / (double) w;
        double v = (mouseY - y) / (double) h;
        u = clamp01(u);
        v = clamp01(v);

        // cheap HSV-ish gradient: hue from u, brightness from v
        int rgb = hsvToRgb((float) u, 1.0f, (float) (1.0 - v));
        onPick.accept(rgb & 0xFFFFFF);
    }

    private static double clamp01(double d) {
        if (d < 0) return 0;
        if (d > 1) return 1;
        return d;
    }

    // Minimal HSV->RGB
    private static int hsvToRgb(float h, float s, float v) {
        float r, g, b;
        int i = (int) (h * 6.0f);
        float f = (h * 6.0f) - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        int ir = (int) (r * 255.0f);
        int ig = (int) (g * 255.0f);
        int ib = (int) (b * 255.0f);
        return (ir << 16) | (ig << 8) | ib;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // Keep narration minimal (prevents the abstract-method error)
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, Text.literal("Color picker"));
    }
}
