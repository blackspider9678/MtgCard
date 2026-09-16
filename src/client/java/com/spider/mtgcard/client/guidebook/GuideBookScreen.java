package com.spider.mtgcard.client.guidebook;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.compat.LegacyScreen;
import com.spider.mtgcard.client.gui.MtgGuiChrome;
import com.spider.mtgcard.guidebook.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import com.spider.mtgcard.config.GuideConfigSnapshot;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.net.GuideBookPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GuideBookScreen extends LegacyScreen {

    private static final Identifier DEFAULT_ICON = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/gui/guidebook/book.png");

    private EditBox search;
    private GuideCategory selectedCategory = GuideCategory.HOME;
    private GuideChapter selectedChapter;

    private int leftX, topY, fullW, fullH;
    private int sidebarW = 170;
    private int padding = 8;

    // simple scroll for the chapter list
    private int listScroll = 0;

    // NEW: scroll for main chapter content
    private int contentScroll = 0;
    private int contentScrollMax = 0;

    private static boolean restartRequired;
    private final Map<String, EditBox> configFields = new LinkedHashMap<>();
    private final Map<String, Boolean> configToggles = new LinkedHashMap<>();
    private final Map<String, String> configValues = new LinkedHashMap<>();
    private final List<ConfigLabel> configLabels = new ArrayList<>();
    private final List<ConfigRule> configRules = new ArrayList<>();
    private GuideConfigSnapshot configOriginal;
    private boolean canEditServerConfig;
    private boolean cardPeekRight = MtgcardConfig.cardPeekOnRight();
    private boolean configRequested;
    private String openConfigDropdown;
    private int configScroll;
    private int configScrollMax;
    private String configStatus = "Loading server settings…";

    public GuideBookScreen(Identifier startChapterId) {
        super(Component.translatable("guide.mtgcard.title"));
        if (startChapterId != null) {
            this.selectedChapter = GuideChapterRegistry.get(startChapterId);
            if (this.selectedChapter != null) {
                this.selectedCategory = this.selectedChapter.category();
            }
        }
        if (this.selectedChapter == null) {
            // default to first in HOME
            var home = GuideChapterRegistry.byCategory(GuideCategory.HOME);
            this.selectedChapter = home.isEmpty() ? null : home.getFirst();
            if (this.selectedChapter != null) {
                this.selectedCategory = this.selectedChapter.category();
            }
        }
    }

    private static final GuideCategory[] TAB_ORDER = new GuideCategory[] {
            GuideCategory.HOME,
            GuideCategory.BLOCKS,
            GuideCategory.ITEMS,
            GuideCategory.GENERAL
    };

    private int tabIndex(GuideCategory cat) {
        for (int i = 0; i < TAB_ORDER.length; i++) {
            if (TAB_ORDER[i] == cat) return i;
        }
        return 0;
    }

    private int tabY(GuideCategory cat) {
        return tabsTopY() + tabIndex(cat) * tabH();
    }

    private int tabsTopY() {
        return topY + padding + 50;
    }

    private int listTopY() {
        return tabsTopY() + (tabH() * TAB_ORDER.length) + 8;
    }

    private int listX() {
        return leftX + padding;
    }

    private int listW() {
        return sidebarW - padding * 2;
    }

    private int listH() {
        return fullH - listTopY() - padding;
    }

    private int tabX() { return leftX + padding; }
    private int tabW() { return sidebarW - padding * 2; }
    private int tabH() { return 16; } // bigger, easier to click

    @Override
    protected void init() {
        if (applyAutoFitGuiScale(840, 420)) return;

        fullW = this.width;
        fullH = this.height;
        leftX = 0;
        topY = 0;

        setupGuideWidgets();
    }

    private void setupGuideWidgets() {
        clearWidgets();
        configFields.clear();
        configLabels.clear();
        configRules.clear();

        int searchX = leftX + padding;
        int searchY = topY + padding + 10;
        int searchW = sidebarW - padding * 2;

        search = new EditBox(this.font, searchX, searchY, searchW, 18, Component.translatable("guide.mtgcard.search"));
        search.setMaxLength(64);
        search.setResponder(s -> listScroll = 0);
        addWidget(search);
        setInitialFocus(search);

        if (isConfigChapter()) buildConfigControls();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // background
        ctx.fill(0, 0, this.width, this.height, 0xAA000000);

        // panels
        int sidebarX = leftX;
        int sidebarY = topY;
        int sidebarH = fullH;

        int mainX = leftX + sidebarW;
        int mainY = topY;
        int mainW = fullW - sidebarW;
        int mainH = fullH;

        // Sidebar background
        ctx.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, 0xAA101010);
        // Main background
        ctx.fill(mainX, mainY, mainX + mainW, mainY + mainH, 0xAA0A0A0A);

        // Title
        ctx.drawString(font, Component.translatable("guide.mtgcard.title"), sidebarX + padding, sidebarY + padding, 0xFFFFFFFF);

        // Search label + widget
        ctx.drawString(font, Component.translatable("guide.mtgcard.search"), sidebarX + padding, sidebarY + padding + 20, 0xFFB0B0B0);
        search.extractRenderState(ctx.unwrap(), mouseX, mouseY, delta);

        // Category tabs (simple text buttons)
        for (GuideCategory cat : TAB_ORDER) {
            drawCategoryTab(ctx, cat, tabX(), tabY(cat), tabW(), tabH());
        }

        // Chapter list
        drawChapterList(ctx, listX(), listTopY(), listW(), listH(), mouseX, mouseY);

        // Main content
        drawMain(ctx, mainX, mainY, mainW, mainH);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCategoryTab(GuiGraphics ctx, GuideCategory cat, int x, int y, int w, int h) {
        boolean active = (cat == selectedCategory);
        ctx.fill(x, y, x + w, y + h, active ? 0x55333333 : 0x22000000);

        Component label = Component.literal(cat.displayName);
        ctx.drawString(font, label, x + 4, y + 4, active ? 0xFFFFFFFF : 0xFFB0B0B0);
    }

    private void drawChapterList(GuiGraphics ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
        // clip region
        ctx.enableScissor(x, y, x + w, y + h);

        List<GuideChapter> chapters = filteredChapters();

        int rowH = 20;
        int yOff = y - listScroll;

        for (int i = 0; i < chapters.size(); i++) {
            GuideChapter ch = chapters.get(i);

            int rowY = yOff + i * rowH;
            if (rowY + rowH < y || rowY > y + h) continue;

            boolean selected = (selectedChapter != null && selectedChapter.id().equals(ch.id()));
            int bg = selected ? 0x55333333 : 0x22000000;

            ctx.fill(x, rowY, x + w, rowY + rowH, bg);

            // icon (16x16)
            int iconX = x + 2;
            int iconY = rowY + 2;

            ItemStack itemIcon = ch.itemIcon();
            if (itemIcon != null && !itemIcon.isEmpty()) {
                ctx.renderItem(itemIcon, iconX, iconY);
            } else {
                Identifier tex = ch.textureIcon();
                if (tex == null) tex = DEFAULT_ICON;
                ctx.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0f, 0f, 16, 16, 16, 16);

            }

            Component title = Component.translatable(ch.titleKey());
            ctx.drawString(font, title, x + 22, rowY + 6, 0xFFFFFFFF);
        }

        ctx.disableScissor();
    }

    private List<GuideChapter> filteredChapters() {
        List<GuideChapter> base = GuideChapterRegistry.byCategory(selectedCategory);
        String q = (search.getValue() == null ? "" : search.getValue()).trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) return base;

        List<GuideChapter> out = new ArrayList<>();
        for (GuideChapter c : base) {
            String title = Component.translatable(c.titleKey()).getString().toLowerCase(Locale.ROOT);
            if (title.contains(q)) out.add(c);
        }
        return out;
    }

    private void drawMain(GuiGraphics ctx, int x, int y, int w, int h) {
        if (selectedChapter == null) {
            ctx.drawString(font, Component.translatable("guide.mtgcard.empty"), x + padding, y + padding, 0xFFFFFFFF);
            return;
        }

        // Header
        int headerY = y + padding;
        int iconX = x + padding;
        int iconY = headerY;

        ItemStack itemIcon = selectedChapter.itemIcon();
        if (itemIcon != null && !itemIcon.isEmpty()) {
            ctx.renderItem(itemIcon, iconX, iconY);
        } else {
            Identifier tex = selectedChapter.textureIcon();
            if (tex == null) tex = DEFAULT_ICON;
            ctx.blit(RenderPipelines.GUI_TEXTURED, tex, iconX, iconY, 0f, 0f, 32, 32, 32, 32);
        }

        Component title = Component.translatable(selectedChapter.titleKey());
        ctx.drawString(font, title, iconX + 40, headerY + 10, 0xFFFFFFFF);

        if (isConfigChapter()) {
            drawConfigContent(ctx, x, y, w, h);
            return;
        }

        int contentX = x + padding;
        int contentY = headerY + 40;
        int contentW = w - padding * 2;
        int contentBottom = y + h - padding;

        int contentH = contentBottom - contentY;

        // Compute total content height (for scroll max)
        int totalH = measureSectionsHeight(selectedChapter.sections(), contentW);
        contentScrollMax = Math.max(0, totalH - contentH);
        contentScroll = clamp(contentScroll, 0, contentScrollMax);

        // Clip to content region
        ctx.enableScissor(contentX, contentY, contentX + contentW, contentBottom);
        int cy = contentY - contentScroll;


        var sections = selectedChapter.sections();
        if (sections == null || sections.isEmpty()) {
            ctx.drawString(font, Component.translatable("guide.mtgcard.chapter_blank"), contentX, contentY, 0xFFB0B0B0);
            ctx.disableScissor();
            return;
        }

        for (var section : sections) {
            // If it's far above, we still need to advance cy correctly (handled by drawWrapped returning cy)
            if (section instanceof GuideSection.Heading hd) {
                cy = drawWrapped(ctx, hd.text(), contentX, cy, contentW, 0xFFFFFFFF) + 6;
            }
            else if (section instanceof GuideSection.Paragraph p) {
                cy = drawWrapped(ctx, p.text(), contentX, cy, contentW, 0xFFDDDDDD) + 10;
            }
            else if (section instanceof GuideSection.Bullets b) {
                for (Component bullet : b.bullets()) {
                    cy = drawWrapped(ctx, Component.literal("• ").append(bullet), contentX, cy, contentW, 0xFFDDDDDD) + 4;
                }
                cy += 6;
            }
        }

        ctx.disableScissor();

        // Optional: draw a scrollbar if needed
        if (contentScrollMax > 0) {
            var scrollbar = MtgGuiChrome.layoutScrollbar(
                    new MtgGuiChrome.Rect(contentX + contentW - 4, contentY, 4, contentH),
                    contentH + contentScrollMax,
                    contentH,
                    contentScroll,
                    12
            );
            MtgGuiChrome.drawScrollbar(ctx, scrollbar, 0x33000000, 0x88FFFFFF, 0x33000000);
        }
    }

    private int measureSectionsHeight(List<GuideSection> sections, int width) {
        if (sections == null || sections.isEmpty()) return 0;

        int h = 0;
        for (var section : sections) {
            if (section instanceof GuideSection.Heading hd) {
                h += measureWrappedHeight(hd.text(), width) + 6;
            } else if (section instanceof GuideSection.Paragraph p) {
                h += measureWrappedHeight(p.text(), width) + 10;
            } else if (section instanceof GuideSection.Bullets b) {
                for (Component bullet : b.bullets()) {
                    h += measureWrappedHeight(Component.literal("• ").append(bullet), width) + 4;
                }
                h += 6;
            }
        }
        return h;
    }

    private int measureWrappedHeight(Component text, int width) {
        var lines = font.split(text, width);
        return lines.size() * 10; // matches your drawWrapped line height
    }

    private int drawWrapped(GuiGraphics ctx, Component text, int x, int y, int width, int color) {
        // Wrap into multiple OrderedText lines
        var lines = font.split(text, width);
        int cy = y;
        for (var line : lines) {
            ctx.drawString(font, line, x, cy, color);
            cy += 10; // line height
        }
        return cy;
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        int code = key.input();
        EditBox whitelist = configFields.get("whitelist_add");
        if ((code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER)
                && whitelist != null && whitelist.isFocused()) {
            addWhitelistedPlayer();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean down) {

        // Only process press
        if (down) return false;

        // Let children (TextFieldWidget) have first dibs
        if (super.mouseClicked(click, true)) return true;

        double mouseX = click.x();
        double mouseY = click.y();

        int tx = tabX();
        int tw = tabW();
        int th = tabH();

        for (GuideCategory cat : TAB_ORDER) {
            if (hit(tx, tabY(cat), tw, th, mouseX, mouseY)) {
                setCategory(cat);
                return true;
            }
        }

        // Chapter list clicks
        int lx = listX();
        int ly = listTopY();
        int lw = listW();
        int lh = listH();

        List<GuideChapter> chapters = filteredChapters();
        int rowH = 20;
        int idx = (int) ((mouseY - (ly - listScroll)) / rowH);

        if (hit(lx, ly, lw, lh, mouseX, mouseY) && idx >= 0 && idx < chapters.size()) {
            selectedChapter = chapters.get(idx);
            contentScroll = 0;          // NEW
            contentScrollMax = 0;       // NEW (recomputed next render)
            setupGuideWidgets();
            return true;
        }

        return false;
    }

    private void setCategory(GuideCategory cat) {
        selectedCategory = cat;
        listScroll = 0;
        contentScroll = 0;          // NEW
        contentScrollMax = 0;       // NEW
        var list = GuideChapterRegistry.byCategory(selectedCategory);
        selectedChapter = list.isEmpty() ? null : list.getFirst();
        setupGuideWidgets();
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int lx = listX();
        int ly = listTopY();
        int lw = listW();
        int lh = listH();

        if (hit(lx, ly, lw, lh, mouseX, mouseY)) {
            int delta = (int)(-verticalAmount * 12);
            listScroll = Math.max(0, listScroll + delta);
            return true;
        }

        if (isConfigChapter() && mouseX >= leftX + sidebarW) {
            int delta = (int) (-verticalAmount * 24);
            int next = clamp(configScroll + delta, 0, configScrollMax);
            if (next != configScroll) {
                configScroll = next;
                setupGuideWidgets();
            }
            return true;
        }

        // NEW: scroll main content (only if overflow exists)
        int mainX = leftX + sidebarW;
        int mainY = topY;
        int mainW = fullW - sidebarW;
        int mainH = fullH;

        int contentX = mainX + padding;
        int contentY = mainY + padding + 40; // matches drawMain
        int contentW = mainW - padding * 2;
        int contentBottom = mainY + mainH - padding;
        int contentH = contentBottom - contentY;

        if (hit(contentX, contentY, contentW, contentH, mouseX, mouseY) && contentScrollMax > 0) {
            int delta = (int)(-verticalAmount * 14);
            contentScroll = clamp(contentScroll + delta, 0, contentScrollMax);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static boolean hit(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean isConfigChapter() {
        return selectedChapter instanceof com.spider.mtgcard.guidebook.chapters.general.ConfigChapter;
    }

    private void buildConfigControls() {
        int mainX = leftX + sidebarW;
        int mainW = fullW - sidebarW;
        int left = mainX + padding;
        int contentW = mainW - padding * 2;

        addRenderableWidget(Button.builder(Component.translatable("guide.mtgcard.config.save"), button -> saveConfig())
                .bounds(left + mainW - padding * 2 - 70, topY + fullH - 24, 70, 20).build());

        int y = configViewportTop() - configScroll;
        y = addConfigHeader("Client settings", y);
        y = addClientPosition(left, y, contentW);
        y += 8;

        if (configOriginal == null) {
            if (!configRequested) {
                configRequested = true;
                ClientPlayNetworking.send(new GuideBookPackets.ConfigRequestPayload());
            }
            return;
        }

        y = addConfigHeader("Server settings", y);
        y = addConfigSubheader("Importing", y);
        y = addConfigToggle("anyone", "Anyone can import", "Allows every player to import custom cards.", left, y, contentW);
        y = addWhitelistEditor(left, y, contentW);
        y = addConfigSubheader("Card downloads", y);
        y = addConfigDropdown("language", "Card language", "Preferred language used when downloading card data from Scryfall.",
                List.of("en", "es", "fr", "de", "it", "pt", "ja", "ko", "ru", "zhs", "zht"), left, y, contentW);
        y = addConfigSubheader("Pack behavior", y);
        y = addConfigToggle("fishing", "Fishing awards packs", "Allows fishing loot to include card packs.", left, y, contentW);
        y = addConfigToggle("pack_debug", "Pack debug logging", "Writes detailed pack-generation information to the log.", left, y, contentW);
        y = addConfigSubheader("Custom pack chances", y);
        y = addConfigText("common", "Common chance", "Chance that a common slot uses a custom card, from 0.0 to 1.0.", left, y, contentW);
        y = addConfigText("uncommon", "Uncommon chance", "Chance that an uncommon slot uses a custom card, from 0.0 to 1.0.", left, y, contentW);
        y = addConfigText("wildcard", "Wildcard chance", "Chance that a common/uncommon wildcard slot uses a custom card.", left, y, contentW);
        y = addConfigText("rare", "Rare or mythic chance", "Chance that a rare or mythic slot uses a custom card.", left, y, contentW);
        y = addConfigText("random", "Random-card chance", "Chance that an unrestricted slot uses a custom card.", left, y, contentW);
        y = addConfigText("foil", "Foil chance", "Chance that a foil slot uses a custom foil card.", left, y, contentW);
        y = addConfigText("land", "Basic-land chance", "Chance that a basic-land slot uses a custom card.", left, y, contentW);
        y = addConfigText("token", "Token or art chance", "Chance that a token or art-card slot uses custom content.", left, y, contentW);
        y = addConfigSubheader("Dice", y);
        y = addConfigToggle("physical_dice", "Physical dice", "Throws dice into the world on normal use instead of rolling instantly. Sneak-use still places dice.", left, y, contentW);
        y = addConfigSubheader("Card Store", y);
        y = addConfigToggle("card_store", "Card Store enabled", "Controls whether Card Store content is loaded.", left, y, contentW);
        y = addConfigToggle("mtg_game", "MTG game enabled", "Controls whether Magic-specific content is loaded. Addons installed: " + installedAddons(), left, y, contentW);
        y = addConfigText("price_item", "Price item", "Minecraft item used as currency by the Card Store.", left, y, contentW);
        y = addConfigDropdown("price_basis", "Price basis", "Market price used for card costs: USD, EUR, or TIX.",
                List.of("USD", "EUR", "TIX"), left, y, contentW);
        configScrollMax = Math.max(0, y + configScroll - configViewportBottom());
    }

    private int addConfigHeader(String text, int y) {
        configLabels.add(new ConfigLabel(leftX + sidebarW + padding, y, Component.literal(text).getVisualOrderText(), 0xFFFFD37F));
        addConfigRule(text, y, 0xFFFFD37F);
        return y + 22;
    }

    private int addConfigSubheader(String text, int y) {
        configLabels.add(new ConfigLabel(leftX + sidebarW + padding, y, Component.literal(text).getVisualOrderText(), 0xFFFFFFFF));
        addConfigRule(text, y, 0xFF777777);
        return y + 18;
    }

    private void addConfigRule(String text, int y, int color) {
        int start = leftX + sidebarW + padding + font.width(text) + 6;
        int end = Math.min(leftX + fullW - padding - 8, start + 180);
        configRules.add(new ConfigRule(start, y + 5, end, color));
    }

    private int addClientPosition(int x, int y, int width) {
        int widgetX = configWidgetX(x, width, 110);
        int rowHeight = addSettingLabels("Card peek position", "Moves the held-card preview to the left or right side of the screen.", x, y, true, widgetX - x - 10);
        if (configWidgetVisible(y)) {
            addRenderableWidget(Button.builder(configPositionText(), button -> {
                cardPeekRight = !cardPeekRight;
                button.setMessage(configPositionText());
            }).bounds(widgetX, y, 110, 20).build());
        }
        return y + rowHeight;
    }

    private int addConfigText(String key, String label, String description, int x, int y, int width) {
        int fieldWidth = key.equals("whitelist") || key.equals("price_item") ? 220 : 90;
        int fieldX = configWidgetX(x, width, fieldWidth);
        int rowHeight = addSettingLabels(label, description, x, y, canEditServerConfig, fieldX - x - 10);
        if (!configWidgetVisible(y)) return y + rowHeight;
        EditBox field = new EditBox(font, fieldX, y, fieldWidth, 20, Component.literal(label));
        field.setMaxLength(256);
        field.setValue(configValues.getOrDefault(key, ""));
        field.setResponder(value -> configValues.put(key, value));
        field.setEditable(canEditServerConfig);
        configFields.put(key, field);
        addRenderableWidget(field);
        return y + rowHeight;
    }

    private int addConfigToggle(String key, String label, String description, int x, int y, int width) {
        int buttonX = configWidgetX(x, width, 90);
        int rowHeight = addSettingLabels(label, description, x, y, canEditServerConfig, buttonX - x - 10);
        if (!configWidgetVisible(y)) return y + rowHeight;
        Button button = Button.builder(toggleText(configToggles.getOrDefault(key, false)), pressed -> {
            boolean next = !Boolean.TRUE.equals(configToggles.get(key));
            configToggles.put(key, next);
            pressed.setMessage(toggleText(next));
        }).bounds(buttonX, y, 90, 20).build();
        button.active = canEditServerConfig;
        addRenderableWidget(button);
        return y + rowHeight;
    }

    private int addConfigDropdown(String key, String label, String description, List<String> choices,
                                  int x, int y, int width) {
        int selectorWidth = "language".equals(key) ? 190 : 120;
        int buttonX = configWidgetX(x, width, selectorWidth);
        int rowHeight = addSettingLabels(label, description, x, y, canEditServerConfig, buttonX - x - 10);
        boolean open = key.equals(openConfigDropdown);
        if (configWidgetVisible(y)) {
            String current = configValues.getOrDefault(key, choices.getFirst());
            Button selector = Button.builder(Component.literal(configChoiceLabel(key, current) + (open ? " ▲" : " ▼")), pressed -> {
                openConfigDropdown = open ? null : key;
                if (!open) {
                    int overflow = y + rowHeight + choices.size() * 20 + 4 - configViewportBottom();
                    if (overflow > 0) configScroll += overflow;
                } else {
                    configScroll = Math.max(0, configScroll - choices.size() * 20 - 4);
                }
                setupGuideWidgets();
            }).bounds(buttonX, y, selectorWidth, 20).build();
            selector.active = canEditServerConfig;
            addRenderableWidget(selector);
        }
        int nextY = y + rowHeight;
        if (open) {
            for (String choice : choices) {
                int optionY = nextY;
                if (configWidgetVisible(optionY)) {
                    Button option = Button.builder(Component.literal(configChoiceLabel(key, choice)), pressed -> {
                        configValues.put(key, choice);
                        openConfigDropdown = null;
                        configScroll = Math.max(0, configScroll - choices.size() * 20 - 4);
                        setupGuideWidgets();
                    }).bounds(buttonX, optionY, selectorWidth, 20).build();
                    option.active = canEditServerConfig;
                    addRenderableWidget(option);
                }
                nextY += 20;
            }
            nextY += 4;
        }
        return nextY;
    }

    private int addWhitelistEditor(int x, int y, int width) {
        int fieldX = configWidgetX(x, width, 220);
        int rowHeight = addSettingLabels("Import whitelist", "Type a player name and press Enter to add it.",
                x, y, canEditServerConfig, fieldX - x - 10);
        if (configWidgetVisible(y)) {
            EditBox field = new EditBox(font, fieldX, y, 220, 20, Component.literal("Add player"));
            field.setMaxLength(16);
            field.setValue(configValues.getOrDefault("whitelist_add", ""));
            field.setResponder(value -> configValues.put("whitelist_add", value));
            field.setEditable(canEditServerConfig);
            configFields.put("whitelist_add", field);
            addRenderableWidget(field);
        }

        int listY = y + rowHeight;
        configLabels.add(new ConfigLabel(x, listY, Component.literal("Currently whitelisted:").getVisualOrderText(), 0xFFFFFFFF));
        listY += 16;
        if (configWidgetVisible(listY)) {
            Button ops = Button.builder(Component.literal((configToggles.getOrDefault("ops", true) ? "[✓] " : "[ ] ") + "OPs"), pressed -> {
                configToggles.put("ops", !configToggles.getOrDefault("ops", true));
                setupGuideWidgets();
            }).bounds(x, listY, 90, 20).build();
            ops.active = canEditServerConfig;
            addRenderableWidget(ops);
        }
        listY += 24;
        for (String player : parseList(configValues.getOrDefault("whitelist", ""))) {
            int entryY = listY;
            configLabels.add(new ConfigLabel(x + 26, entryY + 6, Component.literal(player).getVisualOrderText(), 0xFFE0E0E0));
            if (configWidgetVisible(entryY)) {
                Button remove = Button.builder(Component.literal("X"), pressed -> removeWhitelistedPlayer(player))
                        .bounds(x, entryY, 20, 20).build();
                remove.active = canEditServerConfig;
                addRenderableWidget(remove);
            }
            listY += 24;
        }
        return listY + 6;
    }

    private void addWhitelistedPlayer() {
        String name = configValues.getOrDefault("whitelist_add", "").trim();
        if (name.isEmpty() || !canEditServerConfig) return;
        List<String> names = new ArrayList<>(parseList(configValues.getOrDefault("whitelist", "")));
        if (names.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) names.add(name);
        configValues.put("whitelist", String.join(", ", names));
        configValues.put("whitelist_add", "");
        setupGuideWidgets();
    }

    private void removeWhitelistedPlayer(String player) {
        List<String> names = new ArrayList<>(parseList(configValues.getOrDefault("whitelist", "")));
        names.removeIf(existing -> existing.equalsIgnoreCase(player));
        configValues.put("whitelist", String.join(", ", names));
        setupGuideWidgets();
    }

    private static String installedAddons() {
        List<String> installed = new ArrayList<>();
        if (FabricLoader.getInstance().isModLoaded("pokemon_tcg_addon")) installed.add("Pokémon");
        if (FabricLoader.getInstance().isModLoaded("riftbound_tcg")) installed.add("Riftbound");
        if (FabricLoader.getInstance().isModLoaded("lorcana_addon")) installed.add("Lorcana");
        return installed.isEmpty() ? "None" : String.join(", ", installed);
    }

    private static String configChoiceLabel(String key, String value) {
        if (!"language".equals(key)) return value;
        return switch (value) {
            case "en" -> "English (en)";
            case "es" -> "Spanish (es)";
            case "fr" -> "French (fr)";
            case "de" -> "German (de)";
            case "it" -> "Italian (it)";
            case "pt" -> "Portuguese (pt)";
            case "ja" -> "Japanese (ja)";
            case "ko" -> "Korean (ko)";
            case "ru" -> "Russian (ru)";
            case "zhs" -> "Chinese Simplified (zhs)";
            case "zht" -> "Chinese Traditional (zht)";
            default -> value;
        };
    }

    private int configWidgetX(int x, int availableWidth, int widgetWidth) {
        return x + Math.min(300, Math.max(190, availableWidth - widgetWidth));
    }

    private int addSettingLabels(String label, String description, int x, int y, boolean editable, int descriptionWidth) {
        int color = editable ? 0xFFE0E0E0 : 0xFF888888;
        configLabels.add(new ConfigLabel(x, y + 2, Component.literal(label).getVisualOrderText(), color));
        var lines = font.split(Component.literal(description), Math.max(120, descriptionWidth));
        int lineY = y + 17;
        for (var line : lines) {
            configLabels.add(new ConfigLabel(x, lineY, line, 0xFF999999));
            lineY += 10;
        }
        return Math.max(38, 21 + lines.size() * 10);
    }

    private boolean configWidgetVisible(int y) {
        return y >= configViewportTop() && y + 20 <= configViewportBottom();
    }

    private int configViewportTop() { return topY + 48; }
    private int configViewportBottom() { return topY + fullH - 32; }

    private void loadConfigValues() {
        configValues.clear();
        configToggles.clear();
        configValues.put("whitelist", String.join(", ", configOriginal.importWhitelist()));
        configValues.put("language", configOriginal.cardLanguage());
        configValues.put("common", number(configOriginal.customCommon()));
        configValues.put("uncommon", number(configOriginal.customUncommon()));
        configValues.put("wildcard", number(configOriginal.customWildcard()));
        configValues.put("rare", number(configOriginal.customRare()));
        configValues.put("random", number(configOriginal.customRandom()));
        configValues.put("foil", number(configOriginal.customRandomFoil()));
        configValues.put("land", number(configOriginal.customBasicLand()));
        configValues.put("token", number(configOriginal.customTokenOrArt()));
        configValues.put("price_item", configOriginal.priceItem());
        configValues.put("price_basis", configOriginal.priceBasis());
        configToggles.put("anyone", configOriginal.anyoneCanImport());
        configToggles.put("ops", configOriginal.opsCanImport());
        configToggles.put("fishing", configOriginal.fishingPacks());
        configToggles.put("pack_debug", configOriginal.packDebug());
        configToggles.put("physical_dice", configOriginal.physicalDiceEnabled());
        configToggles.put("card_store", configOriginal.cardStoreEnabled());
        configToggles.put("mtg_game", configOriginal.mtgGameEnabled());
    }

    private void drawConfigContent(GuiGraphics ctx, int x, int y, int w, int h) {
        int contentX = x + padding;
        ctx.enableScissor(contentX, configViewportTop(), x + w - padding, configViewportBottom());
        for (ConfigRule rule : configRules) {
            if (rule.y() >= configViewportTop() && rule.y() < configViewportBottom())
                ctx.fill(rule.startX(), rule.y(), rule.endX(), rule.y() + 1, rule.color());
        }
        for (ConfigLabel label : configLabels) {
            if (label.y() >= configViewportTop() - 10 && label.y() < configViewportBottom())
                ctx.drawString(font, label.text(), label.x(), label.y(), label.color());
        }
        ctx.disableScissor();
        ctx.drawString(font, Component.literal(configStatus), contentX, y + h - 18, 0xFFB0B0B0);
        if (restartRequired) {
            int saveX = x + w - padding - 70;
            ctx.drawString(font, Component.translatable("guide.mtgcard.config.restart_required"),
                    saveX - 310, y + h - 18, 0xFFFFAA55);
        }
        if (configScrollMax > 0) {
            var scrollbar = MtgGuiChrome.layoutScrollbar(
                    new MtgGuiChrome.Rect(x + w - 5, configViewportTop(), 4, configViewportBottom() - configViewportTop()),
                    configViewportBottom() - configViewportTop() + configScrollMax,
                    configViewportBottom() - configViewportTop(), configScroll, 12);
            MtgGuiChrome.drawScrollbar(ctx, scrollbar, 0x33000000, 0x88FFFFFF, 0x33000000);
        }
    }

    public void receiveServerConfig(String json, boolean canEdit, String message) {
        try {
            configOriginal = GuideConfigSnapshot.fromJson(json);
            loadConfigValues();
            canEditServerConfig = canEdit;
            configStatus = message == null || message.isBlank()
                    ? (canEdit ? "Server settings are editable." : "Server settings are read-only; operator permission is required.")
                    : message;
            if (isConfigChapter()) setupGuideWidgets();
        } catch (RuntimeException error) {
            configStatus = "Could not read server settings.";
        }
    }

    private void saveConfig() {
        MtgcardConfig local = MtgcardConfig.get();
        local.Card_Peek_Position = cardPeekRight ? "right" : "left";
        MtgcardConfig.save();
        if (configOriginal == null || !canEditServerConfig) {
            configStatus = "Client settings saved. Server settings are read-only.";
            return;
        }
        try {
            GuideConfigSnapshot update = new GuideConfigSnapshot(
                    configToggle("anyone"), configToggle("ops"), parseList(configText("whitelist")), configText("language"),
                    configDecimal("common"), configDecimal("uncommon"), configDecimal("wildcard"),
                    configDecimal("rare"), configDecimal("random"), configDecimal("foil"),
                    configDecimal("land"), configDecimal("token"), configToggle("pack_debug"),
                    configToggle("fishing"), configToggle("physical_dice"), configToggle("card_store"), configToggle("mtg_game"),
                    configText("price_item"), configText("price_basis")
            );
            if (update.cardStoreEnabled() != configOriginal.cardStoreEnabled()
                    || update.mtgGameEnabled() != configOriginal.mtgGameEnabled()) restartRequired = true;
            configStatus = "Saving server settings…";
            ClientPlayNetworking.send(new GuideBookPackets.ConfigSavePayload(update.toJson()));
        } catch (NumberFormatException error) {
            configStatus = "Chance values must be numbers from 0.0 to 1.0.";
        }
    }

    private Component configPositionText() {
        return Component.translatable(cardPeekRight ? "guide.mtgcard.config.position_right" : "guide.mtgcard.config.position_left");
    }

    private String configText(String key) { return configValues.getOrDefault(key, "").trim(); }
    private boolean configToggle(String key) { return Boolean.TRUE.equals(configToggles.get(key)); }
    private double configDecimal(String key) { return Double.parseDouble(configText(key)); }
    private static Component toggleText(boolean enabled) { return Component.literal(enabled ? "ON" : "OFF"); }
    private static String number(double value) { return Double.toString(value); }

    private static List<String> parseList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim)
                .filter(part -> !part.isEmpty()).distinct().toList();
    }

    private record ConfigLabel(int x, int y, net.minecraft.util.FormattedCharSequence text, int color) {}
    private record ConfigRule(int startX, int y, int endX, int color) {}
}
