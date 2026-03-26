package com.spider.mtgcard.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class FlatButtonWidget extends AbstractWidget {
    private final Runnable onPress;
    public FlatButtonWidget(int x, int y, int w, int h, Component message, Runnable onPress) {
        super(x, y, w, h, message);
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {

    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {

    }
/*

    // local fallback colors (ARGB)
    private static final int BG        = 0xFF1E1E1E;
    private static final int BG_HOVER  = 0xFF2A2A2A;
    private static final int BG_OFF    = 0xFF141414;

    private static final int TEXT      = 0xFFE8E8E8;
    private static final int TEXT_OFF  = 0xFF909090;

    // ✅ your mappings require renderWidget(...)
    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int bg = this.active ? (this.isHovered() ? BG_HOVER : BG) : BG_OFF;

        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);

        int border = UiTheme.BTN_BORDER;
        drawBorder(ctx, getX(), getY(), width, height, border);

        var tr = Minecraft.getInstance().font;
        int col = this.active ? TEXT : TEXT_OFF;

        int tx = getX() + (width - tr.width(getMessage())) / 2;
        int ty = getY() + (height - 8) / 2;

        ctx.text(tr, getMessage(), tx, ty, col);
    }

    // ✅ new input API: Click + boolean
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        if (this.active && this.visible && this.isHovered()) {
            if (onPress != null) onPress.run();
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, this.getMessage());
    }

    private static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int argb) {
        ctx.fill(x, y, x + w, y + 1, argb);             // top
        ctx.fill(x, y + h - 1, x + w, y + h, argb);     // bottom
        ctx.fill(x, y, x + 1, y + h, argb);             // left
        ctx.fill(x + w - 1, y, x + w, y + h, argb);     // right
    }
 */
}
