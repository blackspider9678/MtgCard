package com.spider.mtgcard.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlatButtonWidget extends AbstractWidget {
    private static final int BG = 0xFF1E1E1E;
    private static final int BG_HOVER = 0xFF2A2A2A;
    private static final int BG_OFF = 0xFF141414;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int TEXT_OFF = 0xFF909090;

    private final Runnable onPress;

    public FlatButtonWidget(int x, int y, int w, int h, Component message, Runnable onPress) {
        super(x, y, w, h, message);
        this.onPress = onPress;
    }

    @Override
    protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int bg = this.active ? (this.isHovered() ? BG_HOVER : BG) : BG_OFF;

        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);
        drawBorder(ctx, getX(), getY(), width, height, UiTheme.BTN_BORDER);

        var tr = Minecraft.getInstance().font;
        int color = this.active ? TEXT : TEXT_OFF;
        int textX = getX() + (width - tr.width(getMessage())) / 2;
        int textY = getY() + (height - 8) / 2;
        ctx.drawString(tr, getMessage(), textX, textY, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        if (this.active && this.visible && this.isHovered()) {
            if (onPress != null) {
                onPress.run();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, this.getMessage());
    }

    private static void drawBorder(GuiGraphics ctx, int x, int y, int w, int h, int argb) {
        ctx.fill(x, y, x + w, y + 1, argb);
        ctx.fill(x, y + h - 1, x + w, y + h, argb);
        ctx.fill(x, y, x + 1, y + h, argb);
        ctx.fill(x + w - 1, y, x + w, y + h, argb);
    }
}
