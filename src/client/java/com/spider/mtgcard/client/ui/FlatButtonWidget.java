package com.spider.mtgcard.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class FlatButtonWidget extends ClickableWidget {

    private final Runnable onPress;

    // local fallback colors (ARGB)
    private static final int BG        = 0xFF1E1E1E;
    private static final int BG_HOVER  = 0xFF2A2A2A;
    private static final int BG_OFF    = 0xFF141414;

    private static final int TEXT      = 0xFFE8E8E8;
    private static final int TEXT_OFF  = 0xFF909090;

    public FlatButtonWidget(int x, int y, int w, int h, Text message, Runnable onPress) {
        super(x, y, w, h, message);
        this.onPress = onPress;
    }

    // ✅ your mappings require renderWidget(...)
    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int bg = this.active ? (this.isHovered() ? BG_HOVER : BG) : BG_OFF;

        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);

        int border = UiTheme.BTN_BORDER;
        drawBorder(ctx, getX(), getY(), width, height, border);

        var tr = MinecraftClient.getInstance().textRenderer;
        int col = this.active ? TEXT : TEXT_OFF;

        int tx = getX() + (width - tr.getWidth(getMessage())) / 2;
        int ty = getY() + (height - 8) / 2;

        ctx.drawTextWithShadow(tr, getMessage(), tx, ty, col);
    }

    // ✅ new input API: Click + boolean
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (this.active && this.visible && this.isHovered()) {
            if (onPress != null) onPress.run();
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, this.getMessage());
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int argb) {
        ctx.fill(x, y, x + w, y + 1, argb);             // top
        ctx.fill(x, y + h - 1, x + w, y + h, argb);     // bottom
        ctx.fill(x, y, x + 1, y + h, argb);             // left
        ctx.fill(x + w - 1, y, x + w, y + h, argb);     // right
    }
}
