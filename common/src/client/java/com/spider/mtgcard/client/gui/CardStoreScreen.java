// src/client/java/com/spider/mtgcard/client/gui/CardStoreScreen.java
package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.api.CardStoreProviderRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.input.GuiCardFaceFlipHandler;
import com.spider.mtgcard.client.input.GuiCardFaceFlipper;
import com.spider.mtgcard.client.java.CardArtManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int LEFT_INNER_PAD = 12;
    private static final int GRID_GAP = 12;
    private static final int PAGER_H = 22;

    private void recomputeLayoutScale() {
        float sx = this.width / (float) DESIGN_W;
        float sy = this.height / (float) DESIGN_H;
        layoutScale = Math.min(MAX_LAYOUT_SCALE, Math.max(1f, Math.min(sx, sy)));
    }

    private int ui(int value) {
        return Math.max(1, Math.round(value * layoutScale));
    }

    private int margin() { return ui(M); }
    private int topBarH() { return ui(TOP_H); }
    private int infoBarH() { return ui(INFO_H); }
    private int layoutGap() { return ui(GAP); }
    private int rightPad() { return ui(RIGHT_PAD); }
    private int rightPanelW() { return INV_W + (rightPad() * 2); }
    private int previewColW() { return ui(PREVIEW_COL_W); }
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
    private int cellSize() { return ui(CELL); }
    private int cellPad() { return ui(CELL_PAD); }
    private int thumbSize() { return ui(THUMB); }
    private int cartThumbSize() { return ui(CART_THUMB); }
    private int cartThumbPad() { return ui(CART_THUMB_PAD); }
    private int cartTextPad() { return ui(CART_TEXT_PAD); }

    private int previewBoxX() { return leftX + leftInnerPad(); }
    private int previewBoxY() { return leftY + leftInnerPad(); }
    private int previewBoxW() { return previewColW(); }
    private int previewBoxH() { return leftH - (leftInnerPad() * 2); }

    private int gridAreaX() { return previewBoxX() + previewBoxW() + gridGap(); }
    private int gridAreaY() { return leftY + leftInnerPad(); }
    private int gridAreaW() { return (leftX + leftW - leftInnerPad()) - gridAreaX(); }
    private int gridAreaH() { return leftH - (leftInnerPad() * 2); }

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

    // --- Sort dropdown state ---
    private boolean sortMenuOpen = false;
    private int sortMenuHover = -1;

    private static final int SORT_BTN_H = 18;
    private static final int SORT_MENU_ITEM_H = 18;
    private static final int SORT_MENU_PAD = 2;

    private int nextArrivalIndex = 0;

    private SortMode sortMode = SortMode.SCRYFALL_ORDER; // default

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

    private int computeGridCapacity() {
        int gridW = gridAreaW() - ui(16);
        int gridH = gridContentH() - ui(8);
        if (gridW <= 0 || gridH <= 0) return 1;

        int cell = cellSize();
        int cols = Math.max(1, gridW / cell);
        int rows = Math.max(1, gridH / cell);

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
        if (sortMode == mode) {
            closeSortMenu();
            return;
        }
        sortMode = mode;
        if (sortBtn != null) sortBtn.setMessage(Component.literal("Sort: " + sortMode.label));
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

        grid.sort((a, b) -> {
            switch (sortMode) {
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
        });

        // keep selection sane after resort
        if (selectedIndex < 0 && !grid.isEmpty()) selectedIndex = 0;
        if (selectedIndex >= grid.size()) selectedIndex = grid.size() - 1;

        if (hasValidSelection()) {
            var sel = grid.get(selectedIndex);
            preview = sel.stack;
            selectedPriceItems = sel.priceItems;
            previewFace = 0;
            previewTex = null;
            resolvedSet = sel.set;
            resolvedCn  = sel.cn;
        }

        if (addToCartBtn != null) addToCartBtn.active = hasValidSelection();
    }




    private int clearBtnY() {
        // same Y as addToCart location: directly above inventory block
        int desiredInvTopY = (rightY + rightH) - rightPad() - INV_H;
        return desiredInvTopY - ui(10) - ui(20);
    }

    // CART list now lives in the LEFT panel's grid box area
    private int cartListX() { return gridAreaX() + ui(8); }
    private int cartListY() { return gridAreaY() + ui(8); }
    private int cartListW() { return gridAreaW() - ui(16); }
    private int cartListH() { return gridAreaH() - ui(16); } // full area (includes header)
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



// remove LEFT_W usage; left becomes "whatever is left"


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




    private static final int CELL = 40;      // cell size
    private static final int CELL_PAD = 4;   // spacing
    private static final int THUMB = 32;     // thumb draw size

    private void drawGrid(GuiGraphics ctx, int mouseX, int mouseY) {
        int gridX = gridAreaX() + ui(8);
        int gridY = gridAreaY() + ui(8);
        int gridW = gridAreaW() - ui(16);
        int gridH = gridContentH() - ui(8);
        int cell = cellSize();
        int cellPad = cellPad();
        int thumb = thumbSize();

        int cols = Math.max(1, gridW / cell);
        int rowsVisible = Math.max(1, gridH / cell);
        int maxVisible = cols * rowsVisible;

        int count = Math.min(grid.size(), maxVisible);

        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;

            int cx = gridX + col * cell;
            int cy = gridY + row * cell;

            int bg = (i == selectedIndex) ? 0x66FFFFFF : 0x33000000;
            ctx.fill(cx, cy, cx + cell - cellPad, cy + cell - cellPad, bg);

            ItemStack s = grid.get(i).stack;
            var tex = CardArtManager.getOrRequestFace(s, 0);

            if (tex != null && tex.id() != null) {
                int texW = tex.texW();
                int texH = tex.texH();

                float aspect = (float) texW / (float) texH;
                int drawW = thumb;
                int drawH = (int) (drawW / aspect);
                if (drawH > thumb) {
                    drawH = thumb;
                    drawW = (int) (drawH * aspect);
                }

                int dx = cx + (cell - drawW) / 2;
                int dy = cy + (cell - drawH) / 2;

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
                ctx.renderItem(s, cx + ui(4), cy + ui(4));
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
        if (applyFixedGuiScale(2)) return;

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
        leftW = (rightX - gap) - leftX;
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
            updateWidgetVisibility();
        }).bounds(cursorX, tabY, tabW, tabH).build());

        // --- NOW that leftX/leftY exist, compute previewBox coords ---
        int sortX = previewBoxX() + ui(6);
        int sortY = previewBoxY() + ui(6);
        int sortW = previewBoxW() - ui(12);

        sortBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Sort: " + sortMode.label),
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

        buyCartBtn.active = !cart.isEmpty() && canAffordCart();
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
            boolean selected = (vals[i] == sortMode);

            if (hovered) ctx.fill(x + 1, iy0, x + w - 1, iy1, 0x33FFFFFF);
            if (selected) ctx.fill(x + 1, iy0, x + w - 1, iy1, 0x2200FFFF);

            ctx.drawString(
                    this.font,
                    Component.literal(vals[i].label),
                    x + ui(6),
                    iy0 + ui(5),
                    0xFFFFFFFF,
                    false
            );
        }
    }

    private void updateWidgetVisibility() {
        boolean store = (tab == Tab.STORE);
        boolean cartTab = (tab == Tab.CART);

        if (sortBtn != null) sortBtn.visible = (tab == Tab.STORE);
        if (flipPreviewBtn != null) {
            flipPreviewBtn.visible = store;
            flipPreviewBtn.active = store && GuiCardFaceFlipper.isDoubleFaced(preview);
        }
        if (tab != Tab.STORE) closeSortMenu();
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

        if (buyCartBtn != null) buyCartBtn.active = cartTab && !cart.isEmpty() && canAffordCart();
        if (clearCartBtn != null) clearCartBtn.active = cartTab && !cart.isEmpty();
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
        int gridX = gridAreaX() + ui(8);
        int gridY = gridAreaY() + ui(8);
        int gridW = gridAreaW() - ui(16);
        int gridH = gridContentH() - ui(8);
        int cell = cellSize();

        if (mx < gridX || my < gridY || mx >= gridX + gridW || my >= gridY + gridH) return -1;

        int cols = Math.max(1, gridW / cell);
        int col = (mx - gridX) / cell;
        int row = (my - gridY) / cell;

        int idx = row * cols + col;
        int maxVisible = cols * Math.max(1, gridH / cell);

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
            var sel = grid.get(0);
            preview = sel.stack;
            selectedPriceItems = sel.priceItems;
            previewFace = 0;
            previewTex = null;
            resolvedSet = sel.set;
            resolvedCn = sel.cn;
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

        if (status != null && !status.isBlank()) {
            ctx.drawString(this.font, Component.literal(status), infoX + ui(6), infoY + ui(3), 0xFFFFFFFF, false);
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
        if (tab == Tab.STORE) {
            renderSortMenu(ctx, mouseX, mouseY);
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

        // If STORE tab, reserve space for sort button at top
        if (tab == Tab.STORE) {
            int sortReserve = sortButtonH() * 2 + ui(8);
            py += sortReserve;
            boxH -= sortReserve;
        }

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


    public void handleDeckDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;

        ArrayList<CardStorePackets.ImportDeckC2S.Line> all = new ArrayList<>();
        int filesUsed = 0;

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
                    filesUsed++;
                }
            } catch (Exception e) {
                status = "Import failed for " + p.getFileName() + ": " + e.getMessage();
            }
        }

        if (all.isEmpty()) {
            status = "No valid lines found (need set + collector number).";
            return;
        }

        ClientPlayNetworking.send(new CardStorePackets.ImportDeckC2S(menu.blockPos, selectedGame, all.size(), all));
        status = "Importing " + all.size() + " lines from " + filesUsed + " file(s)...";
    }

    private static final Component DROP_HINT = Component.literal("Drag in .txt or .csv files");

    private void drawDropHint(GuiGraphics ctx) {
        int pad = ui(6);
        int y = topY + ui(6);

        int w = this.font.width(DROP_HINT);
        int x = (topX + topW) - pad - w;

        // subtle shadow plate so it reads on bright backgrounds
        ctx.fill(x - ui(4), y - ui(2), x + w + ui(4), y + ui(10), 0x33202A33);
        ctx.drawString(this.font, DROP_HINT, x, y, TEXT_MUTED, false);
    }

    private static final Pattern DECK_TXT =
            // 1x Name (set) 217 [Type]
            Pattern.compile("^\\s*(?:(\\d+)\\s*x?\\s+)?(.+?)\\s*\\(([^)]+)\\)\\s*([0-9A-Za-z]+).*?$");

    private List<CardStorePackets.ImportDeckC2S.Line> parseDeckTxt(String text) {
        ArrayList<CardStorePackets.ImportDeckC2S.Line> out = new ArrayList<>();
        if (text == null) return out;

        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#") || line.startsWith("//")) continue;
            if (line.toLowerCase(Locale.ROOT).contains("{nodeck}")) continue;

            Matcher m = DECK_TXT.matcher(line);
            if (!m.matches()) continue;

            int qty = 1;
            String qtyStr = m.group(1);
            if (qtyStr != null && !qtyStr.isBlank()) {
                try { qty = Math.max(1, Integer.parseInt(qtyStr)); } catch (Exception ignored) {}
            }

            String set = m.group(3) != null ? m.group(3).trim() : "";
            String cn  = m.group(4) != null ? m.group(4).trim() : "";

            if (set.isBlank() || cn.isBlank()) continue;

            out.add(new CardStorePackets.ImportDeckC2S.Line(selectedGame, set, cn, qty));
        }
        return out;
    }

    /**
     * CSV support:
     * - If headers exist, try to find qty/name/set/cn columns.
     * - Otherwise assume your sheet-style layout:
     *     A=qty, B=name, C=set, I=collectorNumber (index 8)
     */
    private List<CardStorePackets.ImportDeckC2S.Line> parseDeckCsv(String csv) {
        ArrayList<CardStorePackets.ImportDeckC2S.Line> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;

        List<List<String>> rows = parseCsvRows(csv);
        if (rows.isEmpty()) return out;

        int startRow = 0;

        // header detection
        int qtyCol = -1, setCol = -1, cnCol = -1;
        List<String> first = rows.get(0);
        if (looksLikeHeader(first)) {
            for (int i = 0; i < first.size(); i++) {
                String h = first.get(i).trim().toLowerCase(Locale.ROOT);
                if (h.equals("qty") || h.equals("quantity") || h.equals("count")) qtyCol = i;
                if (h.equals("set") || h.equals("setcode") || h.equals("set_code")) setCol = i;
                if (h.equals("cn") || h.equals("collector") || h.equals("collector_number") || h.equals("collectornumber")) cnCol = i;
            }
            startRow = 1;
        }

        for (int r = startRow; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.isEmpty()) continue;

            int qty = 1;
            String set = "";
            String cn = "";

            if (qtyCol >= 0 && qtyCol < row.size()) qty = parseIntOrOne(row.get(qtyCol));
            if (setCol >= 0 && setCol < row.size()) set = safe(row.get(setCol));
            if (cnCol  >= 0 && cnCol  < row.size()) cn  = safe(row.get(cnCol));

            // fallback to your spreadsheet layout if no header mapping worked
            if (set.isBlank()) {
                if (row.size() > 3) set = safe(row.get(3)); // D = set code
                else if (row.size() > 2) set = safe(row.get(2)); // legacy
            }// C
            if (cn.isBlank()) {
                if (row.size() > 8) cn = safe(row.get(8));                      // I
                else if (row.size() > 3) cn = safe(row.get(3));                 // sometimes D
            }
            if (qtyCol < 0 && row.size() > 0) qty = parseIntOrOne(row.get(0));  // A

            if (set.isBlank() || cn.isBlank()) continue;

            out.add(new CardStorePackets.ImportDeckC2S.Line(selectedGame, set, cn, Math.max(1, qty)));
        }

        return out;
    }

    public void onImportDeckResult(CardStorePackets.ImportDeckS2C p) {
        if (!p.pos().equals(menu.blockPos)) return;
        if (!sameGame(p.game())) return;

        status = p.message();

        if (!p.ok() || p.entries() == null || p.entries().isEmpty()) return;

        for (var e : p.entries()) {
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

        clampCartScroll();
        updateWidgetVisibility();
    }

    @Override
    public void removed() {
        syncCartToServer();
        super.removed();
    }

    private static int parseIntOrOne(String s) {
        try { return Math.max(1, Integer.parseInt(s.trim())); }
        catch (Exception ignored) { return 1; }
    }

    private long lastSearchSendMs = 0;

    private boolean canSendSearchNow() {
        long now = System.currentTimeMillis();
        if (now - lastSearchSendMs < 500) return false; // 0.5s debounce
        lastSearchSendMs = now;
        return true;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean looksLikeHeader(List<String> row) {
        if (row == null || row.isEmpty()) return false;
        String joined = String.join(",", row).toLowerCase(Locale.ROOT);
        return joined.contains("qty") || joined.contains("quantity") || joined.contains("set") || joined.contains("collector");
    }

    /** Simple CSV parser (handles quotes). */
    private static List<List<String>> parseCsvRows(String csv) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> curRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"'); // escaped quote
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (!inQuotes && (c == ',')) {
                curRow.add(field.toString());
                field.setLength(0);
                continue;
            }

            if (!inQuotes && (c == '\n' || c == '\r')) {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                curRow.add(field.toString());
                field.setLength(0);
                // skip empty trailing row
                if (!(curRow.size() == 1 && curRow.get(0).isBlank())) rows.add(curRow);
                curRow = new ArrayList<>();
                continue;
            }

            field.append(c);
        }

        // last field/row
        curRow.add(field.toString());
        if (!(curRow.size() == 1 && curRow.get(0).isBlank())) rows.add(curRow);

        return rows;
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
            if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_E) {
                closeSortMenu();
                return true;
            }
        }

        // If we're typing in a text field, don't let "E" close the screen.
        if (tab == Tab.STORE && searchField != null && searchField.isFocused()) {

            // Consume inventory key while typing
            if (code == GLFW.GLFW_KEY_E) return true;

            // Enter triggers search
            if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER) {

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

        if (code == GLFW.GLFW_KEY_F9) {
            wireframe = !wireframe;
            return true;
        }

        return false;
    }

    // --- cart state (client-side) ---
    private final java.util.ArrayList<CartLine> cart = new java.util.ArrayList<>();

    private static class CartLine {
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
