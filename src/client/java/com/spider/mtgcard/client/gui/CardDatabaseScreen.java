package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.net.DBClientPackets;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;


public class CardDatabaseScreen extends HandledScreen<CardDatabaseScreenHandler> {

    private static final Identifier DB_BG  = Identifier.of("mtgcard", "textures/gui/card_database.png");
    private static final Identifier DBX_BG = Identifier.of("mtgcard", "textures/gui/deckbox_cb.png");

    private static final int DB_W  = 194;
    private static final int DB_H  = 252;

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
    private TextFieldWidget search;
    private ButtonWidget btnClear;

    // --- Scrollbar geometry/state ---
    private int trackX, trackY, trackH;   // track rect (2 px wide visual, but we draw 4 for easier hit)
    private int thumbY;                   // top of thumb
    private int thumbH = 12;              // min thumb height
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

    private int rightPanelX() { return this.x + DB_W + GAP_W; }
    private int rightPanelY() { return this.y; }

    private ButtonWidget btnStoreAll;
    private ButtonWidget btnRoute;

    private boolean suppressSearchListener = false;


    private java.util.List<com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry> tabNames = java.util.List.of();
    private java.util.List<ButtonWidget> tabButtons = new java.util.ArrayList<>();

    // Sort dropdown
    private ButtonWidget btnSort;
    private boolean sortMenuOpen = false;
    private final java.util.List<ButtonWidget> sortMenuButtons = new java.util.ArrayList<>();

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

    public void applyDeckboxTabNames(int syncId, java.util.List<com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry> entries) {
        if (this.handler == null) return;
        if (syncId != this.handler.syncId) return; // make sure it’s for this screen
        this.tabNames = (entries == null) ? java.util.List.of() : java.util.List.copyOf(entries);
    }

    private CardDatabaseScreenHandler H() { return (CardDatabaseScreenHandler) this.handler; }

    public CardDatabaseScreen(CardDatabaseScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);

        // Will be finalized in init() based on deckboxes
        this.backgroundWidth  = DB_W;
        this.backgroundHeight = DB_H;

