package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.render.DiceTextureCache;
import com.spider.mtgcard.client.ui.UiTheme;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.dice.DiceCustomizerIngredients;
import com.spider.mtgcard.dice.DiceCustomizerPackets;
import com.spider.mtgcard.dice.DiceCustomizerScreenHandler;
import com.spider.mtgcard.dice.DiceGradientType;
import com.spider.mtgcard.dice.DicePatternResolver;
import com.spider.mtgcard.dice.DiceType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class DiceCustomizerScreen extends LegacyContainerScreen<DiceCustomizerScreenHandler> {
    private static final int TAB_X = 12;
    private static final int TAB_Y = 24;
    private static final int TAB_W = 56;
    private static final int TAB_H = 18;
    private static final int PICKER_X = 16;
    private static final int PICKER_Y = 58;
    private static final int PICKER_W = 112;
    private static final int PICKER_H = 44;
    private static final int HUE_X = 134;
    private static final int HUE_W = 10;
    private static final int HEX_X = 158;
    private static final int HEX_Y = 68;
    private static final int HEX_W = 58;
    private static final int FIELD_Y = 118;
    private static final int FIELD_W = 34;
    private static final int PREVIEW_X = 250;
    private static final int PREVIEW_Y = 54;
    private static final int PREVIEW_SIZE = 82;
    private static final int GLINT_ROWS = 18;
    private static final int GLINT_BANDS = 2;

    private ColorTab selectedTab = ColorTab.PRIMARY;
    private int primaryColor = DiceAppearance.DEFAULT_PRIMARY;
    private int secondaryColor = DiceAppearance.DEFAULT_SECONDARY;
    private int borderColor = DiceAppearance.DEFAULT_BORDER;
    private int textColor = DiceAppearance.DEFAULT_TEXT;
    private int bannerColor = DiceAppearance.DEFAULT_BANNER;
    private DiceGradientType gradientType = DiceAppearance.DEFAULT.gradientType();
    private int diceTypeIndex = DiceType.D20.ordinal();
    private float hue;
    private float saturation;
    private float value;
    private boolean draggingColor;
    private boolean draggingHue;

    private Button gradientButton;
    private Button diceTypeButton;
    private Button craftButton;
    private EditBox hexField;
    private EditBox redField;
    private EditBox greenField;
    private EditBox blueField;
    private boolean updatingColorFields;
    private String lastHexFieldValue = "";
    private String lastRedFieldValue = "";
    private String lastGreenFieldValue = "";
    private String lastBlueFieldValue = "";

    public DiceCustomizerScreen(DiceCustomizerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, DiceCustomizerScreenHandler.GUI_W, DiceCustomizerScreenHandler.GUI_H);
        this.inventoryLabelY = 178;
        syncHsvFromActiveColor();
    }

    @Override
    protected void init() {
        if (applyAutoFitGuiScale(this.imageWidth, this.imageHeight)) return;
        super.init();

        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        createColorFields();

        gradientButton = addRenderableWidget(Button.builder(gradientLabel(), button -> {
            cycleGradient();
            gradientButton.setMessage(gradientLabel());
        }).bounds(leftPos + 152, topPos + 112, 74, 18).build());

        diceTypeButton = addRenderableWidget(Button.builder(Component.literal(currentDiceType().label()), button -> {
            diceTypeIndex = (diceTypeIndex + 1) % DiceType.values().length;
            diceTypeButton.setMessage(Component.literal(currentDiceType().label()));
        }).bounds(leftPos + 304, topPos + 23, 44, 18).build());

        craftButton = addRenderableWidget(Button.builder(Component.literal("Craft Dice"), button ->
                ClientPlayNetworking.send(new DiceCustomizerPackets.CraftDiceC2S(
                        menu.containerId,
                        currentDiceType().sides(),
                        buildPreviewAppearance()
                ))
        ).bounds(leftPos + 292, topPos + 144, 60, 18).build());
        syncColorFieldsFromActiveColor();
        updateCraftButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateCraftButton();
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        ctx.fill(x, y, x + imageWidth, y + imageHeight, UiTheme.PANEL);
        drawBorder(ctx, x, y, imageWidth, imageHeight, UiTheme.PANEL_BORDER);
        ctx.fill(x + 1, y + 174, x + imageWidth - 1, y + 175, UiTheme.PANEL_BORDER);

        renderTabs(ctx);
        renderColorPicker(ctx);
        renderSlotBackdrops(ctx);
        renderPreview(ctx);
        renderStaticLabels(ctx);
    }

    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        // Labels are drawn in absolute coordinates from renderBg to keep them aligned with custom controls.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (handleTabClick(mouseX, mouseY)) {
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + PICKER_X, topPos + PICKER_Y, PICKER_W, PICKER_H)) {
            draggingColor = true;
            updateColorFromPicker(mouseX, mouseY);
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + HUE_X, topPos + PICKER_Y, HUE_W, PICKER_H)) {
            draggingHue = true;
            updateHueFromPicker(mouseY);
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingColor) {
            updateColorFromPicker(click.x(), click.y());
            return true;
        }
        if (draggingHue) {
            updateHueFromPicker(click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingColor = false;
        draggingHue = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        EditBox focused = focusedColorField();
        if (focused != null) {
            if (focused.charTyped(input)) {
                return true;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        EditBox focused = focusedColorField();
        if (focused != null) {
            if (focused.keyPressed(key)) {
                return true;
            }
            return true;
        }
        return super.keyPressed(key);
    }

    private void createColorFields() {
        hexField = addRenderableWidget(new EditBox(font, leftPos + HEX_X, topPos + HEX_Y, HEX_W, 16, Component.literal("Hex")));
        hexField.setMaxLength(7);
        hexField.setResponder(this::onHexFieldChanged);

        redField = addRenderableWidget(channelField(leftPos + 16, topPos + FIELD_Y, "Red"));
        greenField = addRenderableWidget(channelField(leftPos + 58, topPos + FIELD_Y, "Green"));
        blueField = addRenderableWidget(channelField(leftPos + 100, topPos + FIELD_Y, "Blue"));

        redField.setResponder(value -> onRgbFieldChanged(redField));
        greenField.setResponder(value -> onRgbFieldChanged(greenField));
        blueField.setResponder(value -> onRgbFieldChanged(blueField));
    }

    private EditBox channelField(int x, int y, String label) {
        EditBox field = new EditBox(font, x, y, FIELD_W, 16, Component.literal(label));
        field.setMaxLength(3);
        return field;
    }

    private EditBox focusedColorField() {
        if (hexField != null && hexField.isFocused()) {
            return hexField;
        }
        if (redField != null && redField.isFocused()) {
            return redField;
        }
        if (greenField != null && greenField.isFocused()) {
            return greenField;
        }
        if (blueField != null && blueField.isFocused()) {
            return blueField;
        }
        return null;
    }

    private void onHexFieldChanged(String value) {
        if (updatingColorFields) {
            return;
        }

        String normalized = value == null ? "" : value.trim();
        if (!isValidHexInput(normalized)) {
            restoreFieldValue(hexField, lastHexFieldValue);
            return;
        }
        lastHexFieldValue = normalized;

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return;
        }

        try {
            applyColorFromField(Integer.parseInt(normalized, 16), hexField);
        } catch (NumberFormatException ignored) {
            // Filtered by the text box; this is just a guard for pasted input edge cases.
        }
    }

    private void onRgbFieldChanged(EditBox source) {
        if (updatingColorFields || redField == null || greenField == null || blueField == null) {
            return;
        }
        if (!isValidChannelInput(source.getValue())) {
            restoreFieldValue(source, lastChannelFieldValue(source));
            return;
        }
        rememberChannelFieldValue(source);

        if (redField.getValue().isBlank() || greenField.getValue().isBlank() || blueField.getValue().isBlank()) {
            return;
        }

        int current = activeColor();
        int r = parseChannel(redField.getValue(), red(current));
        int g = parseChannel(greenField.getValue(), green(current));
        int b = parseChannel(blueField.getValue(), blue(current));
        applyColorFromField((r << 16) | (g << 8) | b, source);
    }

    private void applyColorFromField(int color, EditBox source) {
        setActiveColorRaw(color);
        syncHsvFromActiveColor();
        syncColorFieldsFromActiveColor(source);
    }

    private void syncColorFieldsFromActiveColor() {
        syncColorFieldsFromActiveColor(null);
    }

    private void syncColorFieldsFromActiveColor(EditBox skipField) {
        if (hexField == null || redField == null || greenField == null || blueField == null) {
            return;
        }

        int color = activeColor();
        updatingColorFields = true;
        if (hexField != skipField) {
            lastHexFieldValue = String.format("%06X", color);
            hexField.setValue(lastHexFieldValue);
        }
        if (redField != skipField) {
            lastRedFieldValue = String.valueOf(red(color));
            redField.setValue(lastRedFieldValue);
        }
        if (greenField != skipField) {
            lastGreenFieldValue = String.valueOf(green(color));
            greenField.setValue(lastGreenFieldValue);
        }
        if (blueField != skipField) {
            lastBlueFieldValue = String.valueOf(blue(color));
            blueField.setValue(lastBlueFieldValue);
        }
        updatingColorFields = false;
    }

    private void restoreFieldValue(EditBox field, String value) {
        updatingColorFields = true;
        field.setValue(value == null ? "" : value);
        updatingColorFields = false;
    }

    private String lastChannelFieldValue(EditBox field) {
        if (field == redField) return lastRedFieldValue;
        if (field == greenField) return lastGreenFieldValue;
        if (field == blueField) return lastBlueFieldValue;
        return "";
    }

    private void rememberChannelFieldValue(EditBox field) {
        if (field == redField) {
            lastRedFieldValue = field.getValue();
        } else if (field == greenField) {
            lastGreenFieldValue = field.getValue();
        } else if (field == blueField) {
            lastBlueFieldValue = field.getValue();
        }
    }

    private void renderTabs(GuiGraphics ctx) {
        for (ColorTab tab : ColorTab.values()) {
            int index = tab.ordinal();
            int x = leftPos + TAB_X + index * (TAB_W + 2);
            int y = topPos + TAB_Y;
            boolean selected = tab == selectedTab;
            int bg = selected ? UiTheme.BTN_DOWN : UiTheme.BTN;
            ctx.fill(x, y, x + TAB_W, y + TAB_H, bg);
            drawBorder(ctx, x, y, TAB_W, TAB_H, selected ? UiTheme.ACCENT : UiTheme.BTN_BORDER);
            int color = selected ? 0xFFFFFFFF : UiTheme.MUTED;
            ctx.drawString(font, tab.label, x + (TAB_W - font.width(tab.label)) / 2, y + 5, color);
        }
    }

    private void renderColorPicker(GuiGraphics ctx) {
        int x = leftPos + PICKER_X;
        int y = topPos + PICKER_Y;

        for (int py = 0; py < PICKER_H; py += 2) {
            for (int px = 0; px < PICKER_W; px += 2) {
                float s = px / (float) (PICKER_W - 1);
                float v = 1.0F - py / (float) (PICKER_H - 1);
                int color = 0xFF000000 | hsvToRgb(hue, s, v);
                ctx.fill(x + px, y + py, x + Math.min(PICKER_W, px + 2), y + Math.min(PICKER_H, py + 2), color);
            }
        }
        drawBorder(ctx, x, y, PICKER_W, PICKER_H, UiTheme.PANEL_BORDER);

        int markerX = x + Math.round(saturation * (PICKER_W - 1));
        int markerY = y + Math.round((1.0F - value) * (PICKER_H - 1));
        ctx.fill(markerX - 3, markerY, markerX + 4, markerY + 1, 0xFFFFFFFF);
        ctx.fill(markerX, markerY - 3, markerX + 1, markerY + 4, 0xFFFFFFFF);

        int hueX = leftPos + HUE_X;
        for (int py = 0; py < PICKER_H; py++) {
            float h = py / (float) (PICKER_H - 1);
            ctx.fill(hueX, y + py, hueX + HUE_W, y + py + 1, 0xFF000000 | hsvToRgb(h, 1.0F, 1.0F));
        }
        drawBorder(ctx, hueX, y, HUE_W, PICKER_H, UiTheme.PANEL_BORDER);
        int hueMarkerY = y + Math.round(hue * (PICKER_H - 1));
        ctx.fill(hueX - 2, hueMarkerY, hueX + HUE_W + 2, hueMarkerY + 1, 0xFFFFFFFF);

        int active = activeColor();
        ctx.fill(leftPos + 222, topPos + HEX_Y, leftPos + 238, topPos + HEX_Y + 16, 0xFF000000 | active);
        drawBorder(ctx, leftPos + 222, topPos + HEX_Y, 16, 16, UiTheme.PANEL_BORDER);
    }

    private void renderSlotBackdrops(GuiGraphics ctx) {
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            ctx.fill(x, y, x + 18, y + 18, 0xFF1A1A1A);
            drawBorder(ctx, x, y, 18, 18, UiTheme.BTN_BORDER);
        }
    }

    private void renderPreview(GuiGraphics ctx) {
        int x = leftPos + PREVIEW_X;
        int y = topPos + PREVIEW_Y;
        ctx.fill(x - 6, y - 6, x + PREVIEW_SIZE + 6, y + PREVIEW_SIZE + 6, 0x66000000);
        drawBorder(ctx, x - 6, y - 6, PREVIEW_SIZE + 12, PREVIEW_SIZE + 12, UiTheme.PANEL_BORDER);

        DiceAppearance appearance = buildPreviewAppearance();
        DiceTextureCache.TextureRef ref = DiceTextureCache.getTexture(currentDiceType().sides(), appearance);
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                x,
                y,
                0.0F,
                0.0F,
                PREVIEW_SIZE,
                PREVIEW_SIZE,
                ref.width(),
                ref.height(),
                ref.width(),
                ref.height()
        );

        if (appearance.foil()) {
            renderGlintPreview(ctx, ref, x, y);
        }

    }

    private void renderGlintPreview(GuiGraphics ctx, DiceTextureCache.TextureRef ref, int x, int y) {
        long now = System.currentTimeMillis();
        float phase = (now % 1800L) / 1800.0F;

        for (int row = 0; row < GLINT_ROWS; row++) {
            int drawY = y + row * PREVIEW_SIZE / GLINT_ROWS;
            int nextDrawY = y + (row + 1) * PREVIEW_SIZE / GLINT_ROWS;
            int drawH = Math.max(1, nextDrawY - drawY);
            int texY = row * ref.height() / GLINT_ROWS;
            int nextTexY = (row + 1) * ref.height() / GLINT_ROWS;
            int texH = Math.max(1, nextTexY - texY);
            float rowT = (row + 0.5F) / GLINT_ROWS;

            for (int band = 0; band < GLINT_BANDS; band++) {
                float bandPhase = (phase + band * 0.5F + rowT * 0.42F) % 1.0F;
                int bandCenter = Math.round((bandPhase * 1.55F - 0.28F) * PREVIEW_SIZE);
                int bandW = Math.max(12, PREVIEW_SIZE / 5);
                int drawX = x + bandCenter - bandW / 2;
                int drawStart = Math.max(x, drawX);
                int drawEnd = Math.min(x + PREVIEW_SIZE, drawX + bandW);
                if (drawEnd <= drawStart) {
                    continue;
                }

                float u0 = (drawStart - x) / (float) PREVIEW_SIZE;
                int texX = Math.round(u0 * ref.width());
                int texW = Math.max(1, Math.round((drawEnd - drawStart) * ref.width() / (float) PREVIEW_SIZE));
                int color = band == 0 ? 0x72B64CFF : 0x56FFFFFF;
                ctx.blit(
                        RenderPipelines.GUI_TEXTURED,
                        ref.id(),
                        drawStart,
                        drawY,
                        texX,
                        texY,
                        drawEnd - drawStart,
                        drawH,
                        texW,
                        texH,
                        ref.width(),
                        ref.height(),
                        color
                );
            }
        }
    }

    private void renderStaticLabels(GuiGraphics ctx) {
        ctx.drawString(font, title, leftPos + 10, topPos + 8, UiTheme.TEXT);
        ctx.drawString(font, Component.literal("COLOR"), leftPos + PICKER_X, topPos + 48, UiTheme.MUTED);
        ctx.drawString(font, Component.literal("HEX"), leftPos + HEX_X, topPos + 56, UiTheme.MUTED);
        ctx.drawString(font, Component.literal("PREVIEW"), leftPos + PREVIEW_X, topPos + 44, UiTheme.MUTED);

        drawCentered(ctx, "R", leftPos + 33, topPos + 108, 0xFFFF6B6B);
        drawCentered(ctx, "G", leftPos + 75, topPos + 108, 0xFF6BFF8A);
        drawCentered(ctx, "B", leftPos + 117, topPos + 108, 0xFF72A0FF);

        ctx.drawString(font, Component.literal("GRADIENT"), leftPos + 152, topPos + 100, UiTheme.MUTED);
        ctx.drawString(font, Component.literal("MATERIAL"), leftPos + 146, topPos + 134, UiTheme.MUTED);
        drawCentered(ctx, "Echo", leftPos + 167, topPos + 164, UiTheme.MUTED);

        drawCentered(ctx, "Foil", leftPos + 245, topPos + 164, UiTheme.MUTED);
        drawCentered(ctx, "Pat", leftPos + 271, topPos + 164, UiTheme.MUTED);

        ctx.drawString(font, playerInventoryTitle, leftPos + DiceCustomizerScreenHandler.PLAYER_INV_X, topPos + inventoryLabelY, UiTheme.MUTED);
    }

    private void drawCentered(GuiGraphics ctx, String text, int centerX, int y, int color) {
        ctx.drawString(font, Component.literal(text), centerX - font.width(text) / 2, y, color);
    }

    private DiceAppearance buildPreviewAppearance() {
        ItemStack patternStack = menu.getIngredientStack(DiceCustomizerScreenHandler.PATTERN_SLOT);
        Optional<Identifier> pattern = Optional.empty();
        if (minecraft != null && minecraft.player != null) {
            pattern = DicePatternResolver.resolveBannerPatternAsset(minecraft.player.registryAccess(), patternStack);
        }

        boolean foil = DiceCustomizerIngredients.isFoil(menu.getIngredientStack(DiceCustomizerScreenHandler.FOIL_SLOT));

        return new DiceAppearance(
                primaryColor,
                secondaryColor,
                borderColor,
                textColor,
                bannerColor,
                gradientType,
                false,
                foil,
                0L,
                pattern
        );
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        for (ColorTab tab : ColorTab.values()) {
            int x = leftPos + TAB_X + tab.ordinal() * (TAB_W + 2);
            int y = topPos + TAB_Y;
            if (inside(mouseX, mouseY, x, y, TAB_W, TAB_H)) {
                selectedTab = tab;
                syncHsvFromActiveColor();
                syncColorFieldsFromActiveColor();
                return true;
            }
        }
        return false;
    }

    private void updateColorFromPicker(double mouseX, double mouseY) {
        saturation = clamp01((float) ((mouseX - (leftPos + PICKER_X)) / (PICKER_W - 1)));
        value = clamp01(1.0F - (float) ((mouseY - (topPos + PICKER_Y)) / (PICKER_H - 1)));
        setActiveColor(hsvToRgb(hue, saturation, value));
    }

    private void updateHueFromPicker(double mouseY) {
        hue = clamp01((float) ((mouseY - (topPos + PICKER_Y)) / (PICKER_H - 1)));
        setActiveColor(hsvToRgb(hue, saturation, value));
    }

    private void syncHsvFromActiveColor() {
        float[] hsv = rgbToHsv(activeColor());
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private int activeColor() {
        return switch (selectedTab) {
            case PRIMARY -> primaryColor;
            case SECONDARY -> secondaryColor;
            case BORDER -> borderColor;
            case TEXT -> textColor;
            case BANNER -> bannerColor;
        };
    }

    private void setActiveColor(int color) {
        setActiveColorRaw(color);
        syncColorFieldsFromActiveColor();
    }

    private void setActiveColorRaw(int color) {
        color &= 0xFFFFFF;
        switch (selectedTab) {
            case PRIMARY -> primaryColor = color;
            case SECONDARY -> secondaryColor = color;
            case BORDER -> borderColor = color;
            case TEXT -> textColor = color;
            case BANNER -> bannerColor = color;
        }
    }

    private void cycleGradient() {
        DiceGradientType[] values = DiceGradientType.values();
        gradientType = values[(gradientType.ordinal() + 1) % values.length];
    }

    private Component gradientLabel() {
        String id = gradientType.id();
        return Component.literal(Character.toUpperCase(id.charAt(0)) + id.substring(1));
    }

    private DiceType currentDiceType() {
        return DiceType.values()[diceTypeIndex];
    }

    private void updateCraftButton() {
        if (craftButton != null) {
            craftButton.active = hasValidRequiredIngredients() && hasValidOptionalIngredients();
        }
    }

    private boolean hasValidRequiredIngredients() {
        return DiceCustomizerIngredients.isMaterial(menu.getIngredientStack(DiceCustomizerScreenHandler.MATERIAL_SLOT));
    }

    private boolean hasValidOptionalIngredients() {
        ItemStack foil = menu.getIngredientStack(DiceCustomizerScreenHandler.FOIL_SLOT);
        ItemStack pattern = menu.getIngredientStack(DiceCustomizerScreenHandler.PATTERN_SLOT);
        return (foil.isEmpty() || DiceCustomizerIngredients.isFoil(foil))
                && (pattern.isEmpty() || DiceCustomizerIngredients.isBannerPattern(pattern));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawBorder(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = clamp01(h);
        s = clamp01(s);
        v = clamp01(v);

        if (s <= 0.0F) {
            int channel = Math.round(v * 255.0F);
            return (channel << 16) | (channel << 8) | channel;
        }

        float sector = h * 6.0F;
        int i = (int) Math.floor(sector);
        float f = sector - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);

        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return (Math.round(r * 255.0F) << 16)
                | (Math.round(g * 255.0F) << 8)
                | Math.round(b * 255.0F);
    }

    private static float[] rgbToHsv(int rgb) {
        float r = red(rgb) / 255.0F;
        float g = green(rgb) / 255.0F;
        float b = blue(rgb) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h;
        if (delta == 0.0F) {
            h = 0.0F;
        } else if (max == r) {
            h = ((g - b) / delta) % 6.0F;
        } else if (max == g) {
            h = ((b - r) / delta) + 2.0F;
        } else {
            h = ((r - g) / delta) + 4.0F;
        }
        h /= 6.0F;
        if (h < 0.0F) h += 1.0F;

        float s = max == 0.0F ? 0.0F : delta / max;
        return new float[]{h, s, max};
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int parseChannel(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Math.max(0, Math.min(255, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean isValidChannelInput(String value) {
        if (value == null || !value.matches("[0-9]{0,3}")) {
            return false;
        }
        if (value.isEmpty()) {
            return true;
        }

        try {
            return Integer.parseInt(value) <= 255;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isValidHexInput(String value) {
        return value != null && value.matches("#?[0-9a-fA-F]{0,6}");
    }

    private static int red(int rgb) {
        return (rgb >>> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >>> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private enum ColorTab {
        PRIMARY("Primary"),
        SECONDARY("Secondary"),
        BORDER("Border"),
        TEXT("Text"),
        BANNER("Banner");

        private final String label;

        ColorTab(String label) {
            this.label = label;
        }
    }
}
