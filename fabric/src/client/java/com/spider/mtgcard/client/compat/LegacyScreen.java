package com.spider.mtgcard.client.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class LegacyScreen extends Screen {
    private boolean renderingScreenContents;

    protected LegacyScreen(Component title) {
        super(title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Draw widgets without reapplying the 1.21.1 background blur over
        // panels and text that the ported screen has already drawn.
        renderingScreenContents = true;
        try {
            super.render(graphics, mouseX, mouseY, delta);
        } finally {
            renderingScreenContents = false;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!renderingScreenContents) {
            super.renderBackground(graphics, mouseX, mouseY, delta);
        }
    }

    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClicked(new MouseButtonEvent(mouseX, mouseY, button), false);
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return mouseReleased(new MouseButtonEvent(mouseX, mouseY, button));
    }

    public boolean mouseReleased(MouseButtonEvent click) {
        return super.mouseReleased(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return mouseDragged(new MouseButtonEvent(mouseX, mouseY, button), dragX, dragY);
    }

    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        return super.mouseDragged(click.x(), click.y(), click.button(), dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent key) {
        return super.keyPressed(key.key(), key.scancode(), key.modifiers());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return charTyped(new CharacterEvent(codePoint, modifiers));
    }

    public boolean charTyped(CharacterEvent input) {
        return super.charTyped((char) input.codepoint(), input.modifiers());
    }

    protected final boolean applyDefaultGuiScale() {
        return MtgGuiScaleHelper.applyPreferredGuiScale(this, MtgGuiScaleHelper.BLOCK_GUI_SCALE);
    }

    protected final boolean applyPreferredGuiScale(int preferredScale) {
        return MtgGuiScaleHelper.applyPreferredGuiScale(this, preferredScale);
    }

    protected final boolean applyFixedGuiScale(int fixedScale) {
        return MtgGuiScaleHelper.applyFixedGuiScale(this, fixedScale);
    }

    protected final boolean applyAutoFitGuiScale(int guiWidth, int guiHeight) {
        return MtgGuiScaleHelper.applyAutoFitGuiScale(this, MtgGuiScaleHelper.BLOCK_GUI_SCALE, guiWidth, guiHeight);
    }

    protected final boolean applyAutoFitGuiScaleWithSidePreview(int guiWidth, int guiHeight, int previewWidth, int previewHeight) {
        return MtgGuiScaleHelper.applyAutoFitGuiScaleWithSidePreview(
                this,
                MtgGuiScaleHelper.BLOCK_GUI_SCALE,
                guiWidth,
                guiHeight,
                previewWidth,
                previewHeight
        );
    }

    @Override
    public void removed() {
        MtgGuiScaleHelper.restoreGuiScale(this);
        super.removed();
    }
}
