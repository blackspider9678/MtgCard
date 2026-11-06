// src/client/java/com/spider/mtgcard/client/gui/CardStoreScreen.java
package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.api.CardStoreProviderRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.cardstore.DeckImportProgress;
import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.compat.MtgGuiScaleHelper;
import com.spider.mtgcard.client.input.GuiCardFaceFlipHandler;
import com.spider.mtgcard.client.input.GuiCardFaceFlipper;
import com.spider.mtgcard.client.java.CardArtManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import com.spider.mtgcard.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CardStoreScreen extends LegacyContainerScreen<CardStoreScreenHandler> implements GuiCardFaceFlipHandler {

    // --- LifeBlock-ish tinting (lighter so world shows through) ---
    // --- Blue-gray "glass" theme (ARGB) ---
// Base hue is a cool slate: R=0x14, G=0x1F, B=0x2A
    private static final int DIM_BG   = 0x66212A33; // full-screen dim (still see world)
    private static final int PANEL_BG = 0x55202A33; // main panels / headers
    private static final int STRIP_BG = 0x44202A33; // info strip
    private static final int INNER_BG = 0x33202A33; // inner sub-panels
    private static final int BOX_BG   = 0x3A202A33; // preview/grid boxes

    // Edges / separators (cool steel)
    private static final int EDGE_LINE   = 0xAA33414F;
    private static final int EDGE_SOFT   = 0x6633414F;
    private static final int TEXT_MUTED  = 0xFFB9C6D3;
    private static final int TEXT_FAINT  = 0xFF90A1B2;

    private ItemStack preview = ItemStack.EMPTY;
    private int previewFace = 0;
    private CardArtManager.TextureRef previewTex = null;

    private long selectedPriceItems = 0;

    private String priceItemId = "minecraft:diamond";
    private String priceBasis  = "USD";

    private ItemStack priceIcon = ItemStack.EMPTY;

    // --- CART UI ---
    private Button clearCartBtn;
    private int selectedCartIndex = -1;
    private int cartScrollPx = 0;

    private static final int CART_ROW_H = 22;
    private static final int CART_ROW_PAD = 4;

    private static final Identifier TEX = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card_store.png");
    private static final FormattedText SCRY_HELP = FormattedText.of("Use Scryfall Syntax for Advanced Search");


    private enum Tab { STORE, CART }
    private Tab tab = Tab.STORE;

    private EditBox searchField;
    private Button addToCartBtn;
    private Button gameBtn;
    private String selectedGame = TcgGameRegistry.ALL_GAMES;
    private boolean gameMenuOpen = false;
    private final ArrayList<Button> gameMenuButtons = new ArrayList<>();

    // ---- virtual GUI size (MUST match ScreenHandler) ----
    private static final int GUI_W = 426;
    private static final int GUI_H = 240;
    private static final int DESIGN_W = 1024;
    private static final int DESIGN_H = 576;
    private static final float MAX_LAYOUT_SCALE = 2.0f;
    private static final int STORE_GUI_SCALE = 4;
    private static final int STORE_MIN_VIEW_W = 600;
    private static final int STORE_MIN_VIEW_H = 300;

    // ---- layout constants ----
    private static final int M = 10;
    private static final int TOP_H = 24;
    private static final int INFO_H = 14;
    private static final int GAP = 8;
    private static final int LEFT_W = 240;

    // Fullscreen layout (independent of handler virtual GUI)
    private int fsX, fsY, fsW, fsH;
    private float layoutScale = 1f;

    // ---- computed layout ----
    private int topX, topY, topW;
    private int infoX, infoY, infoW;
    private int contentX, contentY, contentW, contentH;
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;

    // inventory positions (match handler)
    private int invX, invY, hotbarY;

    private boolean wireframe = false;

    // Vanilla inventory pixel size
    private static final int INV_W = 9 * 18; // 162
    private static final int INV_H = CardStoreScreenHandler.INV_BLOCK_H; // 76 (3 rows + gap + hotbar)

    // Right panel width = inventory width + padding
    private static final int RIGHT_PAD = 10;
    private static final int RIGHT_W = INV_W + RIGHT_PAD * 2;

    // "where the handler placed slots" inside its virtual GUI
// handler uses invX = MARGIN + 8 and invTopY = guiH - MARGIN - INV_BLOCK_H
    private static final int HANDLER_INV_X = CardStoreScreenHandler.MARGIN + 8; // 18
    private static final int HANDLER_INV_TOP_Y = GUI_H - CardStoreScreenHandler.MARGIN - CardStoreScreenHandler.INV_BLOCK_H; // 154

    // --- prints paging ---
    private String lastPrintsQuery = "";
    private Button prevPageBtn;
    private Button nextPageBtn;
    private Button flipPreviewBtn;

    // --- left-panel column layout ---
    private static final int PREVIEW_COL_W = 150; // width of preview column inside left panel
    private static final int COMPACT_PREVIEW_COL_W = 84;
    private static final int LEFT_INNER_PAD = 12;
    private static final int GRID_GAP = 12;
    private static final int PAGER_H = 22;
    private static final int GRID_INSET = 8;
    private static final int MAX_GRID_COLUMNS = 10;
    private static final int MIN_GRID_CELL_W = 44;
    private static final float CARD_THUMB_ASPECT = 63.0F / 88.0F;

    private void recomputeLayoutScale() {
        float sx = this.width / (float) DESIGN_W;
        float sy = this.height / (float) DESIGN_H;
        layoutScale = Math.min(MAX_LAYOUT_SCALE, Math.max(1f, Math.min(sx, sy)));
    }

    private boolean usesCompactLayout() { return this.width <= 640; }

    private int ui(int value) {
        return Math.max(1, Math.round(value * layoutScale));
    }

    private int margin() { return ui(M); }
    private int topBarH() { return ui(TOP_H); }
    private int infoBarH() { return ui(INFO_H); }
    private int layoutGap() { return ui(GAP); }
    private int rightPad() { return ui(RIGHT_PAD); }
    private int rightPanelW() { return INV_W + (rightPad() * 2); }
    private int previewColW() { return ui(usesCompactLayout() ? COMPACT_PREVIEW_COL_W : PREVIEW_COL_W); }
    private int leftInnerPad() { return ui(LEFT_INNER_PAD); }
    private int gridGap() { return ui(GRID_GAP); }
    private int pagerH() { return ui(PAGER_H); }
    private int cartHeaderH() { return ui(CART_HEADER_H); }
    private int cartRowH() { return ui(CART_ROW_H); }
    private int quantityButtonSize() { return ui(QBTN); }
    private int quantityButtonGap() { return ui(QBTN_GAP); }
    private int sortButtonH() { return ui(SORT_BTN_H); }
    private int sortMenuItemH() { return ui(SORT_MENU_ITEM_H); }
    private int sortMenuPad() { return ui(SORT_MENU_PAD); }
    private int cartThumbSize() { return ui(CART_THUMB); }
    private int cartThumbPad() { return ui(CART_THUMB_PAD); }
    private int cartTextPad() { return ui(CART_TEXT_PAD); }

    private int previewBoxX() { return leftX + leftInnerPad(); }
    private int previewBoxY() { return leftY + leftInnerPad(); }
    private int previewBoxW() { return previewColW(); }
    private int previewBoxH() { return Math.max(0, leftH - (leftInnerPad() * 2)); }

    private int gridAreaX() { return previewBoxX() + previewBoxW() + gridGap(); }
    private int gridAreaY() { return leftY + leftInnerPad(); }
    private int gridAreaW() { return Math.max(0, (leftX + leftW - leftInnerPad()) - gridAreaX()); }
    private int gridAreaH() { return Math.max(0, leftH - (leftInnerPad() * 2)); }

    private int gridInset() { return ui(GRID_INSET); }
    private int minGridCellW() { return ui(MIN_GRID_CELL_W); }

    private int gridColumns(int gridW) {
        if (gridW <= 0) return 1;
        if (usesCompactLayout()) return MAX_GRID_COLUMNS;
        return Math.max(1, Math.min(MAX_GRID_COLUMNS, gridW / minGridCellW()));
    }

    private int gridCellPad(int cellW) {
        if (usesCompactLayout()) return ui(1);
        return Math.max(ui(4), Math.min(ui(12), cellW / 10));
    }

    private int gridRowH(int gridW, int cols) {
        int cellW = Math.max(1, (gridW + Math.max(1, cols) - 1) / Math.max(1, cols));
        int pad = gridCellPad(cellW);
        int cardW = Math.max(1, cellW - pad * 2);
        return Math.max(minGridCellW(), Math.round(cardW / CARD_THUMB_ASPECT) + pad * 2);
    }

    private int gridCellLeft(int gridX, int gridW, int cols, int col) {
        return gridX + (col * gridW) / Math.max(1, cols);
    }

    private int gridCellRight(int gridX, int gridW, int cols, int col) {
        return gridX + ((col + 1) * gridW) / Math.max(1, cols);
    }

    private UUID activePrintsRequest = new UUID(0L, 0L);
    private boolean gridLoading = false;
    private int lastRequestedPageSize = 80;

    private Button buyCartBtn;


    // header row
    private static final int CART_HEADER_H = 18;

    // +/- buttons
    private static final int QBTN = 14;      // size of + / - square
    private static final int QBTN_GAP = 4;   // gap between - [qty] +

    private String lastCanonicalName = "";
    private int lastKnownTotalForQuery = 0;

    // pager sits at bottom of grid column
    private int gridContentH() { return Math.max(0, gridAreaH() - pagerH() - ui(6)); }

    private int invPanelY() {
        return (rightY + rightH) - rightPad() - INV_H - ui(14);
    }

    private int invPanelH() {
        return INV_H + ui(14) + rightPad();
    }

    private Button sortBtn;
    private Button importDeckBtn;
    private boolean filePickerOpen;

    // --- Sort dropdown state ---
    private boolean sortMenuOpen = false;
    private int sortMenuHover = -1;

    private static final int SORT_BTN_H = 18;
    private static final int SORT_MENU_ITEM_H = 18;
    private static final int SORT_MENU_PAD = 2;

    private int nextArrivalIndex = 0;

    private SortMode sortMode = SortMode.SCRYFALL_ORDER; // default
    private SortMode cartSortMode = SortMode.SCRYFALL_ORDER;

    private SortMode activeSortMode() {
        return tab == Tab.CART ? cartSortMode : sortMode;
    }

    private String sortLabel(SortMode mode) {
        return tab == Tab.CART && mode == SortMode.SCRYFALL_ORDER ? "Added Order" : mode.label;
    }

    private enum SortMode {
        SCRYFALL_ORDER("Scryfall Order"),
        SET_NUMBER("Set/Number"),
        NAME("Name"),
        PRICE("Price"),
        SET("Set"),
        COLLECTOR_NUMBER("Collector Number");

        final String label;
        SortMode(String label) { this.label = label; }
    }

    private Component sortButtonText() {
        return Component.literal(usesCompactLayout() ? "Sort" : "Sort: " + sortLabel(activeSortMode()));
    }

    private int computeGridCapacity() {
        int inset = gridInset();
        int gridW = gridAreaW() - (inset * 2);
        int gridH = gridContentH() - ui(8);
        if (gridW <= 0 || gridH <= 0) return 1;

        int cols = gridColumns(gridW);
        int rows = Math.max(1, gridH / gridRowH(gridW, cols));

        int cap = cols * rows;
        return Math.max(1, Math.min(MAX_PAGE_SIZE, cap));
    }

    private void toggleSortMenu() {
        sortMenuOpen = !sortMenuOpen;
        sortMenuHover = -1;
    }

    private void closeSortMenu() {
        sortMenuOpen = false;
        sortMenuHover = -1;
    }

    private String normalizeClientGame(String game) {
        String normalized = CardStoreProviderRegistry.sanitizeFilterId(game);
        if (normalized.isBlank()) return TcgGameRegistry.ALL_GAMES;
        return CardStoreProviderRegistry.containsProvider(normalized) ? normalized : TcgGameRegistry.ALL_GAMES;
    }

    private boolean sameGame(String game) {
        if (selectedGame.isBlank()) return true;
        return selectedGame.equals(CardStoreProviderRegistry.sanitizeGameId(game));
    }

    private String gameButtonText(String game) {
        String label = TcgGameRegistry.labelForFilter(game).getString().trim();
        if (label.isEmpty()) label = TcgGameRegistry.shortLabel(game);
        return label.length() <= 10 ? label : label.substring(0, 10);
    }

    private void updateSearchHint() {
        if (searchField == null) return;
        searchField.setHint(Component.literal(isMtgSearchContext() ? "Scryfall syntax" : "Search cards"));
    }

    private boolean isMtgSearchContext() {
        return TcgGameRegistry.MTG.equals(selectedGame)
                || (selectedGame.isBlank() && !CardStoreProviderRegistry.hasMultipleProviders());
    }

    private void toggleGameMenu() {
        if (gameMenuOpen) closeGameMenu();
        else openGameMenu();
    }

    private void openGameMenu() {
        if (gameBtn == null) return;
        closeGameMenu();

        List<TcgGameRegistry.Entry> options = CardStoreProviderRegistry.filterOptionsWithProviders();
        if (options.size() <= 1) return;

        int rowH = ui(18);
        int w = ui(112);
        int hTotal = options.size() * rowH;
        int x0 = gameBtn.getX();
        int y0 = gameBtn.getY() + gameBtn.getHeight() + ui(2);
        x0 = Math.max(ui(4), Math.min(x0, this.width - w - ui(4)));
        y0 = Math.max(ui(4), Math.min(y0, this.height - hTotal - ui(4)));

        for (int i = 0; i < options.size(); i++) {
            TcgGameRegistry.Entry option = options.get(i);
            Button button = Button.builder(option.label(), b -> selectGame(option.id()))
                    .bounds(x0, y0 + (i * rowH), w, rowH)
                    .build();
            gameMenuButtons.add(button);
            this.addRenderableWidget(button);
        }

        gameMenuOpen = true;
    }

    private void closeGameMenu() {
        if (!gameMenuButtons.isEmpty()) {
            for (Button button : gameMenuButtons) {
                this.removeWidget(button);
            }
            gameMenuButtons.clear();
        }
        gameMenuOpen = false;
    }

    private void selectGame(String game) {
        String next = normalizeClientGame(game);
        closeGameMenu();
        if (selectedGame.equals(next)) return;

        selectedGame = next;
        if (gameBtn != null) gameBtn.setMessage(Component.literal(gameButtonText(selectedGame)));
        updateSearchHint();
        resetStoreSearchState();

        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            ClientPlayNetworking.send(new CardStorePackets.SetGameC2S(menu.blockPos, selectedGame));
        }
        updateWidgetVisibility();
    }

    private void resetStoreSearchState() {
        activePrintsRequest = new UUID(0L, 0L);
        gridLoading = false;
        grid.clear();
        gridPage = 1;
        gridHasMore = false;
        gridTotal = 0;
        selectedIndex = -1;
        nextArrivalIndex = 0;
        lastPrintsQuery = "";
        lastCanonicalName = "";
        lastKnownTotalForQuery = 0;
        resolvedSet = "";
        resolvedCn = "";
        selectedPriceItems = 0;
        preview = ItemStack.EMPTY;
        previewTex = null;
        previewFace = 0;
        if (searchField != null) searchField.setValue("");
        status = "Selected " + TcgGameRegistry.labelForFilter(selectedGame).getString();
    }

    private void setSortMode(SortMode mode) {
        if (mode == null) return;
        if (tab == Tab.CART) {
            cartSortMode = mode;
            sortCart();
            if (sortBtn != null) sortBtn.setMessage(sortButtonText());
            closeSortMenu();
            return;
        }
        if (sortMode == mode) {
            closeSortMenu();
            return;
        }
        sortMode = mode;
        if (sortBtn != null) sortBtn.setMessage(sortButtonText());
        sortGrid();          // << only happens once per selection
        closeSortMenu();
    }

    private static int parseCollectorNumberLoose(String cn) {
        if (cn == null) return Integer.MAX_VALUE;
        // Extract leading digits if possible (handles "123", "123a", "123★", etc.)
        int i = 0;
        while (i < cn.length() && Character.isDigit(cn.charAt(i))) i++;
        if (i == 0) return Integer.MAX_VALUE;
        try { return Integer.parseInt(cn.substring(0, i)); }
        catch (Exception ignored) { return Integer.MAX_VALUE; }
    }

    private void sortGrid() {
        if (grid.isEmpty()) return;

        grid.sort(printingComparator(sortMode));

        // keep selection sane after resort
        if (selectedIndex < 0 && !grid.isEmpty()) selectedIndex = 0;
        if (selectedIndex >= grid.size()) selectedIndex = grid.size() - 1;

        if (tab == Tab.STORE && hasValidSelection()) {
            var sel = grid.get(selectedIndex);
            preview = sel.stack;
            selectedPriceItems = sel.priceItems;
            previewFace = 0;
            previewTex = null;
            resolvedSet = sel.set;
            resolvedCn = sel.cn;
        }

        if (addToCartBtn != null) addToCartBtn.active = hasValidSelection();
    }

    private java.util.Comparator<PrintEntry> printingComparator(SortMode mode) {
        return (a, b) -> {
            switch (mode) {
                case SCRYFALL_ORDER -> {
                    return Integer.compare(a.arrival, b.arrival);
                }
                case NAME -> {
                    String an = a.stack.getHoverName().getString();
                    String bn = b.stack.getHoverName().getString();
                    int c = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
                    if (c != 0) return c;
                    // tiebreak
                    c = String.CASE_INSENSITIVE_ORDER.compare(a.set, b.set);
                    if (c != 0) return c;
                    return Integer.compare(parseCollectorNumberLoose(a.cn), parseCollectorNumberLoose(b.cn));
                }
                case PRICE -> {
                    int c = Long.compare(b.priceItems, a.priceItems); // high -> low (feel free to flip)
                    if (c != 0) return c;
                    c = String.CASE_INSENSITIVE_ORDER.compare(a.set, b.set);
                    if (c != 0) return c;
                    return Integer.compare(parseCollectorNumberLoose(a.cn), parseCollectorNumberLoose(b.cn));
                }
                case SET -> {
                    int c = String.CASE_INSENSITIVE_ORDER.compare(a.set, b.set);
                    if (c != 0) return c;
                    return Integer.compare(parseCollectorNumberLoose(a.cn), parseCollectorNumberLoose(b.cn));
                }
                case COLLECTOR_NUMBER -> {
                    int c = Integer.compare(parseCollectorNumberLoose(a.cn), parseCollectorNumberLoose(b.cn));
                    if (c != 0) return c;
                    return String.CASE_INSENSITIVE_ORDER.compare(a.set, b.set);
                }
                case SET_NUMBER -> {
                    // "Collector Number is default" interpreted as set + cn ordering (like your screenshot behavior)
                    int c = String.CASE_INSENSITIVE_ORDER.compare(a.set, b.set);
                    if (c != 0) return c;
                    c = Integer.compare(parseCollectorNumberLoose(a.cn), parseCollectorNumberLoose(b.cn));
                    if (c != 0) return c;
                    // final tie break by name
                    return String.CASE_INSENSITIVE_ORDER.compare(
                            a.stack.getHoverName().getString(),
                            b.stack.getHoverName().getString()
                    );
                }
            }
            return 0;
        };
    }

    private void sortCart() {
        CartLine selected = selectedCartIndex >= 0 && selectedCartIndex < cart.size()
                ? cart.get(selectedCartIndex) : null;
        var keys = new java.util.IdentityHashMap<CartLine, PrintEntry>();
        for (var line : cart) {
            keys.put(line, new PrintEntry(line.game, line.set, line.cn, line.stack, line.priceItems, line.arrival));
        }
        var comparator = printingComparator(cartSortMode);
        cart.sort((a, b) -> comparator.compare(keys.get(a), keys.get(b)));
        selectedCartIndex = selected == null ? -1 : cart.indexOf(selected);
        clampCartScroll();
    }

    private void refreshTabPreview() {
        preview = ItemStack.EMPTY;
        selectedPriceItems = 0;
        if (tab == Tab.STORE && hasValidSelection()) {
            var entry = grid.get(selectedIndex);
            preview = entry.stack;
            selectedPriceItems = entry.priceItems;
        } else if (tab == Tab.CART && selectedCartIndex >= 0 && selectedCartIndex < cart.size()) {
            var entry = cart.get(selectedCartIndex);
            preview = entry.stack;
            selectedPriceItems = entry.priceItems;
        }
        previewFace = 0;
        previewTex = null;
    }




    private int clearBtnY() {
        // same Y as addToCart location: directly above inventory block
        int desiredInvTopY = (rightY + rightH) - rightPad() - INV_H;
        return desiredInvTopY - ui(10) - ui(20);
    }

    // CART list now lives in the LEFT panel's grid box area
    private int cartListX() { return gridAreaX() + gridInset(); }
    private int cartListY() { return gridAreaY() + gridInset(); }
    private int cartListW() { return Math.max(0, gridAreaW() - (gridInset() * 2)); }
    private int cartListH() { return Math.max(0, gridAreaH() - (gridInset() * 2)); } // full area (includes header)
    private int cartRowsY() { return cartListY() + cartHeaderH(); }
    private int cartRowsH() { return Math.max(0, cartListH() - cartHeaderH()); }// fill the whole grid box

    private int cartMaxScrollPx() {
        int contentH = cart.size() * cartRowH();
        int viewH = cartRowsH();
        return Math.max(0, contentH - viewH);
    }

    private void clampCartScroll() {
        int max = cartMaxScrollPx();
        if (cartScrollPx < 0) cartScrollPx = 0;
        if (cartScrollPx > max) cartScrollPx = max;
    }

    private boolean isMouseOverCartList(int mx, int my) {
        int x = cartListX(), y = cartRowsY(), w = cartListW(), h = cartRowsH();
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private int hitTestCartIndex(int mx, int my) {
        if (!isMouseOverCartList(mx, my)) return -1;

        int localY = (my - cartRowsY()) + cartScrollPx;
        int idx = localY / cartRowH();

        return (idx >= 0 && idx < cart.size()) ? idx : -1;
    }

    private int cartTotalQty() {
        int q = 0;
        for (var line : cart) q += Math.max(0, line.qty);
        return q;
    }

    private static String shortSet(String set) {
        if (set == null) return "";
        String s = set.trim();
        return (s.length() > 6) ? s.substring(0, 6) : s;
    }

    public CardStoreScreen(CardStoreScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title, GUI_W, GUI_H);
        this.inventoryLabelY = this.imageHeight - 94;
        this.priceItemId = handler.priceItemId();
        this.priceBasis = handler.priceBasis();
        this.selectedGame = normalizeClientGame(handler.selectedGame());
        loadSavedCart(handler.initialCart());
    }

    private void sendConfirm(List<CardStorePackets.ConfirmPurchaseC2S.Line> lines) {
        var payload = new CardStorePackets.ConfirmPurchaseC2S(menu.blockPos, lines.size(), lines);
        ClientPlayNetworking.send(payload);
    }

    private void syncCartToServer() {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;

        ArrayList<CardStoreScreenHandler.CartEntryData> lines = new ArrayList<>();
        for (var line : cart) {
            if (line == null) continue;
            if (line.set == null || line.set.isBlank() || line.cn == null || line.cn.isBlank()) continue;
            if (line.stack == null || line.stack.isEmpty()) continue;

            int qty = Math.max(1, line.qty);
            lines.add(new CardStoreScreenHandler.CartEntryData(
                    line.game,
                    line.set,
                    line.cn,
                    line.stack.copyWithCount(1),
                    Math.max(0L, line.priceItems),
                    qty
            ));
        }
        ClientPlayNetworking.send(new CardStorePackets.SetCartC2S(menu.blockPos, lines.size(), lines));
    }

    private final java.util.ArrayList<PrintEntry> grid = new java.util.ArrayList<>();
    private int gridPage = 1;
    private boolean gridHasMore = false;
    private int gridTotal = 0;
    private int selectedIndex = -1;

    private static class PrintEntry {
        final String game;
        final String set;
        final String cn;
        final ItemStack stack;
        final long priceItems;
        final int arrival; // << NEW

        PrintEntry(String game, String set, String cn, ItemStack stack, long priceItems, int arrival) {
            this.game = CardStoreProviderRegistry.sanitizeGameId(game);
            this.set = set;
            this.cn = cn;
            this.stack = stack;
            this.priceItems = priceItems;
            this.arrival = arrival;
        }
    }


    private void rebuildPriceIcon() {
        try {
            Identifier id = Identifier.parse(priceItemId);
            var item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null) priceIcon = new ItemStack(item);
            else priceIcon = ItemStack.EMPTY;
        } catch (Throwable t) {
            priceIcon = ItemStack.EMPTY;
        }
    }
    private void drawGrid(GuiGraphics ctx, int mouseX, int mouseY) {
        int inset = gridInset();
        int gridX = gridAreaX() + inset;
        int gridY = gridAreaY() + inset;
        int gridW = gridAreaW() - (inset * 2);
        int gridH = gridContentH() - ui(8);
        if (gridW <= 0 || gridH <= 0) return;

        int cols = gridColumns(gridW);
        int rowH = gridRowH(gridW, cols);
        int rowsVisible = Math.max(1, gridH / rowH);
        int maxVisible = cols * rowsVisible;

        int count = Math.min(grid.size(), maxVisible);

        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;

            int cx = gridCellLeft(gridX, gridW, cols, col);
            int cellRight = gridCellRight(gridX, gridW, cols, col);
            int cellW = Math.max(1, cellRight - cx);
            int cy = gridY + row * rowH;
            int pad = gridCellPad(cellW);

            int bg = (i == selectedIndex) ? 0x66FFFFFF : 0x33000000;
            int bgRight = Math.max(cx + 1, cellRight - Math.max(1, pad / 2));
            int bgBottom = Math.max(cy + 1, cy + rowH - Math.max(1, pad / 2));
            ctx.fill(cx, cy, bgRight, bgBottom, bg);
            int tileW = bgRight - cx;
            int tileH = bgBottom - cy;

            ItemStack s = grid.get(i).stack;
            var tex = CardArtManager.getOrRequestFace(s, 0);

            if (tex != null && tex.id() != null) {
                int texW = tex.texW();
                int texH = tex.texH();

                float aspect = (float) texW / (float) texH;
                int maxDrawW = Math.max(1, cellW - pad * 2);
                int maxDrawH = Math.max(1, rowH - pad * 2);
                int drawW = maxDrawW;
                int drawH = (int) (drawW / aspect);
                if (drawH > maxDrawH) {
                    drawH = maxDrawH;
                    drawW = (int) (drawH * aspect);
                }

                int dx = cx + (tileW - drawW) / 2;
                int dy = cy + (tileH - drawH) / 2;

                float sx = (float) drawW / (float) texW;
                float sy = (float) drawH / (float) texH;

                var m = ctx.pose();
                m.pushMatrix();
                m.translate(dx, dy);
                m.scale(sx, sy);

                ctx.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex.id(),
                        0, 0,
                        0f, 0f,
                        texW, texH,
                        texW, texH
                );
                m.popMatrix();
            } else {
                ctx.renderItem(s, cx + (tileW - 16) / 2, cy + (tileH - 16) / 2);
            }
        }
    }

    private boolean isCreativePlayer() {
        var p = this.minecraft != null ? this.minecraft.player : null;
        return p != null && p.getAbilities().instabuild;
    }

    private long countCurrencyInInventory() {
        var p = (this.minecraft != null) ? this.minecraft.player : null;
        if (p == null) return 0L;

        Identifier id;
        try {
            id = Identifier.parse(priceItemId);
        } catch (Throwable t) {
            return 0L;
        }

        var item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return 0L;

        long count = 0L;

        // Player main inventory (includes hotbar + main)
        var inv = p.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(item)) count += s.getCount();
        }

        // Offhand is not in PlayerInventory list on some mappings—count it explicitly
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty() && off.is(item)) count += off.getCount();

        return count;
    }

    private boolean canAffordCart() {
        if (isCreativePlayer()) return true;
        long totalCost = cartTotal();
        if (totalCost <= 0) return true; // free cart
        return countCurrencyInInventory() >= totalCost;
    }

    @Override
    protected void init() {
        if (MtgGuiScaleHelper.applyAutoFitGuiScale(
                this,
                STORE_GUI_SCALE,
                Math.max(GUI_W, STORE_MIN_VIEW_W),
                Math.max(GUI_H, STORE_MIN_VIEW_H)
        )) return;

        super.init();
        recomputeLayoutScale();

        // Fullscreen panel space
        fsX = 0;
        fsY = 0;
        fsW = this.width;
        fsH = this.height;

        // ----- Panels (FULL HEIGHT) -----
        int margin = margin();
        int topBarH = topBarH();
        int infoBarH = infoBarH();
        int gap = layoutGap();
        int rightPad = rightPad();

        topX = margin;
        topY = margin;
        topW = fsW - (margin * 2);

        infoX = topX;
        infoY = topY + topBarH + ui(2);
        infoW = topW;

        contentX = topX;
        contentY = infoY + infoBarH + ui(6);
        contentW = topW;
        contentH = (fsH - margin) - contentY;

        // Right panel fixed width, left panel takes remaining
        rightW = rightPanelW();
        rightX = fsW - margin - rightW;
        rightY = contentY;
        rightH = contentH;

        leftX = contentX;
        leftY = contentY;
        leftW = Math.max(1, (rightX - gap) - leftX);
        leftH = contentH;

        // ----- Inventory placement INSIDE right panel (bottom) -----
        int desiredInvTopY = (rightY + rightH) - rightPad - INV_H;
        int desiredInvLeftX = rightX + rightPad;

        // Move the entire GUI origin so slot grid lands there
        this.leftPos = desiredInvLeftX - HANDLER_INV_X;
        this.topPos = desiredInvTopY - HANDLER_INV_TOP_Y;

        // Inventory title (above slots)
        this.inventoryLabelX = desiredInvLeftX;
        this.inventoryLabelY = desiredInvTopY - ui(12);

        // Screen title (top-left)
        this.titleLabelX = topX + ui(4);
        this.titleLabelY = topY + ui(6);

        // Clear & rebuild widgets
        closeGameMenu();
        this.clearWidgets();
        gameBtn = null;

        // Tabs
        int tabX = topX + ui(2);
        int tabY = topY + ui(2);
        int tabW = ui(64);
        int tabH = ui(18);
        int tabGap = ui(4);
        int cursorX = tabX;

        this.addRenderableWidget(Button.builder(Component.literal("Store"), b -> {
            tab = Tab.STORE;
            refreshTabPreview();
            closeSortMenu();
            updateWidgetVisibility();
        }).bounds(cursorX, tabY, tabW, tabH).build());
        cursorX += tabW + tabGap;

        if (CardStoreProviderRegistry.hasMultipleProviders()) {
            int gameW = ui(72);
            gameBtn = this.addRenderableWidget(Button.builder(
                    Component.literal(gameButtonText(selectedGame)),
                    b -> toggleGameMenu()
            ).bounds(cursorX, tabY, gameW, tabH).build());
            cursorX += gameW + tabGap;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Cart"), b -> {
            tab = Tab.CART;
            refreshTabPreview();
            closeSortMenu();
            updateWidgetVisibility();
        }).bounds(cursorX, tabY, tabW, tabH).build());

        int importW = ui(72);
        importDeckBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Import Deck"), b -> openDeckFilePicker()
        ).bounds(topX + topW - ui(4) - importW, tabY, importW, tabH).build());

        // --- NOW that leftX/leftY exist, compute previewBox coords ---
        int sortX = previewBoxX() + ui(6);
        int sortY = previewBoxY() + ui(6);
        int sortW = previewBoxW() - ui(12);

        sortBtn = this.addRenderableWidget(Button.builder(
                sortButtonText(),
                b -> toggleSortMenu()
        ).bounds(sortX, sortY, sortW, sortButtonH()).build());

        flipPreviewBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Flip Card"),
                b -> flipPreviewFace()
        ).bounds(sortX, sortY + sortButtonH() + ui(4), sortW, sortButtonH()).build());

        // Right panel fields (above inventory area)
        int fieldX = rightX + rightPad;
        int fieldY = rightY + rightPad;

        int fieldW = rightW - (rightPad * 2);

        searchField = new EditBox(this.font, fieldX, fieldY, fieldW, ui(18), Component.literal(""));
        searchField.setMaxLength(512);
        updateSearchHint();
        this.addWidget(searchField);

        // Add-to-cart sits ABOVE the inventory block, aligned to right panel
        // Add-to-cart / Clear Cart sit ABOVE the inventory block, aligned to right panel
        int btnW = fieldW;
        int btnH = ui(20);
        int btnX = fieldX;

        // base “action row” just above inventory
        int btnY = desiredInvTopY - ui(10) - btnH;

        // STORE button
        addToCartBtn = this.addRenderableWidget(Button.builder(Component.literal("Add to Cart"), btn -> {
            addSelectedToCart();
        }).bounds(btnX, btnY, btnW, btnH).build());

        // CART buttons (stacked)
        int clearY = btnY;
        int buyY   = btnY - (btnH + ui(6));

        buyCartBtn = this.addRenderableWidget(Button.builder(Component.literal("Buy"), btn -> {
            importStatus = "";
            if (cart.isEmpty()) { status = "Cart is empty."; return; }
            if (!canAffordCart()) { status = "You can't afford this cart."; return; }

            // Build purchase lines from cart
            java.util.ArrayList<CardStorePackets.ConfirmPurchaseC2S.Line> lines = new java.util.ArrayList<>();
            for (var line : cart) {
                int q = Math.max(0, line.qty);
                if (q <= 0) continue;
                lines.add(new CardStorePackets.ConfirmPurchaseC2S.Line(line.game, line.set, line.cn, q));
            }

            if (lines.isEmpty()) { status = "Cart is empty."; return; }

            // Send purchase
            sendConfirm(lines);
            status = "Purchase sent.";

            cart.clear();
            selectedCartIndex = -1;
            cartScrollPx = 0;
            preview = ItemStack.EMPTY;
            previewTex = null;
            selectedPriceItems = 0;

            syncCartToServer();
            updateWidgetVisibility(); // disables Buy/Clear when empty
        }).bounds(btnX, buyY, btnW, btnH).build());


        clearCartBtn = this.addRenderableWidget(Button.builder(Component.literal("Clear Cart"), btn -> {
            importStatus = "";
            cart.clear();
            selectedCartIndex = -1;
            cartScrollPx = 0;
            preview = ItemStack.EMPTY;
            previewTex = null;
            selectedPriceItems = 0;
            status = "Cart cleared.";
            syncCartToServer();
            updateWidgetVisibility();
        }).bounds(btnX, clearY, btnW, btnH).build());


        int gx = gridAreaX();
        int gy = gridAreaY();
        int gw = gridAreaW();
        int pagerY = gy + gridAreaH() - pagerH() - ui(6);

        int pagerBtnW = ui(70);
        int pagerBtnH = ui(18);
        int pagerGap = ui(8);

        int pageLabelW = ui(110);
        int totalW = pagerBtnW + pagerGap + pageLabelW + pagerGap + pagerBtnW;
        int startX = gx + (gw - totalW) / 2;

        prevPageBtn = this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            if (lastPrintsQuery.isBlank()) return;
            if (gridPage <= 1) return;

            int target = gridPage - 1;

            int pageSize = computeGridCapacity();
            lastRequestedPageSize = pageSize;

            UUID req = UUID.randomUUID();
            activePrintsRequest = req;

            ClientPlayNetworking.send(new CardStorePackets.SearchPrintsC2S(menu.blockPos, selectedGame, lastPrintsQuery, target, pageSize, req));
            status = "Loading page " + target + "…";

        }).bounds(startX, pagerY, pagerBtnW, pagerBtnH).build());

        nextPageBtn = this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            if (lastPrintsQuery.isBlank()) return;

            boolean computedHasMore = (gridTotal > gridPage * lastRequestedPageSize);
            boolean canNext = gridHasMore || computedHasMore;
            if (!canNext) return;

            int target = gridPage + 1;

            int pageSize = computeGridCapacity();
            lastRequestedPageSize = pageSize;

            UUID req = UUID.randomUUID();
            activePrintsRequest = req;

            ClientPlayNetworking.send(new CardStorePackets.SearchPrintsC2S(menu.blockPos, selectedGame, lastPrintsQuery, target, pageSize, req));
            status = "Loading page " + target + "…";

        }).bounds(startX + pagerBtnW + pagerGap + pageLabelW + pagerGap, pagerY, pagerBtnW, pagerBtnH).build());
        if (addToCartBtn != null) addToCartBtn.active = hasValidSelection();

        rebuildPriceIcon();
        updateWidgetVisibility();
    }

    private void refreshBuyEnabled() {
        if (buyCartBtn == null) return;
        if (tab != Tab.CART) return;

        buyCartBtn.active = !isImportingDeck() && !cart.isEmpty() && canAffordCart();
    }

    /**
     * Your handler adds 27 inventory slots first, then 9 hotbar slots (total 36).
     * We reposition them here to sit at the bottom of the screen.
     */

    private int sortMenuX() { return sortBtn.getX(); }
    private int sortMenuY() { return sortBtn.getY() + sortBtn.getHeight() + 2; }
    private int sortMenuW() { return sortBtn.getWidth(); }

    private int sortMenuH() {
        return (sortMenuPad() * 2) + (SortMode.values().length * sortMenuItemH());
    }

    private int hitTestSortMenuIndex(int mx, int my) {
        if (!sortMenuOpen || sortBtn == null) return -1;

        int x = sortMenuX();
        int y = sortMenuY();
        int w = sortMenuW();
        int h = sortMenuH();

        if (mx < x || my < y || mx >= x + w || my >= y + h) return -1;

        int localY = my - y - sortMenuPad();
        if (localY < 0) return -1;

        int idx = localY / sortMenuItemH();
        if (idx < 0 || idx >= SortMode.values().length) return -1;

        return idx;
    }

    private void renderSortMenu(GuiGraphics ctx, int mouseX, int mouseY) {
        if (!sortMenuOpen || sortBtn == null) return;

        int x = sortMenuX();
        int y = sortMenuY();
        int w = sortMenuW();
        int h = sortMenuH();

        // Background
        ctx.fill(x, y, x + w, y + h, 0xCC10161C);
        outline(ctx, x, y, w, h, 0xAA33414F);

        // Hover
        sortMenuHover = hitTestSortMenuIndex(mouseX, mouseY);

        SortMode[] vals = SortMode.values();
        for (int i = 0; i < vals.length; i++) {
            int iy0 = y + sortMenuPad() + i * sortMenuItemH();
            int iy1 = iy0 + sortMenuItemH();

            boolean hovered = (i == sortMenuHover);
            boolean selected = (vals[i] == activeSortMode());

            if (hovered) ctx.fill(x + 1, iy0, x + w - 1, iy1, 0x33FFFFFF);
            if (selected) ctx.fill(x + 1, iy0, x + w - 1, iy1, 0x2200FFFF);

            ctx.drawString(
                    this.font,
                    Component.literal(sortLabel(vals[i])),
                    x + ui(6),
                    iy0 + ui(5),
                    0xFFFFFFFF,
                    false
            );
        }
    }

    private void updateWidgetVisibility() {
        if (importDeckBtn != null) importDeckBtn.active = !filePickerOpen && !isImportingDeck();
        boolean store = (tab == Tab.STORE);
        boolean cartTab = (tab == Tab.CART);

        if (sortBtn != null) {
            sortBtn.visible = true;
            sortBtn.setMessage(sortButtonText());
        }
        if (flipPreviewBtn != null) {
            flipPreviewBtn.visible = true;
            flipPreviewBtn.active = GuiCardFaceFlipper.isDoubleFaced(preview);
        }
        if (!store) closeGameMenu();

        if (searchField != null) searchField.setVisible(store);
        if (gameBtn != null) {
            gameBtn.visible = store && CardStoreProviderRegistry.hasMultipleProviders();
            gameBtn.setMessage(Component.literal(gameButtonText(selectedGame)));
        }

        if (addToCartBtn != null) addToCartBtn.visible = store;
        if (prevPageBtn != null) prevPageBtn.visible = store;
        if (nextPageBtn != null) nextPageBtn.visible = store;

        if (buyCartBtn != null) buyCartBtn.visible = cartTab;
        if (clearCartBtn != null) clearCartBtn.visible = cartTab;

        if (buyCartBtn != null) buyCartBtn.active = cartTab && !isImportingDeck() && !cart.isEmpty() && canAffordCart();
        if (clearCartBtn != null) clearCartBtn.active = cartTab && !isImportingDeck() && !cart.isEmpty();
    }

    private void adjustCartQty(int idx, int delta) {
        if (idx < 0 || idx >= cart.size()) return;

        CartLine line = cart.get(idx);
        line.qty = Math.max(0, line.qty + delta);

        if (line.qty <= 0) {
            cart.remove(idx);

            if (cart.isEmpty()) {
                selectedCartIndex = -1;
                preview = ItemStack.EMPTY;
                previewTex = null;
                selectedPriceItems = 0;
            } else {
                if (selectedCartIndex == idx) selectedCartIndex = Math.min(idx, cart.size() - 1);
                else if (selectedCartIndex > idx) selectedCartIndex--;
            }

            clampCartScroll();
            updateWidgetVisibility();
            return;
        }

        clampCartScroll();
        updateWidgetVisibility();
    }

    private boolean handleCartRowButtonsClick(int mx, int my) {
        int idx = hitTestCartIndex(mx, my);
        if (idx == -1) return false;

        // Recompute this row’s rects exactly like drawCartRows
        int x = cartListX();
        int yRows = cartRowsY();
        int w = cartListW();

        int rowY = yRows + (idx * cartRowH()) - cartScrollPx;

        int rx0 = x + ui(6);
        int ry0 = rowY + 2;
        int rx1 = x + w - ui(6);
        // int ry1 = rowY + CART_ROW_H - 2; // not needed for click math

        int rightEdge = rx1 - ui(6);

        CartLine line = cart.get(idx);

        String price = String.valueOf(Math.max(0L, line.priceItems));
        String cnRaw = (line.cn == null ? "" : line.cn);
        String cn    = (cnRaw.length() <= 3) ? cnRaw : cnRaw.substring(0, 3);
        String set   = shortSet(line.set).toUpperCase();
        String qtyStr = "x" + Math.max(1, line.qty);

        // Walk right-to-left like rendering: [price][cn][set][ qty controls ]
        int right = rightEdge;
        int qBtn = quantityButtonSize();
        int qGap = quantityButtonGap();

        int pw = this.font.width(price);
        right -= pw + ui(10);

        int cnw = this.font.width(cn);
        right -= cnw + ui(10);

        int sw = this.font.width(set);
        right -= sw + ui(10);

        // Qty controls geometry (must match drawCartRows)
        int qw = this.font.width(qtyStr);
        int pillW = qw + ui(8);
        int pillY = (ry0 + ui(6)) + 1;
        int btnY  = pillY - 1;

        int plusX  = right - qBtn;
        int pillX  = plusX - qGap - pillW;
        int minusX = pillX - qGap - qBtn;

        // minus
        if (mx >= minusX && my >= btnY && mx < minusX + qBtn && my < btnY + qBtn) {
            adjustCartQty(idx, -1);
            return true;
        }

        // plus
        if (mx >= plusX && my >= btnY && mx < plusX + qBtn && my < btnY + qBtn) {
            adjustCartQty(idx, +1);
            return true;
        }

        return false;
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl) {
        int mx = (int) click.x();
        int my = (int) click.y();

        if (gameMenuOpen && handleGameMenuClick(click, bl, mx, my)) {
            return true;
        }

        boolean handled = super.mouseClicked(click, bl);

        // If tabs were clicked (buttons), visibility should update right away
        updateWidgetVisibility();

        // If dropdown open, consume clicks for it first
        // If dropdown open, consume clicks for it first
        if (sortMenuOpen) {
            // IMPORTANT: clicking the sort button should NOT close the menu
            if (isMouseOverSortButton(mx, my)) {
                return true; // let the button's onPress toggle it (super already did)
            }

            int idx = hitTestSortMenuIndex(mx, my);
            if (idx != -1) {
                setSortMode(SortMode.values()[idx]);
                return true;
            } else {
                closeSortMenu();
                return true;
            }
        }

        // Also forward click to text fields if needed (HandledScreen usually handles children,
        // but since these are selectable-only, it's safer to ping them)
        if (tab == Tab.STORE) {
            int idx = hitTestGridIndex((int)click.x(), (int)click.y());
            if (idx != -1) {
                selectedIndex = idx;
                if (addToCartBtn != null) addToCartBtn.active = hasValidSelection();
                var e = grid.get(idx);

                preview = e.stack;
                selectedPriceItems = e.priceItems;
                previewFace = 0;
                previewTex = null;
                updateWidgetVisibility();

                return true;
            }
        }

        if (tab == Tab.CART) {
            // First: +/- buttons
            if (handleCartRowButtonsClick((int)click.x(), (int)click.y())) {
                return true;
            }

            // Otherwise: selecting row
            int idx = hitTestCartIndex((int) click.x(), (int) click.y());
            if (idx != -1) {
                selectedCartIndex = idx;
                var line = cart.get(idx);

                preview = line.stack;
                selectedPriceItems = line.priceItems;
                previewFace = 0;
                previewTex = null;
                updateWidgetVisibility();

                return true;
            }
        }

        return handled;
    }

    private boolean handleGameMenuClick(net.minecraft.client.input.MouseButtonEvent click, boolean bl, int mx, int my) {
        for (int i = gameMenuButtons.size() - 1; i >= 0; i--) {
            Button button = gameMenuButtons.get(i);
            if (button.visible && button.mouseClicked(click, bl)) {
                updateWidgetVisibility();
                return true;
            }
        }

        if (isMouseOverGameButton(mx, my)) {
            closeGameMenu();
            return true;
        }

        closeGameMenu();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.CART && isMouseOverCartList((int) mouseX, (int) mouseY)) {
            cartScrollPx -= (int) Math.signum(verticalAmount) * cartRowH();
            clampCartScroll(); // ✅ put this back
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // --- CART row polish ---
    private static final int CART_THUMB = 18;     // thumbnail square
    private static final int CART_THUMB_PAD = 3;  // inner padding
    private static final int CART_TEXT_PAD = 6;

    private boolean isTextureReady(Identifier id) {
        if (this.minecraft == null) return false;
        var tm = this.minecraft.getTextureManager();
        var tex = tm.getTexture(id);
        return tex != null; // if it exists here, it’s registered/boundable
    }

    private void drawCardThumb(GuiGraphics ctx, ItemStack stack, int x, int y, int size) {
        var tex = CardArtManager.getOrRequestFace(stack, 0);

        // fallback if not ready
        if (tex == null || tex.id() == null) {
            ctx.renderItem(stack, x, y);
            return;
        }

        int texW = tex.texW();
        int texH = tex.texH();

        float aspect = (float) texW / (float) texH;

        // Fit the card into a square thumb
        int drawW = size;
        int drawH = (int) (drawW / aspect);
        if (drawH > size) {
            drawH = size;
            drawW = (int) (drawH * aspect);
        }

        int dx = x + (size - drawW) / 2;
        int dy = y + (size - drawH) / 2;

        float sx = (float) drawW / (float) texW;
        float sy = (float) drawH / (float) texH;

        var m = ctx.pose();
        m.pushMatrix();
        m.translate((float) dx, (float) dy);
        m.scale(sx, sy);

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                tex.id(),
                0, 0,
                0f, 0f,
                texW, texH,
                texW, texH
        );

        m.popMatrix();

        // subtle border so it reads like an icon
        outline(ctx, x, y, size, size, 0x66000000);
    }

    private int hitTestGridIndex(int mx, int my) {
        int inset = gridInset();
        int gridX = gridAreaX() + inset;
        int gridY = gridAreaY() + inset;
        int gridW = gridAreaW() - (inset * 2);
        int gridH = gridContentH() - ui(8);
        if (gridW <= 0 || gridH <= 0) return -1;

        if (mx < gridX || my < gridY || mx >= gridX + gridW || my >= gridY + gridH) return -1;

        int cols = gridColumns(gridW);
        int rowH = gridRowH(gridW, cols);
        int col = (int) (((long) (mx - gridX) * cols) / gridW);
        int row = (my - gridY) / rowH;
        int rowsVisible = Math.max(1, gridH / rowH);

        int idx = row * cols + col;
        int maxVisible = cols * rowsVisible;

        if (col < 0 || col >= cols || row < 0 || row >= rowsVisible) return -1;
        if (idx < 0 || idx >= maxVisible) return -1;
        if (idx >= grid.size()) return -1;

        return idx;
    }

    public void onPrintsStart(CardStorePackets.SearchPrintsStartS2C p) {
        if (!p.storePos().equals(menu.blockPos)) return;

        // accept + lock to this request
        activePrintsRequest = p.requestId();

        status = p.message();
        nextArrivalIndex = 0;

        if (p.priceItemId() != null && !p.priceItemId().isBlank()) priceItemId = p.priceItemId();
        if (p.priceBasis() != null && !p.priceBasis().isBlank()) priceBasis = p.priceBasis();
        rebuildPriceIcon();

        grid.clear();
        selectedIndex = -1;
        preview = ItemStack.EMPTY;
        previewTex = null;
        selectedPriceItems = 0;

        gridPage = p.page();
        gridTotal = p.total();
        gridHasMore = p.hasMore();

        gridLoading = true;

        // Disable buttons until DONE (prevents spam + keeps UX clean)
        if (prevPageBtn != null) prevPageBtn.active = false;
        if (nextPageBtn != null) nextPageBtn.active = false;
        if (addToCartBtn != null) addToCartBtn.active = false;
    }

    public void onPrintsAdd(CardStorePackets.SearchPrintsAddS2C p) {
        if (!p.storePos().equals(menu.blockPos)) return;
        if (!p.requestId().equals(activePrintsRequest)) return;

        var e = p.entry();
        if (e == null || e.stack() == null || e.stack().isEmpty()) return;

        grid.add(new PrintEntry(e.game(), e.setCode(), e.collectorNumber(), e.stack(), e.priceItems(), nextArrivalIndex++));

        // auto-select first arriving card
        if (selectedIndex < 0 && !grid.isEmpty()) {
            selectedIndex = 0;
            if (tab == Tab.STORE) refreshTabPreview();
            resolvedSet = grid.get(0).set;
            resolvedCn = grid.get(0).cn;
            if (addToCartBtn != null) addToCartBtn.active = true;
            updateWidgetVisibility();
        }

        // keep sort behavior: either:
        // A) don't sort until DONE (best performance)
        // B) or incremental insert sort (more code)
    }

    public void onPrintsDone(CardStorePackets.SearchPrintsDoneS2C p) {
        if (!p.storePos().equals(menu.blockPos)) return;
        if (!p.requestId().equals(activePrintsRequest)) return;

        status = p.message();

        gridLoading = false;

        // Now do ONE sort pass
        sortGrid();

        // enable pager
        boolean canPrev = (gridPage > 1);
        boolean computedHasMore = (gridTotal > gridPage * lastRequestedPageSize);
        boolean canNext = gridHasMore || computedHasMore;

        if (prevPageBtn != null) prevPageBtn.active = canPrev;
        if (nextPageBtn != null) nextPageBtn.active = canNext;

        if (addToCartBtn != null) addToCartBtn.active = hasValidSelection();
    }


    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        int topBarH = topBarH();
        int infoBarH = infoBarH();

        // dim the world
        ctx.fill(0, 0, this.width, this.height, DIM_BG);

        // top bar + info strip
        ctx.fill(topX, topY, topX + topW, topY + topBarH, PANEL_BG);
        ctx.fill(infoX, infoY, infoX + infoW, infoY + infoBarH, STRIP_BG);

        // main panels
        ctx.fill(leftX, leftY, leftX + leftW, leftY + leftH, PANEL_BG);
        ctx.fill(rightX, rightY, rightX + rightW, rightY + rightH, PANEL_BG);

        // subtle separators (optional but makes it feel “LifeBlock polished”)
        ctx.fill(topX, topY + topBarH - 1, topX + topW, topY + topBarH, EDGE_LINE);
        ctx.fill(infoX, infoY + infoBarH - 1, infoX + infoW, infoY + infoBarH, EDGE_SOFT);

        // inventory sub-panel
        int invPanelX = rightX + ui(2);
        int invPanelY = invPanelY();
        int invPanelW = rightW - ui(4);
        int invPanelH = invPanelH();
        ctx.fill(invPanelX, invPanelY, invPanelX + invPanelW, invPanelY + invPanelH, INNER_BG);

        // preview + grid boxes
        ctx.fill(previewBoxX(), previewBoxY(), previewBoxX() + previewBoxW(), previewBoxY() + previewBoxH(), BOX_BG);
        ctx.fill(gridAreaX(), gridAreaY(), gridAreaX() + gridAreaW(), gridAreaY() + gridAreaH(), BOX_BG);
    }

    private static void outline(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }


    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        MtgGuiChrome.drawDimBackground(ctx, width, height);
        MtgGuiChrome.drawHudStrips(ctx, width, height);

        super.render(ctx, mouseX, mouseY, delta);

        refreshBuyEnabled();

        String visibleStatus = (isImportingDeck() || tab == Tab.CART) && !importStatus.isBlank()
                ? importStatus : status;
        if (visibleStatus != null && !visibleStatus.isBlank()) {
            ctx.drawString(this.font, Component.literal(visibleStatus), infoX + ui(6), infoY + ui(3), 0xFFFFFFFF, false);
        }

        drawDropHint(ctx);

        if (tab == Tab.STORE) {
            // fields
        searchField.extractRenderState(ctx.unwrap(), mouseX, mouseY, delta);

            // helper text under search field
            int hx = searchField.getX() + 2;
            int hy = searchField.getY() + searchField.getHeight() + 4;

            // wrap to the search field width (minus a little padding)
            int wrapW = searchField.getWidth() - 4;

            String q = (searchField != null) ? searchField.getValue() : "";
            if (isMtgSearchContext() && isAdvancedQuery(q)) {
                ctx.drawWordWrap(this.font, SCRY_HELP, hx, hy, wrapW, TEXT_FAINT, false);
            }

            // grid + pager
            drawGrid(ctx, mouseX, mouseY);
            drawStorePagerLabel(ctx);

            // preview (left)
            drawPreviewPanel(ctx);
        } else if (tab == Tab.CART) {
            // draw cart rows inside LEFT panel grid area
            drawCartRows(ctx, mouseX, mouseY);

            // preview still on left column
            drawPreviewPanel(ctx);

            // --- totals block (right panel, TOP) ---
            long total = cartTotal();
            int totalQty = cartTotalQty();
            int lines = cart.size();

            String totalStr = "Total: " + total;
            String meta = " (" + (priceBasis == null ? "" : priceBasis.toUpperCase())
                    + ")  •  Cards: " + totalQty
                    + "  •  Lines: " + lines;

            // anchor in right panel (top-left)
            int tx = rightX + rightPad();
            int ty = rightY + rightPad() + ui(6);

            // total
            ctx.drawString(this.font, Component.literal(totalStr), tx, ty, 0xFFFFFFFF, false);

            // price icon immediately after total value
            int tw = this.font.width(totalStr);
            if (!priceIcon.isEmpty()) {
                ctx.renderItem(priceIcon, tx + tw + ui(6), ty - ui(2));
            }

            // meta line
            ctx.drawString(this.font, Component.literal(meta), tx, ty + ui(12), 0xFFAAAAAA, false);

        }

        // draw dropdown on top of everything
        renderSortMenu(ctx, mouseX, mouseY);
        if (tab == Tab.STORE) {
            if (gameMenuOpen) {
                renderGameMenu(ctx, mouseX, mouseY, delta);
            }
        }
        this.renderTooltip(ctx, mouseX, mouseY);
    }

    private void renderGameMenu(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        for (Button button : gameMenuButtons) {
            if (button.visible) {
                button.extractRenderState(ctx.unwrap(), mouseX, mouseY, delta);
            }
        }
    }

    private boolean isMouseOverGameButton(int mx, int my) {
        if (gameBtn == null || !gameBtn.visible) return false;
        return mx >= gameBtn.getX() && my >= gameBtn.getY()
                && mx < gameBtn.getX() + gameBtn.getWidth()
                && my < gameBtn.getY() + gameBtn.getHeight();
    }

    private boolean isMouseOverSortButton(int mx, int my) {
        if (sortBtn == null || !sortBtn.visible) return false;
        return mx >= sortBtn.getX() && my >= sortBtn.getY()
                && mx < sortBtn.getX() + sortBtn.getWidth()
                && my < sortBtn.getY() + sortBtn.getHeight();
    }

    private void drawStorePagerLabel(GuiGraphics ctx) {
        int gx = gridAreaX();
        int gy = gridAreaY();
        int gw = gridAreaW();
        int pagerY = gy + gridAreaH() - pagerH() - ui(6);

        int maxPages = Math.max(1, (int) Math.ceil(gridTotal / (double) lastRequestedPageSize));

        String label = "Page " + gridPage + " / " + maxPages;
        int labelW = this.font.width(label);
        ctx.drawString(this.font, Component.literal(label), gx + (gw - labelW) / 2, pagerY + ui(5), 0xFFFFFFFF, false);
    }

    private void drawPreviewPanel(GuiGraphics ctx) {
        if (preview.isEmpty()) return;

        int px = previewBoxX();
        int py = previewBoxY();
        int boxW = previewBoxW();
        int boxH = previewBoxH();

        // Both tabs have Sort and Flip Card above the preview.
        int sortReserve = sortButtonH() * 2 + ui(8);
        py += sortReserve;
        boxH -= sortReserve;

        previewTex = CardArtManager.getOrRequestFace(preview, previewFace);

        if (previewTex != null && previewTex.id() != null) {
            int texW = previewTex.texW();
            int texH = previewTex.texH();

            float aspect = (float) texW / (float) texH;

            int drawW = boxW;
            int drawH = (int) (drawW / aspect);
            if (drawH > boxH) {
                drawH = boxH;
                drawW = (int) (drawH * aspect);
            }

            int dx = px + (boxW - drawW) / 2;
            int dy = py + (boxH - drawH) / 2;

            float sx = (float) drawW / (float) texW;
            float sy = (float) drawH / (float) texH;

            var m = ctx.pose();
            m.pushMatrix();
            m.translate((float) dx, (float) dy);
            m.scale(sx, sy);

            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    previewTex.id(),
                    0, 0,
                    0f, 0f,
                    texW, texH,
                    texW, texH
            );

            if (com.spider.mtgcard.client.render.CardFoilUtil.isFoil(preview)) {
                var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), texW);
                if (sweep != null) {
                    ctx.fill(
                            sweep.drawU(),
                            0,
                            sweep.drawU() + sweep.clipW(),
                            texH,
                            com.spider.mtgcard.client.render.CardFoilUtil.guiShimmerColor(1f)
                    );
                }
            }

            m.popMatrix();

            // price strip (same as before)
            int pricePad = ui(6);
            int priceRowH = ui(18);

            int priceX = px + pricePad;
            int priceY = (py + boxH) - pricePad - priceRowH;

            ctx.fill(priceX - 2, priceY - 2, px + boxW - pricePad, priceY + priceRowH, 0x66000000);

            if (!priceIcon.isEmpty()) ctx.renderItem(priceIcon, priceX, priceY);

            String costText = (selectedPriceItems <= 0) ? "Cost: —" : ("Cost: " + selectedPriceItems);
            int textX = priceX + ui(18);

            ctx.drawString(this.font, Component.literal(costText), textX, priceY + ui(5), 0xFFFFFFFF, false);

            if (priceBasis != null && !priceBasis.isBlank()) {
                String basis = "(" + priceBasis.toUpperCase() + ")";
                ctx.drawString(
                        this.font,
                        Component.literal(basis),
                        textX + this.font.width(costText) + ui(6),
                        priceY + ui(5),
                        0xFFAAAAAA,
                        false
                );
            }
        } else {
            ctx.renderItem(preview, px, py);
            ctx.renderItemDecorations(this.font, preview, px, py);
        }
    }

    private Button pageLabelBtn;
    private String status = "";
    private DeckImportProgress<CardStorePackets.ImportDeckC2S.Line> deckImport;
    private String importGame = "";
    private String importStatus = "";

    private boolean isImportingDeck() {
        return deckImport != null && deckImport.isActive();
    }

    private void importNextBatch() {
        importStatus = deckImport.message();
        status = importStatus;
        if (isImportingDeck()) {
            var batch = deckImport.currentBatch();
            var request = new CardStorePackets.ImportDeckC2S(
                    menu.blockPos, importGame, batch.size(), batch);
            // Match replies against the wire game, not the blank "All games" UI filter.
            importGame = request.game();
            ClientPlayNetworking.send(request);
        }
    }
    private String resolvedSet = "";
    private String resolvedCn = "";

    public void onSearchResult(CardStorePackets.SearchS2C p) {
        if (!p.storePos().equals(menu.blockPos)) return;

        status = p.message();

        if (p.ok()) {
            resolvedSet = p.setCode();
            resolvedCn  = p.collectorNumber();

            if (p.name() != null && !p.name().isBlank() && searchField != null) {
                searchField.setValue(p.name());
            }

            preview = p.hasPreview() ? p.preview() : ItemStack.EMPTY;
            previewFace = 0;
            previewTex = null;
        } else {
            resolvedSet = "";
            resolvedCn = "";
            preview = ItemStack.EMPTY;
            previewTex = null;
        }
        updateWidgetVisibility();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        handleDeckDrop(paths);
    }

    private void openDeckFilePicker() {
        if (filePickerOpen || isImportingDeck() || this.minecraft == null) return;
        var client = this.minecraft;
        filePickerOpen = true;
        updateWidgetVisibility();
        com.spider.mtgcard.Mtgcard.LOGGER.info("[CardStore] Opening deck file picker");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            List<Path> selected = List.of();
            String error = null;
            try {
                selected = DeckFilePicker.open();
                com.spider.mtgcard.Mtgcard.LOGGER.info("[CardStore] File picker selected {} file(s)", selected.size());
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                com.spider.mtgcard.Mtgcard.LOGGER.error("[CardStore] Could not open deck file picker", e);
                error = "Could not open deck picker. See the log for details.";
            }
            List<Path> paths = selected;
            String failure = error;
            client.execute(() -> {
                filePickerOpen = false;
                if (client.gui.screen() != this) return;
                if (failure != null) {
                    importStatus = "";
                    status = failure;
                } else if (!paths.isEmpty()) {
                    handleDeckDrop(paths);
                }
                updateWidgetVisibility();
            });
        });
    }


    public void handleDeckDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        if (isImportingDeck()) {
            importStatus = "A deck is already importing; drop additional files when it finishes.";
            tab = Tab.CART;
            updateWidgetVisibility();
            return;
        }
        importStatus = "";

        ArrayList<CardStorePackets.ImportDeckC2S.Line> all = new ArrayList<>();

        for (Path p : paths) {
            try {
                String name = p.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".txt") || lower.endsWith(".csv"))) continue;

                String text = Files.readString(p, StandardCharsets.UTF_8);
                List<CardStorePackets.ImportDeckC2S.Line> lines =
                        lower.endsWith(".txt") ? parseDeckTxt(text) : parseDeckCsv(text);

                if (!lines.isEmpty()) {
                    all.addAll(lines);
                }
            } catch (Exception e) {
                status = "Import failed for " + p.getFileName() + ": " + e.getMessage();
            }
        }

        if (all.isEmpty()) {
            status = "No cards found. Include a card name or set + collector number.";
            return;
        }

        deckImport = new DeckImportProgress<>(all);
        importGame = selectedGame;
        tab = Tab.CART;
        closeSortMenu();
        closeGameMenu();
        updateWidgetVisibility();
        importNextBatch();
    }

    private static final Component DROP_HINT = Component.literal("Drop .txt or .csv decks to add cards to Cart");

    private void drawDropHint(GuiGraphics ctx) {
        int pad = ui(6);
        int y = topY + ui(6);

        int w = this.font.width(DROP_HINT);
        int right = importDeckBtn == null ? topX + topW : importDeckBtn.getX();
        int x = right - pad - w;
        int tabsRight = topX + ui(CardStoreProviderRegistry.hasMultipleProviders() ? 220 : 144);
        if (x < tabsRight) return; // Keep the header controls usable on compact layouts.

        // subtle shadow plate so it reads on bright backgrounds
        ctx.fill(x - ui(4), y - ui(2), x + w + ui(4), y + ui(10), 0x33202A33);
        ctx.drawString(this.font, DROP_HINT, x, y, TEXT_MUTED, false);
    }

    private List<CardStorePackets.ImportDeckC2S.Line> parseDeckTxt(String text) {
        return importLines(com.spider.mtgcard.cardstore.DeckListParser.parseTxt(text));
    }

    private List<CardStorePackets.ImportDeckC2S.Line> parseDeckCsv(String text) {
        return importLines(com.spider.mtgcard.cardstore.DeckListParser.parseCsv(text));
    }

    private List<CardStorePackets.ImportDeckC2S.Line> importLines(List<com.spider.mtgcard.cardstore.DeckListParser.Entry> entries) {
        return entries.stream().map(e -> new CardStorePackets.ImportDeckC2S.Line(
                selectedGame, e.name(), e.set(), e.collectorNumber(), e.quantity())).toList();
    }

    public void onImportDeckResult(CardStorePackets.ImportDeckS2C p) {
        if (!p.pos().equals(menu.blockPos)) return;
        if (!isImportingDeck() || !importGame.equals(p.game())) return;

        boolean added = p.ok() && !p.entries().isEmpty();
        List<CardStorePackets.ImportDeckS2C.Entry> entries = added ? p.entries() : List.of();
        for (var e : entries) {
            // merge into cart by game/set/cn
            boolean merged = false;
            for (var line : cart) {
                if (line.game.equals(e.game()) && line.set.equals(e.set()) && line.cn.equals(e.cn())) {
                    line.qty += Math.max(1, e.qty());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                ItemStack icon = e.stack().copyWithCount(1);
                cart.add(new CartLine(e.game(), e.set(), e.cn(), icon, e.priceItems(), Math.max(1, e.qty())));
            }
        }

        sortCart();
        deckImport.completeBatch(Math.min(entries.size(), deckImport.currentBatch().size()));
        importNextBatch();
        updateWidgetVisibility();
    }

    @Override
    public void removed() {
        syncCartToServer();
        super.removed();
    }

    private long lastSearchSendMs = 0;

    private boolean canSendSearchNow() {
        long now = System.currentTimeMillis();
        if (now - lastSearchSendMs < 500) return false; // 0.5s debounce
        lastSearchSendMs = now;
        return true;
    }

    private boolean flipPreviewFace() {
        if (!GuiCardFaceFlipper.isDoubleFaced(preview)) return false;
        int faces = Math.max(1, GuiCardFaceFlipper.getFaceCount(preview));
        previewFace = (previewFace + 1) % faces;
        previewTex = null;
        return true;
    }

    @Override
    public boolean mtgcard$flipHoveredCardFace(net.minecraft.client.Minecraft client) {
        return flipPreviewFace();
    }


    @Override
    public boolean keyPressed(KeyEvent key) {
        int code = key.key();

        // Close sort menu on ESC (or inventory key) before anything else
        if (sortMenuOpen) {
            if (code == InputConstants.KEY_ESCAPE || code == InputConstants.KEY_E) {
                closeSortMenu();
                return true;
            }
        }

        // If we're typing in a text field, don't let "E" close the screen.
        if (tab == Tab.STORE && searchField != null && searchField.isFocused()) {

            // Consume inventory key while typing
            if (code == InputConstants.KEY_E) return true;

            // Enter triggers search
            if (code == InputConstants.KEY_RETURN || code == InputConstants.KEY_NUMPADENTER) {

                // ✅ debounce ONLY here (when sending)
                if (!canSendSearchNow()) {
                    status = "Searching… (please wait)";
                    return true;
                }

                String q = searchField.getValue().trim();
                if (q.isBlank()) {
                    status = "Type a search first.";
                    return true;
                }

                lastPrintsQuery = q;

                int page = 1;
                int pageSize = computeGridCapacity();
                lastRequestedPageSize = pageSize;

                UUID req = UUID.randomUUID();
                activePrintsRequest = req;

                // ✅ allow advanced queries again
                ClientPlayNetworking.send(new CardStorePackets.SearchPrintsC2S(menu.blockPos, selectedGame, q, page, pageSize, req));
                status = "Loading page " + page + "…";
                return true;
            }
        }

        // Let vanilla handle everything else (including typing into widgets)
        if (super.keyPressed(key)) return true;

        if (code == InputConstants.KEY_F9) {
            wireframe = !wireframe;
            return true;
        }

        return false;
    }

    // --- cart state (client-side) ---
    private final java.util.ArrayList<CartLine> cart = new java.util.ArrayList<>();
    private int nextCartArrival;

    private class CartLine {
        final int arrival = nextCartArrival++;
        final String game;
        final String set;
        final String cn;
        final ItemStack stack;     // for icon/preview
        final long priceItems;     // per-item cost in configured currency item
        int qty;

        CartLine(String game, String set, String cn, ItemStack stack, long priceItems, int qty) {
            this.game = CardStoreProviderRegistry.sanitizeGameId(game);
            this.set = set;
            this.cn = cn;
            this.stack = stack;
            this.priceItems = priceItems;
            this.qty = qty;
        }

        long lineTotal() {
            return Math.max(0L, priceItems) * Math.max(1, qty);
        }
    }

    private void loadSavedCart(List<CardStoreScreenHandler.CartEntryData> entries) {
        cart.clear();
        selectedCartIndex = -1;
        cartScrollPx = 0;

        if (entries == null || entries.isEmpty()) return;

        for (var entry : entries) {
            if (entry == null || !entry.isValid()) continue;

            boolean merged = false;
            for (var line : cart) {
                if (line.game.equals(entry.game()) && line.set.equals(entry.set()) && line.cn.equals(entry.cn())) {
                    line.qty += Math.max(1, entry.qty());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                cart.add(new CartLine(
                        entry.game(),
                        entry.set(),
                        entry.cn(),
                        entry.stack().copyWithCount(1),
                        entry.priceItems(),
                        Math.max(1, entry.qty())
                ));
            }
        }
        clampCartScroll();
    }

    private boolean hasValidSelection() {
        return selectedIndex >= 0 && selectedIndex < grid.size();
    }

    private void addSelectedToCart() {
        if (!hasValidSelection()) {
            status = "Select a card first.";
            return;
        }

        var sel = grid.get(selectedIndex);

        // safety: set/cn must exist
        if (sel.set == null || sel.set.isBlank() || sel.cn == null || sel.cn.isBlank()) {
            status = "Selection missing set/collector number.";
            return;
        }

        // If same printing already in cart -> increment qty
        for (var line : cart) {
            if (line.game.equals(sel.game) && line.set.equals(sel.set) && line.cn.equals(sel.cn)) {
                line.qty++;
                status = "Added +1 (now " + line.qty + ")";
                return;
            }
        }

        // Add new line
        ItemStack icon = sel.stack.copy();
        icon.setCount(1);

        cart.add(new CartLine(sel.game, sel.set, sel.cn, icon, sel.priceItems, 1));
        sortCart();
        status = "Added to cart (" + cart.size() + " items)";
    }

    private long cartTotal() {
        long t = 0L;
        for (var line : cart) t += line.lineTotal();
        return t;
    }

    public static final int MAX_PAGE_SIZE = 175;

    private void drawCartRows(GuiGraphics ctx, int mouseX, int mouseY) {
        int headerH = cartHeaderH();
        int rowH = cartRowH();
        int qBtn = quantityButtonSize();
        int qGap = quantityButtonGap();
        int thumb = cartThumbSize();
        int thumbPad = cartThumbPad();
        int textPad = cartTextPad();

        int x = cartListX();
        int y = cartListY();
        int w = cartListW();
        int h = cartListH();

        // list background
        ctx.fill(x, y, x + w, y + h, 0x33000000);
        // header background
        int hx0 = x + ui(6);
        int hy0 = cartListY() + ui(2);
        int hx1 = x + w - ui(6);
        int hy1 = hy0 + headerH;


        ctx.fill(hx0, hy0, hx1, hy1, 0x22000000);
        ctx.fill(hx0, hy0, hx1, hy0 + 1, 0x26FFFFFF);
        ctx.fill(hx0, hy1 - 1, hx1, hy1, 0x26000000);


// Header labels: Art | Name | Qty | Set | CN | Price
        int labelY = hy0 + ui(5);
        ctx.drawString(this.font, Component.literal("Art"),  hx0 + ui(6), labelY, 0xFFAAAAAA, false);
        ctx.drawString(this.font, Component.literal("Name"), hx0 + ui(30), labelY, 0xFFAAAAAA, false);

// Right-aligned labels (match your column order)
        String hPrice = "Price";
        String hCn    = "CN";
        String hSet   = "Set";
        String hQty   = "Qty";

        int r = hx1 - ui(6);
        int wPrice = this.font.width(hPrice);
        ctx.drawString(this.font, Component.literal(hPrice), r - wPrice, labelY, 0xFFAAAAAA, false);
        r -= wPrice + ui(10);

        int wCn = this.font.width(hCn);
        ctx.drawString(this.font, Component.literal(hCn), r - wCn, labelY, 0xFFAAAAAA, false);
        r -= wCn + ui(10);

        int wSet = this.font.width(hSet);
        ctx.drawString(this.font, Component.literal(hSet), r - wSet, labelY, 0xFFAAAAAA, false);
        r -= wSet + ui(10);

        int wQty = this.font.width(hQty);
        ctx.drawString(this.font, Component.literal(hQty), r - wQty, labelY, 0xFFAAAAAA, false);

        int hovered = hitTestCartIndex(mouseX, mouseY);

        int yRows = cartRowsY();
        int hRows = cartRowsH();

        // visible range (with scroll)
        int first = Math.max(0, cartScrollPx / rowH);
        int last = Math.min(cart.size(), first + (hRows / rowH) + 2);

        int clipTop = yRows;
        int clipBot = yRows + hRows;

        for (int i = first; i < last; i++) {
            int rowY = yRows + (i * rowH) - cartScrollPx;
            if (rowY + rowH < clipTop || rowY > clipBot) continue;

            boolean sel = (i == selectedCartIndex);
            boolean hov = (i == hovered);

            // row rect (clean like Counters)
            int rx0 = x + ui(6);
            int ry0 = rowY + ui(2);
            int rx1 = x + w - ui(6);
            int ry1 = rowY + rowH - ui(2);

            int base = 0x22000000;
            int hover = 0x2AFFFFFF;
            int selected = 0x55FFFFFF;

            int fill = sel ? selected : (hov ? hover : base);
            ctx.fill(rx0, ry0, rx1, ry1, fill);

            // thin top highlight + bottom shadow (gives “button” feel)
            ctx.fill(rx0, ry0, rx1, ry0 + 1, 0x26FFFFFF);
            ctx.fill(rx0, ry1 - 1, rx1, ry1, 0x26000000);

            CartLine line = cart.get(i);

            // --- thumb (card art) ---
            int thumbX = rx0 + thumbPad;
            int thumbY = ry0 + ((ry1 - ry0) - thumb) / 2;

            // dark plate behind thumb so art always reads
            ctx.fill(thumbX, thumbY, thumbX + thumb, thumbY + thumb, 0x44000000);
            drawCardThumb(ctx, line.stack, thumbX, thumbY, thumb);

            // --- text columns ---
            int textX = thumbX + thumb + textPad;
            int textY = ry0 + ui(6);

            String name  = line.stack.getHoverName().getString();
            String set   = shortSet(line.set).toUpperCase();
            String cnRaw = (line.cn == null ? "" : line.cn);
            String cn    = (cnRaw.length() <= 3) ? cnRaw : cnRaw.substring(0, 3);
            String price = String.valueOf(Math.max(0L, line.priceItems));
            String qty   = "x" + Math.max(1, line.qty);

            // left: name (truncate if needed)
            int rightEdge = rx1 - ui(6);

            // Reserve space for right-side columns: [-][qty pill][+], plus set/cn/price with gaps
            int pw  = this.font.width(price);
            int cnw = this.font.width(cn);
            int sw  = this.font.width(set);
            int qw  = this.font.width(qty);

            int pillW = qw + ui(8);
            int qtyControlsW = qBtn + qGap + pillW + qGap + qBtn;

            // match your draw spacing: after price/cn/set you subtract +10 each time
            int rightColumnsW = qtyControlsW
                    + ui(10) + sw
                    + ui(10) + cnw
                    + ui(10) + pw;

            // how much width the name is allowed to use
            int nameMaxW = Math.max(10, (rightEdge - rightColumnsW) - textX);

            if (this.font.width(name) > nameMaxW) {
                int ellW = this.font.width("…");
                name = this.font.plainSubstrByWidth(name, Math.max(0, nameMaxW - ellW)) + "…";
            }

            ctx.drawString(this.font, Component.literal(name), textX, textY, 0xFFFFFFFF, false);

            // right aligned: [qty] [set] [cn] [price]
            int right = rightEdge;

            // price

            ctx.drawString(this.font, Component.literal(price), right - pw, textY, 0xFFFFFFFF, false);
            right -= pw + ui(10);

            // cn

            ctx.drawString(this.font, Component.literal(cn), right - cnw, textY, 0xFFDDDDDD, false);
            right -= cnw + ui(10);

            // set

            ctx.drawString(this.font, Component.literal(set), right - sw, textY, 0xFFDDDDDD, false);
            right -= sw + ui(10);

            // qty pill (looks nice, optional)
            // qty controls: [-] [x#] [+]
            int pillH = ui(12);
            int pillY = textY + 1;
            int btnY = pillY - 1;

            int plusX  = right - qBtn;
            int pillX  = plusX - qGap - pillW;
            int minusX = pillX - qGap - qBtn;

            // minus button
            ctx.fill(minusX, btnY, minusX + qBtn, btnY + qBtn, 0x22000000);
            ctx.fill(minusX, btnY, minusX + qBtn, btnY + 1, 0x26FFFFFF);
            ctx.fill(minusX, btnY + qBtn - 1, minusX + qBtn, btnY + qBtn, 0x26000000);
            ctx.drawString(this.font, Component.literal("-"), minusX + ui(4), btnY + ui(3), 0xFFFFFFFF, false);

            // qty pill
            ctx.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0x22000000);
            ctx.fill(pillX, pillY, pillX + pillW, pillY + 1, 0x26FFFFFF);
            ctx.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, 0x26000000);
            ctx.drawString(this.font, Component.literal(qty), pillX + ui(4), pillY + ui(2), 0xFFDDDDDD, false);

            // plus button
            ctx.fill(plusX, btnY, plusX + qBtn, btnY + qBtn, 0x22000000);
            ctx.fill(plusX, btnY, plusX + qBtn, btnY + 1, 0x26FFFFFF);
            ctx.fill(plusX, btnY + qBtn - 1, plusX + qBtn, btnY + qBtn, 0x26000000);
            ctx.drawString(this.font, Component.literal("+"), plusX + ui(4), btnY + ui(3), 0xFFFFFFFF, false);

        }
    }

    private static String readMtgMetaString(ItemStack st, String key) {
        if (st == null || st.isEmpty()) return "";
        try {
            var comp = st.get(DataComponents.CUSTOM_DATA);
            if (comp == null) return "";

            var root = comp.copyTag();
            if (root == null) return "";

            // ✅ Optional-aware
            var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);
            if (meta.isEmpty()) return "";

            return meta.getString(key).orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }


    private static String displayNameForCart(ItemStack st) {
        String n = readMtgMetaString(st, "name");
        if (n != null && !n.isBlank()) return n;
        // fallback
        return st.getHoverName().getString();
    }

    private static boolean isAdvancedQuery(String q) {
        if (q == null) return false;
        String s = q.trim();
        if (s.isEmpty()) return false;

        // Hard-trigger chars: you specifically asked for < and > to count even by themselves.
        if (s.indexOf('<') >= 0 || s.indexOf('>') >= 0) return true;

        // Other strong Scryfall syntax characters (safe triggers)
        if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0 || s.indexOf('!') >= 0) return true;

        // Equals is usually a comparator or "field=value" patterns
        if (s.indexOf('=') >= 0) return true;

        // The big one: field syntax like set:neo, c:w, t:dragon, o:"draw a card"
        // \bword:  (word can include underscores for things like "type_line:" etc)
        if (s.matches(".*\\b[a-zA-Z_]+:.*")) return true;

        // IMPORTANT: DO NOT trigger advanced just because '-' exists (too common in names)
        return false;
    }

    private void requestPrintsPage(int page) {
        if (lastPrintsQuery.isBlank()) return;

        int pageSize = computeGridCapacity();
        lastRequestedPageSize = pageSize;

        UUID req = UUID.randomUUID();
        activePrintsRequest = req;

        ClientPlayNetworking.send(new CardStorePackets.SearchPrintsC2S(
                menu.blockPos, selectedGame, lastPrintsQuery, page, pageSize, req
        ));

        status = "Loading page " + page + "…";
    }
}
