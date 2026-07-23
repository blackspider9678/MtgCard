package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.compat.MtgGuiScaleHelper;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.net.DBClientPackets;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;


public class CardDatabaseScreen extends LegacyContainerScreen<CardDatabaseScreenHandler> {

    private static final Identifier DB_BG  = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card_database.png");
    private static final Identifier DBX_BG = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/deckbox_cb.png");

    private static final int DB_W  = 194;
    private static final int DB_H  = 252;
    private static final String CLEAR_GLYPH = "\u00D7";
    private static final String STORE_ALL_GLYPH = "\u21EA";

    private static final int DBX_W = 212;
    private static final int DBX_H = 238;

    private static final int GAP_W = 14;

    private String lastQuery = "";

    // Absolute-offset button id base (must match handler)
    private static final int SET_OFFSET_BASE = CardDatabaseScreenHandler.SET_OFFSET_BASE;

    // --- Search / sort state ---
    private String orderKey = "name";
    private boolean dirAsc = true;

    // --- Search / sort widgets ---
    private EditBox search;
    private Button btnClear;

    // --- Scrollbar geometry/state ---
    private int trackX, trackY, trackH;   // track rect (2 px wide visual, but we draw 4 for easier hit)
    private boolean dragging = false;
    private int dragGrabDy = 0;           // mouseY - thumbY when click begins
    private int lastSentOffset = -1;      // avoid spamming identical offsets

    // --- Auto-search debounce ---
    private static final long SEARCH_DEBOUNCE_MS = 250;
    private long nextSearchAtMs = -1;
    private String lastSentQuery = "";

    // --- Preview state (same as Deckbox) ---
    private long hoverSinceMs = 0L;
    private static final long HOVER_DEBOUNCE_MS = 80;

    private ItemStack lastHoverStack = ItemStack.EMPTY;
    private int lastHoverFace = 0;
    private CardArtManager.TextureRef lastTexRef = null;

    // Animation
    private long animStartMs = 0L;
    private static final int ANIM_MS = 180;     // duration in ms
    private static final float ANIM_SCALE_FROM = 0.92f;

    private int rightPanelX() { return this.leftPos + DB_W + GAP_W; }
    private int rightPanelY() { return this.topPos; }

    private Button btnStoreAll;
    private Button btnGame;
    private Button btnRoute;

    private boolean suppressSearchListener = false;


    private java.util.List<String> tabNames = java.util.List.of();
    private java.util.List<Button> tabButtons = new java.util.ArrayList<>();

    // Sort dropdown
    private Button btnSort;
    private boolean sortMenuOpen = false;
    private final java.util.List<Button> sortMenuButtons = new java.util.ArrayList<>();

    private boolean gameMenuOpen = false;
    private final java.util.List<Button> gameMenuButtons = new java.util.ArrayList<>();

    private static final SortOpt[] SORT_OPTS = {
            new SortOpt("Name",       "name"),
            new SortOpt("Mana Value", "mv"),
            new SortOpt("Price",      "price"),
            new SortOpt("Type",       "type"),
            new SortOpt("Power",      "power"),
            new SortOpt("Toughness",  "toughness"),
            new SortOpt("Rarity",     "rarity")
    };

    private record SortOpt(String label, String key) {}

    public void applyDeckboxTabNames(int syncId, java.util.List<String> entries) {
        if (this.menu == null) return;
        if (syncId != this.menu.containerId) return; // make sure itâ€™s for this screen
        this.tabNames = (entries == null) ? java.util.List.of() : java.util.List.copyOf(entries);
    }

    private CardDatabaseScreenHandler H() { return (CardDatabaseScreenHandler) this.menu; }