        // With your +12 shift, we will set this precisely in init()
    }

    private void sendSearchToServer() {
        if (this.client == null) return;
        String q   = (this.search == null) ? "" : this.search.getText();
        String dir = (dirAsc ? "asc" : "desc");
        DBClientPackets.sendSearch(q, orderKey, dir);
    }

    private void scheduleAutoSearch() {
        this.nextSearchAtMs = System.currentTimeMillis() + SEARCH_DEBOUNCE_MS;
    }

    private void maybeFireAutoSearch() {
        if (this.nextSearchAtMs < 0) return;
        long now = System.currentTimeMillis();
        if (now >= this.nextSearchAtMs) {
            this.nextSearchAtMs = -1;
            String q = (this.search == null) ? "" : this.search.getText();
            if (!q.equals(lastSentQuery)) {
                lastSentQuery = q;
                triggerSearchFromUI(); // sends packet to server
            }
        }
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        maybeFireAutoSearch();

        if (btnRoute != null) {
            boolean hasDeckbox = H().getClientDeckboxCount() > 0;
            btnRoute.active = hasDeckbox;
            btnRoute.setMessage(Text.literal(routeLabel()));
        }
    }

    @Override
    protected void init() {
        boolean hasDeckbox = (this.handler.getClientDeckboxCount() > 0);

        // Size depends on whether the right deckbox panel is visible
        this.backgroundWidth  = hasDeckbox ? (DB_W + GAP_W + DBX_W) : DB_W;
        this.backgroundHeight = DB_H;

        super.init();

        // If a dropdown is open and we re-init (resize), close it
        closeSortMenu();

        // ----------------------------
        // Scrollbar geometry (left panel)
        // ----------------------------
        this.trackX = this.x + DB_W - 6;
        this.trackY = this.y + CardDatabaseScreenHandler.DB_GRID_Y; // 36
        this.trackH = 6 * 18;

        // ----------------------------
        // Header: Search + Clear
        // ----------------------------
        final int headerY = this.y + 16;
        final int fieldH  = 14;

        final int padL = 8;
        final int padR = 8;

        final int clearW = 16;

        // Search field starts after the left gutter area (18) + a little breathing room
        final int leftX  = this.x + padL + CardDatabaseScreenHandler.DB_LEFT_GUTTER;
        final int rightX = this.x + DB_W - padR;

        final int clearX  = rightX - clearW;
        final int searchW = Math.max(60, clearX - 2 - leftX);

        this.search = new TextFieldWidget(this.textRenderer, leftX, headerY, searchW, fieldH, Text.literal(""));
        this.search.setDrawsBackground(true);
        this.search.setEditable(true);
        this.addDrawableChild(this.search);
        this.setInitialFocus(this.search);

        this.search.setChangedListener(s -> {
            if (suppressSearchListener) return;
            lastQuery = s;
            scheduleAutoSearch();
        });

        this.btnClear = ButtonWidget.builder(Text.literal("×"), b -> {
            this.search.setText("");
            this.search.setFocused(true);
            lastSentQuery = "";
            triggerSearchFromUI();
        }).dimensions(clearX, headerY, clearW, fieldH).build();
        this.addDrawableChild(this.btnClear);

        // ----------------------------
        // Left gutter buttons (your requested stack)
        // Top button: x+5, y+33, then +18 each
        // ----------------------------
        final int gx = this.x + 5;
        final int gy = this.y + 35;
        final int step = 18;
        int row = 0;

        // Store All
        this.btnStoreAll = addGutterButton(gx, gy + (row++ * step), "⇪", "Store all cards into DB", () -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, CardDatabaseScreenHandler.BTN_STORE_ALL);
            }
        });

        // Sort dropdown
        this.btnSort = addGutterButton(gx, gy + (row++ * step), "S", "Sort options", this::toggleSortMenu);

        // Route toggle
        this.btnRoute = addGutterButton(gx, gy + (row++ * step), routeLabel(), "Shift-click destination (P/D)", () -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, CardDatabaseScreenHandler.BTN_TOGGLE_ROUTE);
            }
        });
        this.btnRoute.active = hasDeckbox;


        // ----------------------------
        // Deckbox tab buttons (only if >1 deckbox)
        // ----------------------------
        tabButtons.clear();
        int deckboxCount = H().getClientDeckboxCount();
        if (deckboxCount > 1) {
            int panelLeft = this.x + DB_W + GAP_W;
            int tabY = this.y + 6;     // inside deckbox header strip
            int tabX = panelLeft + 8;

            for (int i = 0; i < deckboxCount; i++) {
                int idx = i;
                ButtonWidget b = ButtonWidget.builder(Text.literal(String.valueOf(i + 1)), btn -> {
                    if (this.client != null && this.client.interactionManager != null) {
                        this.client.interactionManager.clickButton(this.handler.syncId,
                                CardDatabaseScreenHandler.TAB_BASE + idx);
                    }
                }).dimensions(tabX + i * 20, tabY, 18, 14).build();

                tabButtons.add(b);
                this.addDrawableChild(b);
            }
        }

        // ----------------------------
        // Thumb calc + inventory label shift
        // ----------------------------
        updateThumbFromHandler();
        updateThumbFromOffset();

        this.playerInventoryTitleY = (this.backgroundHeight - 94) + 12;
    }


    private String routeLabel() {
        return (H().getClientRouteMode() == 1) ? "D" : "P";
    }

    private void triggerSearchFromUI() {
        String q   = (this.search == null) ? "" : this.search.getText();
        String dir = (dirAsc ? "asc" : "desc");
        DBClientPackets.sendSearch(q, orderKey, dir);
    }

    // ---------- Input ----------
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (vy == 0) return false;
        boolean overWindow =
                mx >= this.x + CardDatabaseScreenHandler.DB_GRID_X &&
                        mx <  this.x + CardDatabaseScreenHandler.DB_GRID_X + 9 * 18 &&
                        my >= this.y + CardDatabaseScreenHandler.DB_GRID_Y &&
                        my <  this.y + CardDatabaseScreenHandler.DB_GRID_Y + 6 * 18;
        if (!overWindow) return false;
        int id = (vy < 0) ? CardDatabaseScreenHandler.SCROLL_ROW_DOWN
                : CardDatabaseScreenHandler.SCROLL_ROW_UP;
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, id);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean isSimulated) {
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

        if (button == 0) {
            if (isOverThumb(mouseX, mouseY)) {
                dragging = true;
                dragGrabDy = (int) mouseY - thumbY;
                return true;
            }
            if (isOverTrack(mouseX, mouseY)) {
                int cur = H().getClientWindowOffset();
                int max = H().getClientMaxWindowOffset();
                if (max > 0) {
                    int page = 54;
                    int next = (mouseY < thumbY) ? cur - page : cur + page;
                    sendAbsoluteOffset(Math.max(0, Math.min(max, next)));
                }
                return true;
            }
        }
        if (isOverTrack(mouseX, mouseY)) {
            int max = H().getClientMaxWindowOffset();
            if (max > 0) {
                int travel = Math.max(1, trackH - thumbH);
                int bottom = trackY + travel;
                if (mouseY >= bottom - 1) {
                    sendAbsoluteOffset(max);  // jump straight to bottom if you click at the end
                } else {
                    int cur = H().getClientWindowOffset();
                    int page = 54;
                    int next = (mouseY < thumbY) ? cur - page : cur + page;
                    next = Math.max(0, Math.min(max, next));
                    sendAbsoluteOffset(next);
                }
            }
            return true;
        }
        return super.mouseClicked(click, isSimulated);
    }

    private void drawBottomPadding(DrawContext ctx) {
        final int total = H().getClientIntakeCount();
        final int offset = H().getClientWindowOffset();
        final int pageSize = 54;   // 6x9 window
        final int cols = 9;
        final int rows = 6;

        final int maxOffset = Math.max(0, total - pageSize);
        if (offset != maxOffset) return;

        final int remaining = Math.max(0, total - offset);
        final int cellsOnPage = Math.min(pageSize, remaining);
        if (cellsOnPage >= pageSize) return;

        final int fullRows = cellsOnPage / cols;
        final int lastRowCells = cellsOnPage % cols;

        final int x0 = this.x + CardDatabaseScreenHandler.DB_GRID_X; // 26
        final int y0 = this.y + CardDatabaseScreenHandler.DB_GRID_Y; // 36
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
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging && click.button() == 0) {
            int max = H().getClientMaxWindowOffset();
            if (max > 0) {
                int travel = Math.max(1, trackH - thumbH);

                int thumbTop = (int) click.y() - dragGrabDy;
                thumbTop = Math.max(trackY, Math.min(trackY + travel, thumbTop));

                double t = (thumbTop - trackY) / (double) travel; // 0..1

                int target;
                boolean atBottom = (thumbTop >= trackY + travel - 1) || (t > 0.999_5);

                if (atBottom) {
                    target = max;
                } else {
                    int rowsMax = (int) Math.ceil(max / 9.0);
                    int rowIndex = (int) Math.round(t * rowsMax);
                    rowIndex = Math.max(0, Math.min(rowsMax, rowIndex));
                    target = Math.min(max, rowIndex * 9);
                }

                if (target != lastSentOffset) {
                    sendAbsoluteOffset(target);
                }
            }
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    public void applyResults(Object ignored) { }

    @Override
    public boolean keyPressed(KeyInput key) {
        // If the search box is focused, swallow the Inventory keybind (E) so the GUI doesn't close.
        if (this.search != null && this.search.isFocused() && this.client != null) {
            var invKey = this.client.options.inventoryKey;
            if (invKey != null && invKey.matchesKey(key)) {
                return true; // consume E (or whatever the inventory key is)
            }
        }

        // Optional: Enter triggers search manually (auto-search still works)
        int code = key.getKeycode();
        if ((code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER)
                && this.search != null && this.search.isFocused()) {
            triggerSearchFromUI();
            return true;
        }

        return super.keyPressed(key);
    }

    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        // Yarn exposes either public fields x/y or getters getX()/getY() depending on version.
        int sx, sy;
        try {
            // most mappings: public fields
            sx = this.x + slot.x;
            sy = this.y + slot.y;
        } catch (Throwable ignore) {
            // if your mappings use getters, uncomment these two lines and delete the try/catch:
            // sx = this.x + slot.getX();
            // sy = this.y + slot.getY();
            sx = this.x; sy = this.y; // placeholder to keep compile if you switch to getters
        }
        // 16x16 slot box (same as vanilla)
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    /** Matches Deckbox foil/glint check so previews shimmer the same way. */
    private boolean isFoil(ItemStack st) {
        // Component-based glint (your foil slot uses this)
        Boolean glint = st.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        boolean hasGlint = glint != null && glint;

        // Optional NBT flag support (if you decide to store mtg_foil)
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound root = (comp == null)
                ? new net.minecraft.nbt.NbtCompound()
                : comp.copyNbt();

        boolean foilNbt = root.getBoolean("mtg_foil").orElse(false);

        return hasGlint || foilNbt;
    }

    private void renderHoverPreview(DrawContext ctx, int mouseX, int mouseY, float delta) {

        // Which slot are we hovering?
        var slot = this.focusedSlot;
        if (slot == null || !slot.hasStack() || !isMouseOverSlotArea(slot, mouseX, mouseY)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        ItemStack st = slot.getStack();
        if (!st.isOf(com.spider.mtgcard.item.ModItems.CARD)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        // Debounce hover so we don't spam loads while sweeping the mouse
        long now = System.currentTimeMillis();
        if (!ItemStack.areEqual(st, lastHoverStack)) {
            hoverSinceMs = now;
            lastHoverStack = st.copy();
            lastTexRef = null;
            animStartMs = now; // ← start anim on new card
        }
        if (now - hoverSinceMs < HOVER_DEBOUNCE_MS) return;

        // Request texture (async if needed)
        int face = DeckboxScreen.readFaceIndex(st);
        if (face != lastHoverFace) {
            lastHoverFace = face;
            lastTexRef = null;
            animStartMs = now; // ← start anim on face change
        }

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(st, face);
        if (ref == null || ref.id() == null) return; // not ready yet
        lastTexRef = ref;

        // --- Layout: left of GUI, with capped size ---
        final int PANEL_PAD = 4;
        final int panelMaxH = Math.min(this.backgroundHeight - 8, 220);
        final int panelMaxW = 180;

        int panelW = panelMaxW;
        int panelH = panelMaxH;
        int panelX = this.x - (panelW + 12);
        int panelY = this.y + 4;
        if (panelX < 8) panelX = this.x + this.backgroundWidth + 12; // flip to right if off-screen

        // Fit card into panel while preserving aspect
        final int texW = ref.texW();
        final int texH = ref.texH();
        float aspect = (float) texW / (float) texH;

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

        // Tilt: ~2.5° -> 0°
        float angleDeg = (1f - ease) * 2.5f;

        // Fade: 40% -> 100% alpha
        int alphaMain = (int) (255f * (0.40f + 0.60f * ease));
        int colorMain = (alphaMain << 24) | 0x00FFFFFF; // ARGB tint for main draw
        int colorShadow = 0x55000000;                    // soft black shadow

        // Precompute scales from texture → target draw size
        float sx = (float) drawW / (float) texW;
        float sy = (float) drawH / (float) texH;

        // Save matrix
        var m = ctx.getMatrices();
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

        ctx.drawTexture(
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

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                0, 0, 0f, 0f,
                texW, texH, texW, texH,
                colorMain
        );

        // -------- 3) FOIL SHIMMER pass (overlay) --------
        if (isFoil(st)) {
            // Re-apply the main transform
            m.set(m00, m01, m10, m11, m20, m21);
            m.translate((float) x, (float) y);
            m.scale(sx * scaleAnim, sy * scaleAnim);
            m.translate(texW / 2f, texH / 2f);
            m.rotate((float) Math.toRadians(angleDeg));
            m.translate(-texW / 2f, -texH / 2f);

            int stripePx = Math.max(6, (int) (texW * 0.22f));
            float travel = texW + stripePx * 2f;
            float speed = 0.22f;
            float pos = ((nowMs / 16f) * speed) % travel - stripePx;

            int u = Math.round(pos);
            if (u < texW && u + stripePx > 0) {
                int drawU = Math.max(0, Math.min(texW - stripePx, u));
                int clipW = Math.min(stripePx, texW - drawU);

                int shimmerAlpha = (int) (0x88 + 0x2A * ease); // a bit brighter
                int colorShimmer = (shimmerAlpha << 24) | 0x00FFFFFF;

                ctx.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        ref.id(),
                        drawU, 0,
                        (float) drawU, 0f,
                        clipW, texH,
                        texW, texH,
                        colorShimmer
                );
            }
        }

        // Restore matrix
        m.set(m00, m01, m10, m11, m20, m21);
    }

    // ---------- Drawing ----------
    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {

        // LEFT: card database texture (512 atlas)
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                DB_BG,
                this.x, this.y,
                0, 0,
                DB_W, DB_H,
                512, 512
        );

        if (H().getClientDeckboxCount() > 0) {
            int px = rightPanelX();
            int py = rightPanelY();
            int tab = H().getClientActiveDeckboxTab();
            String title = "Deckbox " + (tab + 1) + "/" + H().getClientDeckboxCount();
            ctx.drawText(this.textRenderer, title, px + 52, py + 9, 0xFFFFFFFF, false);
        }

        // scrollbar geometry (left panel)
        this.trackX = this.x + DB_W - 6;
        this.trackY = this.y + CardDatabaseScreenHandler.DB_GRID_Y;
        this.trackH = 6 * 18;

        int maxOff = H().getClientMaxWindowOffset();
        if (maxOff > 0) {
            updateThumbFromHandler();

            int x0 = trackX - 1, x1 = trackX + 3;
            ctx.fill(x0, trackY, x1, trackY + trackH, 0xFF1E1E1E);

            int y0 = thumbY;
            int y1 = Math.min(trackY + trackH, y0 + thumbH);
            ctx.fill(x0, y0, x1, y1, 0xFFBFBFBF);
        }

        drawBottomPadding(ctx);

        // RIGHT: deckbox texture (also 512 atlas)
        if (H().getClientDeckboxCount() > 0) {
            int panelLeft = this.x + DB_W + GAP_W;

            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    DBX_BG,
                    panelLeft, this.y,
                    0, 0,
                    DBX_W, DBX_H,
                    512, 512
            );
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        renderDeckboxTabTooltip(ctx, mouseX, mouseY);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);

        // Hover preview
        renderHoverPreview(ctx, mouseX, mouseY, delta);
    }

    private void renderDeckboxTabTooltip(DrawContext ctx, int mouseX, int mouseY) {
        if (tabButtons == null || tabButtons.isEmpty()) return;
        if (tabNames == null || tabNames.isEmpty()) return;

        for (int i = 0; i < tabButtons.size(); i++) {
            var b = tabButtons.get(i);
            if (b == null) continue;

            // ButtonWidget has isMouseOver(double,double) in modern mappings
            if (!b.isMouseOver(mouseX, mouseY)) continue;

            if (i >= tabNames.size()) return;
            var e = tabNames.get(i);

            java.util.List<Text> lines = new java.util.ArrayList<>();
            if (e.commander() != null && !e.commander().isBlank()) {
                lines.add(Text.literal("Commander: ").append(Text.literal(e.commander())));
            }
            if (e.partner() != null && !e.partner().isBlank()) {
                lines.add(Text.literal("Partner: ").append(Text.literal(e.partner())));
            }
            if (lines.isEmpty()) lines.add(Text.literal("Empty"));

            ctx.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            return;
        }
    }

    // ---------- Helpers ----------
    private void updateThumbFromHandler() {
        final int count  = this.H().getClientIntakeCount();
        final int maxOff = Math.max(0, count - 54);

        if (maxOff <= 0) {
            thumbH = trackH;
            thumbY = trackY;
            return;
        }

        int totalRows   = (count + 8) / 9; // ceil(count / 9)
        int visibleRows = 6;
        int minThumbPx  = 12;

        thumbH = Math.max(minThumbPx, (int)Math.round(trackH * (visibleRows / (double) totalRows)));

        int off    = this.H().getClientWindowOffset();
        int travel = Math.max(1, trackH - thumbH);
        double t   = off / (double) maxOff;          // 0..1
        thumbY     = trackY + (int)Math.round(t * travel);
    }

    private void updateThumbFromOffset() {
        int max = H().getClientMaxWindowOffset();
        int total = Math.max(1, H().getClientIntakeCount());
        int vis = 54;
        int minH = 12;
        int est = (int) Math.max(minH, Math.floor((vis / (double) total) * trackH));
        thumbH = clamp(est, minH, trackH);

        if (max <= 0) {
            thumbY = trackY;
            return;
        }
        int off = H().getClientWindowOffset();
        int travel = Math.max(1, trackH - thumbH);
        thumbY = trackY + (int) Math.round((off / (double) max) * travel);
    }

    private boolean isOverThumb(double mx, double my) {
        int x0 = trackX - 1, x1 = trackX + 3;
        int y0 = thumbY,     y1 = thumbY + thumbH;
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    private boolean isOverTrack(double mx, double my) {
        int x0 = trackX - 1, x1 = trackX + 3;
        int y0 = trackY,     y1 = trackY + trackH;
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    private void sendAbsoluteOffset(int offset) {
        lastSentOffset = offset;
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(this.handler.syncId, SET_OFFSET_BASE + offset);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    /** Ensure exactly one token key:<value> exists in the search box; update or append. */
    private void rewriteTokenInQuery(String key, String value) {
        String raw = search.getText();
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
        search.setText(rebuilt);
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

    private ButtonWidget addGutterButton(int gx, int gy, String label, String tooltip, Runnable onClick) {
        var b = ButtonWidget.builder(Text.literal(label), btn -> onClick.run())
                .dimensions(gx, gy, 16, 16)
                .build();
        this.addDrawableChild(b);
        // store tooltip info however you prefer (simple: reuse render loop + isMouseOver)
        return b;
    }

    private void toggleSortMenu() {
        if (sortMenuOpen) closeSortMenu();
        else openSortMenu();
    }

    private void openSortMenu() {
        if (this.btnSort == null) return;
        closeSortMenu(); // safety

        final int rowH = 16;
        final int w = 96;
        final int hTotal = SORT_OPTS.length * rowH;

        // Anchor dropdown to the LEFT of the Sort button
        int x0 = this.btnSort.getX() - 4 - w;
        int y0 = this.btnSort.getY();

        // Clamp so it stays on-screen (and doesn’t go above/below)
        int minX = 4;
        int maxX = this.width - w - 4;
        int minY = 4;
        int maxY = this.height - hTotal - 4;

        x0 = clamp(x0, minX, maxX);
        y0 = clamp(y0, minY, maxY);

        for (int i = 0; i < SORT_OPTS.length; i++) {
            SortOpt opt = SORT_OPTS[i];

            ButtonWidget b = ButtonWidget.builder(Text.literal(opt.label()), btn -> {
                // Update visible query text
                suppressSearchListener = true;
                rewriteTokenInQuery("order", opt.key()); // puts/updates order:<key> in the search box
                suppressSearchListener = false;

                // Keep packet order param consistent too
                this.orderKey = opt.key();

                // Fire exactly one search now
                lastSentQuery = this.search.getText();
                triggerSearchFromUI();

                closeSortMenu();
            }).dimensions(x0, y0 + i * rowH, w, rowH).build();

            sortMenuButtons.add(b);
            this.addDrawableChild(b);
        }

        sortMenuOpen = true;
    }

    private void closeSortMenu() {
        if (!sortMenuButtons.isEmpty()) {
            // remove from drawable children list
            for (var b : sortMenuButtons) {
                this.remove(b); // Available in modern Screen; if mappings differ, see note below
            }
            sortMenuButtons.clear();
        }
        sortMenuOpen = false;
    }

}