    public CardDatabaseScreen(CardDatabaseScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title, initialImageWidth(handler), DB_H);
    }

    private static int initialImageWidth(CardDatabaseScreenHandler handler) {
        return handler.getClientDeckboxCount() > 0 ? (DB_W + GAP_W + DBX_W) : DB_W;
    }

    private void sendSearchToServer(boolean rememberSort) {
        if (this.minecraft == null) return;
        String q   = (this.search == null) ? "" : this.search.getValue();
        String dir = (dirAsc ? "asc" : "desc");
        DBClientPackets.sendSearch(q, orderKey, dir, rememberSort);
    }

    private void scheduleAutoSearch() {
        this.nextSearchAtMs = System.currentTimeMillis() + SEARCH_DEBOUNCE_MS;
    }

    private void maybeFireAutoSearch() {
        if (this.nextSearchAtMs < 0) return;
        long now = System.currentTimeMillis();
        if (now >= this.nextSearchAtMs) {
            this.nextSearchAtMs = -1;
            String q = (this.search == null) ? "" : this.search.getValue();
            if (!q.equals(lastSentQuery)) {
                lastSentQuery = q;
                triggerSearchFromUI(false);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        maybeFireAutoSearch();
        syncClientSortState();
        syncClientGameState();

        if (btnRoute != null) {
            boolean hasDeckbox = H().getClientDeckboxCount() > 0;
            btnRoute.active = hasDeckbox;
            btnRoute.setMessage(Component.literal(routeLabel()));
        }
    }

    @Override
    protected void init() {
        boolean hasDeckbox = (this.menu.getClientDeckboxCount() > 0);

        if (applyAutoFitGuiScaleWithSidePreview(this.imageWidth, this.imageHeight, 180, Math.min(this.imageHeight - 8, 220))) return;

        super.init();

        // If a dropdown is open and we re-init (resize), close it
        closeSortMenu();
        closeGameMenu();

        // ----------------------------
        // Scrollbar geometry (left panel)
        // ----------------------------
        this.trackX = this.leftPos + DB_W - 6;
        this.trackY = this.topPos + CardDatabaseScreenHandler.DB_GRID_Y; // 36
        this.trackH = 6 * 18;

        // ----------------------------
        // Header: Search + Clear
        // ----------------------------
        final int headerY = this.topPos + 16;
        final int fieldH  = 14;

        final int padL = 8;
        final int padR = 8;

        final int clearW = 16;

        // Search field starts after the left gutter area (18) + a little breathing room
        final int leftX  = this.leftPos + padL + CardDatabaseScreenHandler.DB_LEFT_GUTTER;
        final int rightX = this.leftPos + DB_W - padR;

        final int clearX  = rightX - clearW;
        final int searchW = Math.max(60, clearX - 2 - leftX);

        this.search = new EditBox(this.font, leftX, headerY, searchW, fieldH, Component.literal(""));
        this.search.setBordered(true);
        this.search.setEditable(true);
        this.search.setMaxLength(512);
        this.search.setHint(Component.literal("Scryfall syntax"));
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        this.search.setResponder(s -> {
            if (suppressSearchListener) return;
            lastQuery = s;
            scheduleAutoSearch();
        });

        this.btnClear = Button.builder(Component.literal("Ã—"), b -> {
            this.search.setValue("");
            this.search.setFocused(true);
            lastSentQuery = "";
            triggerSearchFromUI(false);
        }).bounds(clearX, headerY, clearW, fieldH).build();
        this.addRenderableWidget(this.btnClear);
        this.btnClear.setMessage(Component.literal(CLEAR_GLYPH));

        // ----------------------------
        // Left gutter buttons (your requested stack)
        // Top button: x+5, y+33, then +18 each
        // ----------------------------
        final int gx = this.leftPos + 5;
        final int gy = this.topPos + 35;
        final int step = 18;
        int row = 0;

        // Store All
        this.btnStoreAll = addGutterButton(gx, gy + (row++ * step), "â‡ª", "Store all cards into DB", () -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CardDatabaseScreenHandler.BTN_STORE_ALL);
            }
        });
        this.btnStoreAll.setMessage(Component.literal(STORE_ALL_GLYPH));

        // Game filter dropdown, shown only when an addon registers another game.
        this.btnGame = null;
        if (TcgGameRegistry.hasMultipleGames()) {
            this.btnGame = addGutterButton(gx, gy + (row++ * step), TcgGameRegistry.shortLabel(H().getClientGameFilter()), "Card game filter", this::toggleGameMenu);
        }

        // Sort dropdown
        this.btnSort = addGutterButton(gx, gy + (row++ * step), "S", "Sort options", this::toggleSortMenu);

        // Route toggle
        this.btnRoute = addGutterButton(gx, gy + (row++ * step), routeLabel(), "Shift-click destination (P/D)", () -> {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CardDatabaseScreenHandler.BTN_TOGGLE_ROUTE);
            }
        });
        this.btnRoute.active = hasDeckbox;


        // ----------------------------
        // Deckbox tab buttons (only if >1 deckbox)
        // ----------------------------
        tabButtons.clear();
        int deckboxCount = H().getClientDeckboxCount();
        if (deckboxCount > 1) {
            int panelLeft = this.leftPos + DB_W + GAP_W;
            int tabY = this.topPos + 6;     // inside deckbox header strip
            int tabX = panelLeft + 8;

            for (int i = 0; i < deckboxCount; i++) {
                int idx = i;
                Button b = Button.builder(Component.literal(String.valueOf(i + 1)), btn -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                                CardDatabaseScreenHandler.TAB_BASE + idx);
                    }
                }).bounds(tabX + i * 20, tabY, 18, 14).build();

                tabButtons.add(b);
                this.addRenderableWidget(b);
            }
        }

        if (this.menu.slots.size() > 54) {
            Slot firstPlayerSlot = this.menu.slots.get(54);
            this.inventoryLabelX = firstPlayerSlot.x;
            this.inventoryLabelY = firstPlayerSlot.y - 12;
        } else {
            this.inventoryLabelX = CardDatabaseScreenHandler.DB_GRID_X;
            this.inventoryLabelY = this.imageHeight - 94;
        }

        syncClientSortState();
        syncClientGameState();
    }


    private String routeLabel() {
        return (H().getClientRouteMode() == 1) ? "D" : "P";
    }

    private void syncClientSortState() {
        this.orderKey = H().getClientSortOrder();
        this.dirAsc = H().isClientSortAscending();
    }

    private void syncClientGameState() {
        if (btnGame != null) {
            btnGame.setMessage(Component.literal(TcgGameRegistry.shortLabel(H().getClientGameFilter())));
        }
    }

    private int getScrollContentCount() {
        int searchTotal = H().getLastSearchTotal();
        return (searchTotal >= 0) ? searchTotal : H().getClientIntakeCount();
    }

    private int getScrollRowCount() {
        return (int) Math.ceil(Math.max(0, getScrollContentCount()) / 9.0);
    }

    private int getScrollMaxRows() {
        return Math.max(0, getScrollRowCount() - 6);
    }

    private int getCurrentScrollRow() {
        return Math.max(0, H().getClientWindowOffset() / 9);
    }

    private void sendAbsoluteRowOffset(int rowIndex) {
        sendAbsoluteOffset(Math.max(0, rowIndex) * 9);
    }

    private boolean isOverLeftDbPanel(double mx, double my) {
        return mx >= this.leftPos
                && mx < this.leftPos + DB_W
                && my >= this.topPos
                && my < this.topPos + DB_H;
    }

    private boolean isOverDbGrid(double mx, double my) {
        int x0 = this.leftPos + CardDatabaseScreenHandler.DB_GRID_X;
        int y0 = this.topPos + CardDatabaseScreenHandler.DB_GRID_Y;
        int w = CardDatabaseScreenHandler.DB_COLS * CardDatabaseScreenHandler.SLOT_SIZE;
        int h = CardDatabaseScreenHandler.DB_ROWS * CardDatabaseScreenHandler.SLOT_SIZE;
        return mx >= x0 && mx < x0 + w && my >= y0 && my < y0 + h;
    }

    private int dbGridSlotAt(double mx, double my) {
        if (!isOverDbGrid(mx, my)) return -1;

        int x0 = this.leftPos + CardDatabaseScreenHandler.DB_GRID_X;
        int y0 = this.topPos + CardDatabaseScreenHandler.DB_GRID_Y;
        int col = (int) ((mx - x0) / CardDatabaseScreenHandler.SLOT_SIZE);
        int row = (int) ((my - y0) / CardDatabaseScreenHandler.SLOT_SIZE);
        if (col < 0 || col >= CardDatabaseScreenHandler.DB_COLS || row < 0 || row >= CardDatabaseScreenHandler.DB_ROWS) {
            return -1;
        }
        return row * CardDatabaseScreenHandler.DB_COLS + col;
    }

    private boolean sendDbGridAction(int slot, int action) {
        if (slot < 0 || slot >= CardDatabaseScreenHandler.DB_ROWS * CardDatabaseScreenHandler.DB_COLS) return false;
        if (this.minecraft == null || this.minecraft.gameMode == null) return false;

        int id = CardDatabaseScreenHandler.DB_INTERACT_BASE + slot * 8 + action;
        if (!DBClientPackets.sendGridAction(this.menu.containerId, slot, action)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
        return true;
    }

    private boolean shiftDown() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return false;
        long handle = this.minecraft.getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void triggerSearchFromUI(boolean rememberSort) {
        sendSearchToServer(rememberSort);
    }

    // ---------- Input ----------
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        boolean overDbGrid = isOverDbGrid(mx, my);
        var scrollbar = intakeScrollbar();
        boolean overScrollbar =
                scrollbar != null &&
                (MtgGuiChrome.ptInExpanded(scrollbar.thumb(), mx, my, 1, 0)
                        || MtgGuiChrome.ptInExpanded(scrollbar.track(), mx, my, 1, 0));

        if (!overDbGrid && !isOverLeftDbPanel(mx, my) && !overScrollbar) return false;

        int id = (vy < 0) ? CardDatabaseScreenHandler.SCROLL_ROW_DOWN
                : CardDatabaseScreenHandler.SCROLL_ROW_UP;
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isSimulated) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (sortMenuOpen) {
            // If click is NOT on the sort button or any dropdown option, close it
            boolean overSort = (btnSort != null && btnSort.isMouseOver(click.x(), click.y()));
            boolean overAny = false;
            for (var b : sortMenuButtons) {
                if (b != null && b.isMouseOver(click.x(), click.y())) { overAny = true; break; }
            }
            if (!overSort && !overAny) {
                closeSortMenu();
            }
        }

        if (gameMenuOpen) {
            boolean overGame = (btnGame != null && btnGame.isMouseOver(click.x(), click.y()));
            boolean overAny = false;
            for (var b : gameMenuButtons) {
                if (b != null && b.isMouseOver(click.x(), click.y())) { overAny = true; break; }
            }
            if (!overGame && !overAny) {
                closeGameMenu();
            }
        }

        if (button == 0) {
            var scrollbar = intakeScrollbar();
            if (scrollbar != null && MtgGuiChrome.ptInExpanded(scrollbar.thumb(), mouseX, mouseY, 1, 0)) {
                dragging = true;
                dragGrabDy = (int) mouseY - scrollbar.thumb().y();
                return true;
            }
            if (scrollbar != null && MtgGuiChrome.ptInExpanded(scrollbar.track(), mouseX, mouseY, 1, 0)) {
                int cur = getCurrentScrollRow();
                int max = scrollbar.maxScroll();
                if (max > 0) {
                    int page = 6;
                    int next = (mouseY < scrollbar.thumb().y()) ? cur - page : cur + page;
                    sendAbsoluteRowOffset(Math.max(0, Math.min(max, next)));
                }
                return true;
            }
        }
        var scrollbar = intakeScrollbar();
        if (scrollbar != null && MtgGuiChrome.ptInExpanded(scrollbar.track(), mouseX, mouseY, 1, 0)) {
            int max = scrollbar.maxScroll();
            if (max > 0) {
                int bottom = scrollbar.track().y() + scrollbar.track().h() - scrollbar.thumb().h();
                if (mouseY >= bottom - 1) {
                    sendAbsoluteRowOffset(max);
                } else {
                    int cur = getCurrentScrollRow();
                    int page = 6;
                    int next = (mouseY < scrollbar.thumb().y()) ? cur - page : cur + page;
                    next = Math.max(0, Math.min(max, next));
                    sendAbsoluteRowOffset(next);
                }
            }
            return true;
        }
        int gridSlot = dbGridSlotAt(mouseX, mouseY);
        if (gridSlot >= 0 && (button == 0 || button == 1)) {
            if (isSimulated) {
                return true;
            }

            int action;
            if (button == 1) {
                action = shiftDown()
                        ? CardDatabaseScreenHandler.DB_INTERACT_SHIFT_RIGHT
                        : CardDatabaseScreenHandler.DB_INTERACT_RIGHT;
            } else {
                action = shiftDown()
                        ? CardDatabaseScreenHandler.DB_INTERACT_SHIFT_LEFT
                        : CardDatabaseScreenHandler.DB_INTERACT_LEFT;
            }
            return sendDbGridAction(gridSlot, action);
        }
        return super.mouseClicked(click, isSimulated);
    }

    private void drawBottomPadding(GuiGraphics ctx) {
        final int total = getScrollContentCount();
        final int offset = H().getClientWindowOffset();
        final int pageSize = 54;   // 6x9 window
        final int cols = 9;
        final int rows = 6;

        final int maxOffset = getScrollMaxRows() * 9;
        if (offset != maxOffset) return;

        final int remaining = Math.max(0, total - offset);
        final int cellsOnPage = Math.min(pageSize, remaining);
        if (cellsOnPage >= pageSize) return;

        final int fullRows = cellsOnPage / cols;
        final int lastRowCells = cellsOnPage % cols;

        final int x0 = this.leftPos + CardDatabaseScreenHandler.DB_GRID_X; // 26
        final int y0 = this.topPos + CardDatabaseScreenHandler.DB_GRID_Y; // 36
        final int cell = 18;
        final int inner = 16;

        final int padColor = 0x11FFFFFF;

        if (lastRowCells > 0) {
            int rowY = y0 + (fullRows * cell);
            for (int c = lastRowCells; c < cols; c++) {
                int sx = x0 + c * cell + 1;
                int sy = rowY + 1;
                ctx.fill(sx, sy, sx + inner, sy + inner, padColor);
            }
        }

        int usedRows = fullRows + (lastRowCells > 0 ? 1 : 0);
        for (int r = usedRows; r < rows; r++) {
            int rowY = y0 + r * cell;
            for (int c = 0; c < cols; c++) {
                int sx = x0 + c * cell + 1;
                int sy = rowY + 1;
                ctx.fill(sx, sy, sx + inner, sy + inner, padColor);
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (dragging && click.button() == 0) {
            var scrollbar = intakeScrollbar();
            if (scrollbar == null) return true;

            if (scrollbar.maxScroll() > 0) {
                int targetRow = MtgGuiChrome.scrollFromThumb(scrollbar, (int) click.y(), dragGrabDy);
                int targetOffset = targetRow * 9;
                if (targetOffset != lastSentOffset) {
                    sendAbsoluteOffset(targetOffset);
                }
            }
            return true;
        }
        if (isOverDbGrid(click.x(), click.y())) {
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        if (isOverDbGrid(click.x(), click.y())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    public void applyResults(Object ignored) { }

    @Override
    public boolean keyPressed(KeyEvent key) {
        // If the search box is focused, swallow the Inventory keybind (E) so the GUI doesn't close.
        if (this.search != null && this.search.isFocused() && this.minecraft != null) {
            var invKey = this.minecraft.options.keyInventory;
            if (invKey != null && invKey.matches(key)) {
                return true; // consume E (or whatever the inventory key is)
            }
        }

        // Optional: Enter triggers search manually (auto-search still works)
        int code = key.input();
        if ((code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER)
                && this.search != null && this.search.isFocused()) {
            triggerSearchFromUI(false);
            return true;
        }

        return super.keyPressed(key);
    }

    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        // Yarn exposes either public fields x/y or getters getX()/getY() depending on version.
        int sx, sy;
        try {
            // most mappings: public fields
            sx = this.leftPos + slot.x;
            sy = this.topPos + slot.y;
        } catch (Throwable ignore) {
            // if your mappings use getters, uncomment these two lines and delete the try/catch:
            // sx = this.x + slot.getX();
            // sy = this.y + slot.getY();
            sx = this.leftPos; sy = this.topPos; // placeholder to keep compile if you switch to getters
        }
        // 16x16 slot box (same as vanilla)
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    /** Matches Deckbox foil/glint check so previews shimmer the same way. */
    private boolean isFoil(ItemStack st) {
        return com.spider.mtgcard.client.render.CardFoilUtil.isFoil(st);
    }

    private void renderHoverPreview(GuiGraphics ctx, int mouseX, int mouseY, float delta) {

        // Which slot are we hovering?
        var slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem() || !isMouseOverSlotArea(slot, mouseX, mouseY)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        ItemStack st = slot.getItem();
        if (!st.is(com.spider.mtgcard.item.ModItemTags.TCG_CARD)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        // Debounce hover so we don't spam loads while sweeping the mouse
        long now = System.currentTimeMillis();
        if (!ItemStack.matches(st, lastHoverStack)) {
            hoverSinceMs = now;
            lastHoverStack = st.copy();
            lastTexRef = null;
            animStartMs = now; // â† start anim on new card
        }
        if (now - hoverSinceMs < HOVER_DEBOUNCE_MS) return;

        // Request texture (async if needed)
        int face = DeckboxScreen.readFaceIndex(st);
        if (face != lastHoverFace) {
            lastHoverFace = face;
            lastTexRef = null;
            animStartMs = now; // â† start anim on face change
        }

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(st, face);
        if (ref == null || ref.id() == null) return; // not ready yet
        lastTexRef = ref;

        final int panelMaxH = Math.min(this.imageHeight - 8, 220);
        final int panelMaxW = 180;

        // Fit card into panel while preserving aspect
        final int texW = ref.texW();
        final int texH = ref.texH();
        float aspect = (float) texW / (float) texH;

        int panelW = panelMaxW;
        int panelH = panelMaxH;
        int panelX = this.leftPos - (panelW + MtgGuiScaleHelper.SIDE_PREVIEW_GAP);
        int panelY = this.topPos + 4;
        if (panelX < MtgGuiScaleHelper.SIDE_PREVIEW_MARGIN) {
            panelX = this.leftPos + this.imageWidth + MtgGuiScaleHelper.SIDE_PREVIEW_GAP;
        }

        int drawW = panelW;
        int drawH = (int) (drawW / aspect);
        if (drawH > panelH) {
            drawH = panelH;
            drawW = (int) (drawH * aspect);
        }

        int x = panelX + (panelW - drawW) / 2;
        int y = panelY + (panelH - drawH) / 2;

        // --- Animation params ---
        long nowMs = System.currentTimeMillis();
        float t = Math.max(0f, Math.min(1f, (nowMs - animStartMs) / (float) ANIM_MS));
        // Smoothstep easing
        float ease = t * t * (3f - 2f * t);

        // Scale: 92% -> 100%
        float scaleAnim = ANIM_SCALE_FROM + (1f - ANIM_SCALE_FROM) * ease;

        // Tilt: ~2.5Â° -> 0Â°
        float angleDeg = (1f - ease) * 2.5f;

        // Fade: 40% -> 100% alpha
        int alphaMain = (int) (255f * (0.40f + 0.60f * ease));
        int colorMain = (alphaMain << 24) | 0x00FFFFFF; // ARGB tint for main draw
        int colorShadow = 0x55000000;                    // soft black shadow

        // Precompute scales from texture â†’ target draw size
        float sx = (float) drawW / (float) texW;
        float sy = (float) drawH / (float) texH;

        // Save matrix
        var m = ctx.pose();
        float m00 = m.m00(), m01 = m.m01();
        float m10 = m.m10(), m11 = m.m11();
        float m20 = m.m20(), m21 = m.m21();

        // -------- 1) DROP SHADOW pass (slightly larger + offset) --------
        m.translate((float) x, (float) y);
        m.scale(sx * 1.02f * scaleAnim, sy * 1.02f * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.rotate((float) Math.toRadians(angleDeg));
        m.translate(-texW / 2f, -texH / 2f);
        m.translate(2f, 3f);

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                0, 0, 0f, 0f,
                texW, texH, texW, texH,
                colorShadow
        );

        // -------- 2) MAIN CARD pass --------
        m.set(m00, m01, m10, m11, m20, m21);
        m.translate((float) x, (float) y);
        m.scale(sx * scaleAnim, sy * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.rotate((float) Math.toRadians(angleDeg));
        m.translate(-texW / 2f, -texH / 2f);

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                0, 0, 0f, 0f,
                texW, texH, texW, texH,
                colorMain
        );

        // -------- 3) FOIL SHIMMER pass (overlay) --------
        if (isFoil(st)) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(nowMs, texW);
            if (sweep != null) {
                // Re-apply the main transform
                m.set(m00, m01, m10, m11, m20, m21);
                m.translate((float) x, (float) y);
                m.scale(sx * scaleAnim, sy * scaleAnim);
                m.translate(texW / 2f, texH / 2f);
                m.rotate((float) Math.toRadians(angleDeg));
                m.translate(-texW / 2f, -texH / 2f);

                ctx.blit(
                        RenderPipelines.GUI_TEXTURED,
                        ref.id(),
                        sweep.drawU(), 0,
                        (float) sweep.drawU(), 0f,
                        sweep.clipW(), texH,
                        texW, texH,
                        com.spider.mtgcard.client.render.CardFoilUtil.guiShimmerColor(ease)
                );
            }
        }

        // Restore matrix
        m.set(m00, m01, m10, m11, m20, m21);
    }

    // ---------- Drawing ----------
    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {

        // LEFT: card database texture (512 atlas)
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                DB_BG,
                this.leftPos, this.topPos,
                0, 0,
                DB_W, DB_H,
                512, 512
        );

        if (H().getClientDeckboxCount() > 0) {
            int px = rightPanelX();
            int py = rightPanelY();
            int tab = H().getClientActiveDeckboxTab();
            String title = "Deckbox " + (tab + 1) + "/" + H().getClientDeckboxCount();
            ctx.drawString(this.font, title, px + 52, py + 9, 0xFFFFFFFF, false);
        }

        // scrollbar geometry (left panel)
        this.trackX = this.leftPos + DB_W - 6;
        this.trackY = this.topPos + CardDatabaseScreenHandler.DB_GRID_Y;
        this.trackH = 6 * 18;

        var scrollbar = intakeScrollbar();
        if (scrollbar != null && scrollbar.enabled()) {
            MtgGuiChrome.drawScrollbar(ctx, scrollbar, 0xFF1E1E1E, 0xFFBFBFBF, 0xFF1E1E1E);
        }

        drawBottomPadding(ctx);

        // RIGHT: deckbox texture (also 512 atlas)
        if (H().getClientDeckboxCount() > 0) {
            int panelLeft = this.leftPos + DB_W + GAP_W;

            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    DBX_BG,
                    panelLeft, this.topPos,
                    0, 0,
                    DBX_W, DBX_H,
                    512, 512
            );
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        renderDeckboxTabTooltip(ctx, mouseX, mouseY);
        this.renderTooltip(ctx, mouseX, mouseY);

        // Hover preview
        renderHoverPreview(ctx, mouseX, mouseY, delta);
    }

    private void renderDeckboxTabTooltip(GuiGraphics ctx, int mouseX, int mouseY) {
        if (tabButtons == null || tabButtons.isEmpty()) return;
        if (tabNames == null || tabNames.isEmpty()) return;

        for (int i = 0; i < tabButtons.size(); i++) {
            var b = tabButtons.get(i);
            if (b == null) continue;

            // ButtonWidget has isMouseOver(double,double) in modern mappings
            if (!b.isMouseOver(mouseX, mouseY)) continue;

            int nameIndex = i * 2;
            if (nameIndex >= tabNames.size()) return;

            java.util.List<Component> lines = new java.util.ArrayList<>();
            String commander = tabNames.get(nameIndex);
            String partner = nameIndex + 1 < tabNames.size() ? tabNames.get(nameIndex + 1) : "";
            if (commander != null && !commander.isBlank()) {
                lines.add(Component.literal("Commander: ").append(Component.literal(commander)));
            }
            if (partner != null && !partner.isBlank()) {
                lines.add(Component.literal("Partner: ").append(Component.literal(partner)));
            }
            if (lines.isEmpty()) lines.add(Component.literal("Empty"));

            ctx.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    // ---------- Helpers ----------
    private MtgGuiChrome.Scrollbar intakeScrollbar() {
        int totalRows = Math.max(1, getScrollRowCount());
        return MtgGuiChrome.layoutScrollbar(
                new MtgGuiChrome.Rect(trackX - 1, trackY, 4, trackH),
                totalRows,
                6,
                getCurrentScrollRow(),
                12
        );
    }

    private void sendAbsoluteOffset(int offset) {
        lastSentOffset = offset;
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SET_OFFSET_BASE + offset);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    /** Ensure exactly one token key:<value> exists in the search box; update or append. */
    private void rewriteTokenInQuery(String key, String value) {
        String raw = search.getValue();
        java.util.List<String> toks = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([^\"]+)\"|(\\S+)").matcher(raw == null ? "" : raw);

        boolean replaced = false;
        while (m.find()) {
            String tok = (m.group(1) != null) ? m.group(1) : m.group(2);
            if (tok == null || tok.isBlank()) continue;

            String lower = tok.toLowerCase(java.util.Locale.ROOT);
            if (lower.startsWith(key.toLowerCase(java.util.Locale.ROOT) + ":")) {
                toks.add(key + ":" + value);
                replaced = true;
            } else {
                toks.add(tok);
            }
        }
        if (!replaced) toks.add(key + ":" + value);

        String rebuilt = rebuildQuery(toks);
        search.setValue(rebuilt);
    }

    private static String rebuildQuery(java.util.List<String> toks) {
        java.util.List<String> out = new java.util.ArrayList<>(toks.size());
        for (String t : toks) {
            if (t.indexOf(' ') >= 0 && !(t.startsWith("\"") && t.endsWith("\""))) {
                out.add("\"" + t.replace("\"", "") + "\"");
            } else {
                out.add(t);
            }
        }
        return String.join(" ", out);
    }

    private Button addGutterButton(int gx, int gy, String label, String tooltip, Runnable onClick) {
        var b = Button.builder(Component.literal(label), btn -> onClick.run())
                .bounds(gx, gy, 16, 16)
                .build();
        this.addRenderableWidget(b);
        // store tooltip info however you prefer (simple: reuse render loop + isMouseOver)
        return b;
    }

    private void toggleGameMenu() {
        if (gameMenuOpen) closeGameMenu();
        else openGameMenu();
    }

    private void openGameMenu() {
        if (this.btnGame == null) return;
        closeGameMenu();
        closeSortMenu();

        java.util.List<TcgGameRegistry.Entry> options = TcgGameRegistry.filterOptions();
        final int rowH = 16;
        final int w = 96;
        final int hTotal = options.size() * rowH;

        int x0 = this.btnGame.getX() - 4 - w;
        int y0 = this.btnGame.getY();

        int minX = 4;
        int maxX = this.width - w - 4;
        int minY = 4;
        int maxY = this.height - hTotal - 4;

        x0 = clamp(x0, minX, maxX);
        y0 = clamp(y0, minY, maxY);

        for (int i = 0; i < options.size(); i++) {
            TcgGameRegistry.Entry opt = options.get(i);
            int optionIndex = i;
            Button b = Button.builder(opt.label(), btn -> {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(
                            this.menu.containerId,
                            CardDatabaseScreenHandler.GAME_FILTER_BASE + optionIndex
                    );
                }
                closeGameMenu();
            }).bounds(x0, y0 + i * rowH, w, rowH).build();

            gameMenuButtons.add(b);
            this.addRenderableWidget(b);
        }

        gameMenuOpen = true;
    }

    private void closeGameMenu() {
        if (!gameMenuButtons.isEmpty()) {
            for (var b : gameMenuButtons) {
                this.removeWidget(b);
            }
            gameMenuButtons.clear();
        }
        gameMenuOpen = false;
    }

    private void toggleSortMenu() {
        if (sortMenuOpen) closeSortMenu();
        else openSortMenu();
    }

    private void openSortMenu() {
        if (this.btnSort == null) return;
        closeSortMenu(); // safety
        closeGameMenu();

        final int rowH = 16;
        final int w = 96;
        final int hTotal = SORT_OPTS.length * rowH;

        // Anchor dropdown to the LEFT of the Sort button
        int x0 = this.btnSort.getX() - 4 - w;
        int y0 = this.btnSort.getY();

        // Clamp so it stays on-screen (and doesnâ€™t go above/below)
        int minX = 4;
        int maxX = this.width - w - 4;
        int minY = 4;
        int maxY = this.height - hTotal - 4;

        x0 = clamp(x0, minX, maxX);
        y0 = clamp(y0, minY, maxY);

        for (int i = 0; i < SORT_OPTS.length; i++) {
            SortOpt opt = SORT_OPTS[i];

            Button b = Button.builder(Component.literal(opt.label()), btn -> {
                // Update visible query text
                suppressSearchListener = true;
                rewriteTokenInQuery("order", opt.key()); // puts/updates order:<key> in the search box
                suppressSearchListener = false;

                // Keep packet order param consistent too
                this.orderKey = opt.key();

                // Fire exactly one search now
                lastSentQuery = this.search.getValue();
                triggerSearchFromUI(true);

                closeSortMenu();
            }).bounds(x0, y0 + i * rowH, w, rowH).build();

            sortMenuButtons.add(b);
            this.addRenderableWidget(b);
        }

        sortMenuOpen = true;
    }

    private void closeSortMenu() {
        if (!sortMenuButtons.isEmpty()) {
            // remove from drawable children list
            for (var b : sortMenuButtons) {
                this.removeWidget(b); // Available in modern Screen; if mappings differ, see note below
            }
            sortMenuButtons.clear();
        }
        sortMenuOpen = false;
    }

}
