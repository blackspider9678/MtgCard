// com/spider/mtgcard/db/CardDatabaseScreenHandler.java
package com.spider.mtgcard.db;

import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModRegistry;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

public class CardDatabaseScreenHandler extends ScreenHandler {
    public static final int TAB_BASE = 300_000; // button IDs: TAB_BASE + tabIndex

    private static final int WINDOW_SLOTS = 54;

    public static final int SCROLL_ROW_UP     = -1;
    public static final int SCROLL_ROW_DOWN   = +1;
    public static final int SCROLL_PAGE_UP    = -2;
    public static final int SCROLL_PAGE_DOWN  = +2;
    public static final int SCROLL_TOP        = -3;
    public static final int SCROLL_BOTTOM     = +3;

    public  static final int SET_OFFSET_BASE   = 10_000;

    private static final int DBX_ROWS = com.spider.mtgcard.deckbox.DeckboxBlockEntity.DECKBOX_ROWS; // 11
    private static final int DBX_COLS = com.spider.mtgcard.deckbox.DeckboxBlockEntity.DECKBOX_COLS; // 9

    // === Card Database panel ===
    public static final int DB_LEFT_GUTTER = 18;   // new left UI column
    public static final int DB_TOP_BAR     = 18;   // new header strip
    public static final int SLOT_SIZE     = 18;

    // 6x9 visible window
    public static final int DB_ROWS = 6;
    public static final int DB_COLS = 9;

    // Vanilla chest padding is still 8px INSIDE the texture
    public static final int DB_INNER_PAD_X = 8;
    public static final int DB_INNER_PAD_Y = 18;

    // Final grid origin (pixel-perfect)
    public static final int DB_GRID_X =
            DB_INNER_PAD_X + DB_LEFT_GUTTER;   // 8 + 18 = 26
    public static final int DB_GRID_Y =
            DB_INNER_PAD_Y + DB_TOP_BAR;       // 18 + 18 = 36

    private DeckboxInventoryView deckboxView = null;

    private String activeQuery = "";

    private final Inventory windowInv;

    @org.jetbrains.annotations.Nullable
    private final CardDBView view; // null on client

    @org.jetbrains.annotations.Nullable
    private final CardDBSession session;


    // kept for server-managed rebuild path (optional)
    private java.util.List<ItemStack> intakeAll = new java.util.ArrayList<>();
    private com.spider.mtgcard.db.search.ScryfallQuery currentQuery = new com.spider.mtgcard.db.search.ScryfallQuery();
    private java.util.List<com.spider.mtgcard.db.search.SearchEngine.Row> currentView = java.util.List.of();
    private int windowOffset = 0;

    private final net.minecraft.screen.ArrayPropertyDelegate props = new net.minecraft.screen.ArrayPropertyDelegate(6); // props[5] = route mode: 0 = player inv, 1 = deckbox tab
    public int getClientRouteMode() { return props.get(5); } // 0 inv, 1 deckbox
    public static final int BTN_STORE_ALL     = 10;
    public static final int BTN_TOGGLE_ROUTE  = 11;
    private PlayerInventory playerInvRef;

    // existing
    public int getLastSearchTotal() { return props.get(2); }
    public int getClientIntakeCount()  { return props.get(0); }
    public int getClientWindowOffset() { return props.get(1); }

    // new
    public int getClientDeckboxCount() { return props.get(3); }
    public int getClientActiveDeckboxTab() { return props.get(4); }

    private java.util.List<BlockPos> deckboxPositions = java.util.List.of();
    private int activeDeckboxIndex = 0;

    public int getClientMaxWindowOffset() {
        int count = getClientIntakeCount();
        return Math.max(0, count - 54);
    }

    public void sendDeckboxTabNamesTo(net.minecraft.server.network.ServerPlayerEntity sp) {
        sendTabNamesToClient(sp);
    }

    // ---------- CLIENT ctor ----------
    public CardDatabaseScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session   = null;
        this.view      = null;
        this.windowInv = new net.minecraft.inventory.SimpleInventory(54);

        // build the SAME deckbox slots on client by scanning neighbors from pos
        this.deckboxPositions = findNeighborDeckboxes(inv.player.getEntityWorld(), pos);

        init(inv);                // adds the 54 + player inv slots
    }

    public CardDatabaseScreenHandler(int syncId, PlayerInventory inv, CardDBSession session, BlockPos pos) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session   = java.util.Objects.requireNonNull(session);
        this.view      = session;
        this.windowInv = session.getWindow();

        this.deckboxPositions = findNeighborDeckboxes(inv.player.getEntityWorld(), pos);

        init(inv);
        session.compactIntakeAndReprojectSamePage();
    }

    private static java.util.List<BlockPos> findNeighborDeckboxes(net.minecraft.world.World world, BlockPos pos) {
        if (world == null) return java.util.List.of();
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        for (var d : net.minecraft.util.math.Direction.values()) {
            BlockPos p = pos.offset(d);
            var st = world.getBlockState(p);
            if (st != null && st.getBlock() == com.spider.mtgcard.registry.ModBlocks.DECKBOX) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean uiFrozen = false;
    private boolean needsReproject = false;

    private java.util.List<String> tabCommander = java.util.List.of();
    private java.util.List<String> tabPartner   = java.util.List.of();
    private static String readCustomNameFromCard(net.minecraft.item.ItemStack st) {
        var comp = st.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, null);
        if (comp == null) return "";
        var nbt = comp.copyNbt();
        var compsOpt = nbt.getCompound("components");
        if (compsOpt.isEmpty()) return "";
        var comps = compsOpt.get();
        var nameOpt = comps.getString("minecraft:custom_name");
        return nameOpt.orElse("");
    }

    private static String safeCommanderNameFromDeckbox(com.spider.mtgcard.deckbox.DeckboxBlockEntity be, int slot) {
        if (be == null) return "";
        var st = be.getStack(slot);
        if (st == null || st.isEmpty()) return "";
        String nm = readCustomNameFromCard(st);
        return nm == null ? "" : nm;
    }

    private void sendTabNamesToClient(net.minecraft.server.network.ServerPlayerEntity sp) {
        if (deckboxPositions.isEmpty()) return;

        java.util.ArrayList<com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry> entries =
                new java.util.ArrayList<>(deckboxPositions.size());

        for (var p : deckboxPositions) {
            var be = sp.getEntityWorld().getBlockEntity(p);
            if (be instanceof com.spider.mtgcard.deckbox.DeckboxBlockEntity dbe) {
                String cmd = safeCommanderNameFromDeckbox(dbe, com.spider.mtgcard.deckbox.DeckboxBlockEntity.SECOND_SIDE_SLOT);
                String par = safeCommanderNameFromDeckbox(dbe, com.spider.mtgcard.deckbox.DeckboxBlockEntity.THIRD_SIDE_SLOT);
                entries.add(new com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry(cmd, par));
            } else {
                entries.add(new com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry("", ""));
            }
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                sp,
                new com.spider.mtgcard.net.payload.DeckboxTabNamesPayload(this.syncId, entries)
        );
    }

    // --- guard to prevent re-entrancy while we rebuild a page ---
    private boolean reprojectingNow = false;

    private void guardedReprojectSamePage() {
        if (view == null) return;
        reprojectingNow = true;
        try {
            reprojectCurrentPage();
            sendContentUpdates();
            syncPropsFromView();
        } finally {
            reprojectingNow = false;
        }
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType action, PlayerEntity player) {
        // Swallow SHIFT-CLICK (QUICK_MOVE) on the 6×9 window entirely and handle it ourselves.
        if (action == SlotActionType.QUICK_MOVE && slotIndex >= 0 && slotIndex < WINDOW_SLOTS) {
            if (!player.getEntityWorld().isClient() && this.view instanceof CardDBSession sess) {
                Slot slot = (slotIndex < this.slots.size()) ? this.slots.get(slotIndex) : null;
                if (slot != null && slot.hasStack()) {
                    // 1) Copy the clicked stack and read its uid
                    ItemStack clicked = slot.getStack().copy();
                    String uid = readUid(clicked);

                    // 2) Clear the visible window cell immediately (so nothing else can be pulled)
                    //    and make sure no other window-side remove hooks fire during this gesture.
                    this.suppressWindowOnTake = true;
                    sess.setUiFrozen(true);
                    try {
                        slot.setStack(ItemStack.EMPTY);
                        slot.markDirty();

                        // 3) Give the item to the player's inventory directly (no ScreenHandler sweeps)
                        giveDbCardToDestination(player, clicked);

                        // 4) Remove exactly one backing entry by UID, compact, and queue a single reproject
                        if (uid != null && !uid.isBlank()) {
                            sess.removeFromIntakeByUid(uid);
                        }
                        sess.compactIntakeAndReprojectSamePage();
                        if (sess.isProjectingSearch()) {
                            this.reprojectingNow = true;
                            try { reprojectCurrentPage(); } finally { this.reprojectingNow = false; }
                        }

                        // 5) Sync props/UI once
                        sendContentUpdates();
                        syncPropsFromView();
                    } finally {
                        sess.setUiFrozen(false);
                        this.suppressWindowOnTake = false;
                    }
                }
            }
            return; // <- CRITICAL: do NOT call super; we fully handled the shift-click.
        }

        // All other interactions use vanilla.
        super.onSlotClick(slotIndex, button, action, player);
    }

    // ---- helpers ----
    private static String readUid(ItemStack st) {
        var comp = st.getOrDefault(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, null);
        if (comp == null) return "";
        var nbt = comp.copyNbt();
        return nbt.getString("mtg_uid").orElse("");
    }

    private void syncPropsFromView() {
        if (view == null) return;
        props.set(0, view.getIntakeCount());
        props.set(1, view.getWindowOffset());
        this.sendContentUpdates();
    }

    private void syncDeckboxPropsAndTarget() {
        props.set(3, deckboxPositions.size());
        props.set(4, Math.max(0, Math.min(deckboxPositions.size() - 1, props.get(4))));
        sendContentUpdates();
    }

    private void updateDeckboxTargetFromTab(int tabIdx) {
        if (deckboxView == null) return;
        if (view == null || deckboxPositions.isEmpty()) {
            deckboxView.setTarget(null);
            return;
        }
        tabIdx = Math.max(0, Math.min(deckboxPositions.size() - 1, tabIdx));

        // We need world access; session is server-side and can provide player world indirectly.
        // easiest: use the player’s world at click-time (see onButtonClick), so here assume session can give world:
        // If your CardDBSession already has a world reference, use that; otherwise we'll set target in onButtonClick.

        // We'll leave this method for when we DO have a world reference.
    }

    // ---------- CLIENT ctor ----------

    private void syncDeckboxProps() {
        props.set(3, deckboxPositions.size());
        int tab = props.get(4);
        if (deckboxPositions.isEmpty()) {
            props.set(4, 0);
        } else {
            props.set(4, Math.max(0, Math.min(deckboxPositions.size() - 1, tab)));
        }
        sendContentUpdates();
    }

    private void setDeckboxTarget(PlayerEntity player, int tabIdx) {
        if (deckboxView == null) return;
        if (deckboxPositions.isEmpty()) {
            deckboxView.setTarget(null);
            return;
        }
        tabIdx = Math.max(0, Math.min(deckboxPositions.size() - 1, tabIdx));
        var pos = deckboxPositions.get(tabIdx);
        var be = player.getEntityWorld().getBlockEntity(pos);
        if (be instanceof com.spider.mtgcard.deckbox.DeckboxBlockEntity dbe) deckboxView.setTarget(dbe);
        else deckboxView.setTarget(null);
    }


    // ---------- SERVER ctor ----------
    public CardDatabaseScreenHandler(int syncId, PlayerInventory inv, CardDBSession session) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session   = java.util.Objects.requireNonNull(session, "session");
        this.view      = session;
        this.windowInv = session.getWindow();
        init(inv);

        // First-time cleanup so reopening doesn't show stale empties
        session.compactIntakeAndReprojectSamePage();
    }

    private boolean suppressWindowOnTake = false;


    // ---------- Common init ----------
    private void init(PlayerInventory playerInv) {
        this.playerInvRef = playerInv;

        // IMPORTANT: register property delegate FIRST so initial open sync includes these values
        this.addProperties(props);

        // --------------------
        // Deckbox props (must exist BEFORE screen init reads them)
        // --------------------
        props.set(3, deckboxPositions.size());
        // route mode defaults to player inventory
        props.set(5, 0);

        // if no deckbox exists, force route mode to inv
        if (deckboxPositions.isEmpty()) props.set(5, 0);

        int tab = props.get(4);
        if (deckboxPositions.isEmpty()) {
            tab = 0;
        } else {
            tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));
        }
        props.set(4, tab);

        // --------------------
        // Top 6×9 (window)
        // --------------------
        final int x0 = DB_GRID_X; // 26
        final int y0 = DB_GRID_Y; // 36
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r * 9 + c;
                int sx = x0 + c * SLOT_SIZE;
                int sy = y0 + r * SLOT_SIZE;

                this.addSlot(new Slot(this.windowInv, idx, sx, sy) {
                    @Override public boolean canInsert(ItemStack stack) { return false; }
                    @Override public void onQuickTransfer(ItemStack newStack, ItemStack original) {}

                    @Override
                    public void onTakeItem(PlayerEntity player, ItemStack taken) {
                        if (player.getEntityWorld().isClient()) return;
                        if (suppressWindowOnTake) return;

                        String uid = readUid(taken);
                        if (!uid.isEmpty() && view instanceof CardDBSession s) {
                            s.removeFromIntakeByUid(uid);
                            s.compactIntakeAndReprojectSamePage();
                            if (s.isProjectingSearch()) {
                                reprojectingNow = true;
                                try { reprojectCurrentPage(); }
                                finally { reprojectingNow = false; }
                            }
                            // do NOT call sendContentUpdates() here; click paths handle it
                            // do NOT call syncPropsFromView() here; click paths handle it
                        }
                    }

                    @Override
                    public ItemStack takeStack(int amount) {
                        ItemStack cur = this.getStack();
                        if (cur.isEmpty()) return ItemStack.EMPTY;

                        int n = Math.min(amount, cur.getCount());
                        ItemStack out = cur.copy();
                        out.setCount(n);

                        this.setStack(ItemStack.EMPTY);
                        this.markDirty();
                        return out;
                    }
                });
            }
        }

        // --------------------
        // Player inventory (3 rows) — pixel-perfect for card_database.png
        // --------------------
        final int invX = DB_GRID_X;     // 26
        final int invY0 = 166;          // row1 slot Y
        final int invRowStep = 19;      // texture uses 19px vertical spacing

        for (int r = 0; r < 3; r++) {
            int y = invY0 + r * invRowStep;
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9,
                        invX + c * 18,
                        y
                ));
            }
        }

        // --------------------
        // Hotbar — pixel-perfect for card_database.png
        // --------------------
        final int hotbarY = 227;        // texture hotbar is 227
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c,
                    invX + c * 18,
                    hotbarY
            ));
        }

        // --------------------
        // Optional right-side deckbox panel
        // --------------------
        if (!deckboxPositions.isEmpty()) {
            if (deckboxView == null) deckboxView = new DeckboxInventoryView();

            // If server, bind target NOW (so the slots have a live inventory)
            if (!playerInv.player.getEntityWorld().isClient()) {
                props.set(4, 0);
                setDeckboxTarget(playerInv.player, 0);

                if (playerInv.player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                    sendTabNamesToClient(sp);
                }
            }

            // IMPORTANT: panelX must match left-panel width (194) + gap (14)
            final int panelX = 193 + 14;

            // Pixel-perfect for deckbox_cb.png
            final int dx0 = panelX + 16;
            final int dy0 = 29;

            // 11 x 9 main grid
            for (int r = 0; r < DBX_ROWS; r++) {
                for (int c = 0; c < DBX_COLS; c++) {
                    int idx = r * DBX_COLS + c;
                    int sx = dx0 + c * 18;
                    int sy = dy0 + r * 18;

                    this.addSlot(new Slot(deckboxView, idx, sx, sy) {
                        @Override public boolean canInsert(ItemStack stack) { return stack.isOf(ModItems.CARD); }
                        @Override public boolean canTakeItems(PlayerEntity player) { return true; }
                    });
                }
            }

            // side slots
            int sideX = dx0 + DBX_COLS * 18 + 3;
            int sideY = dy0;

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.FIRST_SIDE_SLOT,
                    sideX, sideY + 0 * 18) {
                @Override public boolean canInsert(ItemStack stack) { return stack.isOf(net.minecraft.item.Items.BUNDLE); }
                @Override public int getMaxItemCount() { return 1; }
                @Override public boolean canTakeItems(PlayerEntity player) { return true; }
            });

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.SECOND_SIDE_SLOT,
                    sideX, sideY + 1 * 18) {
                @Override public boolean canInsert(ItemStack stack) { return stack.isOf(ModItems.CARD); }
                @Override public boolean canTakeItems(PlayerEntity player) { return true; }
            });

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.THIRD_SIDE_SLOT,
                    sideX, sideY + 2 * 18) {
                @Override public boolean canInsert(ItemStack stack) { return stack.isOf(ModItems.CARD); }
                @Override public boolean canTakeItems(PlayerEntity player) { return true; }
            });
        }

        // --------------------
        // Initial prop sync from view (set-only; no nested sendContentUpdates)
        // --------------------
        if (view != null) {
            props.set(0, view.getIntakeCount());
            props.set(1, view.getWindowOffset());
        }

        // One authoritative sync at end of init
        this.sendContentUpdates();
    }

    public void setActiveQuery(String q) {
        this.activeQuery = (q == null) ? "" : q;
    }

    // --- Search ---
    public void applySearch(String q, String order, String dir) {
        if (this.view == null) return;
        setActiveQuery(q);
        boolean clear = (q == null) || q.isBlank();
        if (clear) {
            view.clearSearchProjection();
            props.set(2, -1);
            view.setWindowOffset(0);
            props.set(1, 0);
            syncPropsFromView();
            return;
        }
        final int limit = 54, offset = 0;
        var parsed = com.spider.mtgcard.db.search.ScryfallQuery.parse(q, limit, offset, order, dir);
        var page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);
        var toShow = new java.util.ArrayList<ItemStack>(Math.min(54, page.items().size()));
        for (var row : page.items())
            toShow.add(row.stack == null ? ItemStack.EMPTY : row.stack.copy());
        view.setWindowOffset(0);
        props.set(1, 0);
        view.projectSearchResults(toShow);
        props.set(2, page.total());
        syncPropsFromView();
    }

    // Scroll / drag from client
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (view == null || player.getEntityWorld().isClient()) return false;

        if (id >= TAB_BASE && id < TAB_BASE + 1000) {
            int tab = id - TAB_BASE;
            if (deckboxPositions.isEmpty()) return false;

            tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));
            props.set(4, tab);
            // inside TAB_BASE handling after props.set(4, tab);
            if (deckboxPositions.isEmpty()) props.set(5, 0);

            setDeckboxTarget(player, tab);
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                sendTabNamesToClient(sp);
            }
            syncDeckboxProps();
            return true;
        }

        if (id >= SET_OFFSET_BASE) {
            view.setWindowOffset(id - SET_OFFSET_BASE);
            syncPropsFromView();
            return true;
        }
        if (id == BTN_STORE_ALL) {
            storeAllFromPlayerInventory(player);
            return true;
        }

        if (id == BTN_TOGGLE_ROUTE) {
            if (deckboxPositions.isEmpty()) {
                props.set(5, 0); // force inv when no deckbox
                sendContentUpdates();
                return true;
            }
            props.set(5, (props.get(5) == 1) ? 0 : 1);
            sendContentUpdates();
            return true;
        }
        switch (id) {
            case SCROLL_ROW_UP    -> view.shiftWindow(-9);
            case SCROLL_ROW_DOWN  -> view.shiftWindow(+9);
            case SCROLL_PAGE_UP   -> view.shiftWindow(-WINDOW_SLOTS);
            case SCROLL_PAGE_DOWN -> view.shiftWindow(+WINDOW_SLOTS);
            case SCROLL_TOP       -> view.setWindowOffset(0);
            case SCROLL_BOTTOM    -> view.setWindowOffset(view.getMaxWindowOffset());
            default               -> { return false; }
        }
        var results = runSearch(null, activeQuery, new com.spider.mtgcard.db.search.PageCursor(0, 54));
        props.set(2, results.total);
        sendContentUpdates();
        syncPropsFromView();
        return true;
    }

    public com.spider.mtgcard.db.search.SearchResults runSearch(
            com.spider.mtgcard.db.search.Filters f, String q,
            com.spider.mtgcard.db.search.PageCursor cursor
    ) {
        var res = new com.spider.mtgcard.db.search.SearchResults();
        if (view == null) { res.total=0; res.items = java.util.List.of(); res.next=null; return res; }

        final int offset = (cursor != null) ? cursor.offset() : 0;
        final int limit  = 54;
        final String order = "name";
        final String dir   = "asc";

        var parsed = com.spider.mtgcard.db.search.ScryfallQuery.parse((q == null ? "" : q), limit, offset, order, dir);
        var page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);

        var items = new java.util.ArrayList<com.spider.mtgcard.db.search.IndexRecord>(page.items().size());
        for (var row : page.items()) items.add(com.spider.mtgcard.db.search.SearchEngine.toIndexRecord(row));

        res.total = page.total();
        res.items = items;
        res.next  = (page.nextOffset() >= 0) ? new com.spider.mtgcard.db.search.PageCursor(page.nextOffset(), limit) : null;
        return res;
    }

    // Rebuild filtered/sorted rows (server-managed path) — optional
    private void rebuildAndProject() {
        currentView = buildViewRows(intakeAll, currentQuery);
        int maxOffset = Math.max(0, currentView.size() - 54);
        if (windowOffset > maxOffset) windowOffset = maxOffset;
        session.projectWindow(currentView, windowOffset);
        this.sendContentUpdates();
    }

    private java.util.List<com.spider.mtgcard.db.search.SearchEngine.Row> buildViewRows(
            java.util.List<ItemStack> base,
            com.spider.mtgcard.db.search.ScryfallQuery q
    ) {
        var filteredStacks = com.spider.mtgcard.db.search.SearchEngine.filterStacks(base, q);
        var rows = new java.util.ArrayList<com.spider.mtgcard.db.search.SearchEngine.Row>(filteredStacks.size());
        for (var st : filteredStacks) {
            rows.add(com.spider.mtgcard.db.search.SearchEngine.toRow(st));
        }
        return rows;
    }

    private void reprojectCurrentPage() {
        if (view == null) return;

        int offset = Math.max(0, view.getWindowOffset());
        final int limit = 54;

        var parsed = com.spider.mtgcard.db.search.ScryfallQuery.parse(
                (activeQuery == null ? "" : activeQuery), limit, offset, "name", "asc"
        );
        var page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);

        var toShow = new java.util.ArrayList<ItemStack>(Math.min(54, page.items().size()));
        for (var row : page.items()) {
            toShow.add(row.stack == null ? ItemStack.EMPTY : row.stack.copy());
        }

        view.projectSearchResults(toShow);
        props.set(2, page.total()); // lastSearchTotal
        sendContentUpdates();
        syncPropsFromView();
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

     // reuse the existing flag


    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (player.getEntityWorld().isClient()) {
            // Let the server do the authoritative transfer; no client-side sweeps.
            return ItemStack.EMPTY;
        }
        ItemStack empty = ItemStack.EMPTY;
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return empty;

        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) return empty;

        ItemStack stackInSlot = slot.getStack();   // <- operate on the real stack
        ItemStack original    = stackInSlot.copy();

        final int beEnd    = WINDOW_SLOTS;   // 0..53 window
        final int invStart = beEnd;          // 54..80 player inv
        final int invEnd   = invStart + 27;  // exclusive
        final int hotStart = invEnd;         // 81..89 hotbar
        final int hotEnd   = hotStart + 9;   // exclusive

        CardDBSession sess = (view instanceof CardDBSession) ? (CardDBSession) view : null;

        // WINDOW -> PLAYER
        // (unchanged logic; now guaranteed server-only due to guard)
        if (slotIndex < beEnd) {
            String uid = readUid(stackInSlot);
            suppressWindowOnTake = true;
            if (sess != null) sess.setUiFrozen(true);
            try {
                ItemStack toGive = stackInSlot.copy();
                stackInSlot.setCount(0);
                slot.setStack(ItemStack.EMPTY);
                slot.markDirty();

                giveDbCardToDestination(player, toGive);

                if (uid != null && !uid.isBlank() && sess != null) {
                    sess.removeFromIntakeByUid(uid);
                    sess.compactIntakeAndReprojectSamePage();
                    if (sess.isProjectingSearch()) {
                        reprojectingNow = true; try { reprojectCurrentPage(); } finally { reprojectingNow = false; }
                    }
                }
                sendContentUpdates();
                if (view != null) syncPropsFromView();
            } finally {
                if (sess != null) sess.setUiFrozen(false);
                suppressWindowOnTake = false;
            }
            return original;
        }




        // PLAYER -> WINDOW/DB
        // PLAYER -> DB (append)
        if (!stackInSlot.isOf(ModItems.CARD)) return empty;

        if (!player.getEntityWorld().isClient() && view != null) {
            view.appendToIntake(stackInSlot);
            stackInSlot.setCount(0);

            if (stackInSlot.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else                       slot.markDirty();

            if (sess != null) {
                sess.compactIntakeAndReprojectSamePage();
                if (sess.isProjectingSearch()) {
                    reprojectingNow = true; try { reprojectCurrentPage(); } finally { reprojectingNow = false; }
                }
            }
            sendContentUpdates();
            syncPropsFromView();
            return original;
        }

        // Prefer placing into the visible window first
        boolean placed = this.insertItem(stackInSlot, 0, beEnd, false);
        if (!placed && view != null && !player.getEntityWorld().isClient()) {
            // Append to backing when page is full
            view.appendToIntake(stackInSlot);
            stackInSlot.setCount(0);
            placed = true;
        }
        if (!placed) return empty;

        if (stackInSlot.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else                       slot.markDirty();

        if (!player.getEntityWorld().isClient()) {
            if (view instanceof CardDBSession s) {
                s.compactIntakeAndReprojectSamePage();
            }
            sendContentUpdates();
            if (view != null) syncPropsFromView();
        }
        return original;
    }

    @Override
    public void onContentChanged(Inventory inv) {
        super.onContentChanged(inv);
        if (reprojectingNow) return; // avoid re-entrancy; click paths handle reprojection
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player.getEntityWorld().isClient()) return;

        if (this.view instanceof CardDBSession session
                && player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            var sw = (net.minecraft.server.world.ServerWorld) sp.getEntityWorld();
            var st = PlayerCardDBState.get(sw);
            st.putIntake(sp.getUuid(), CardDBState.snapshotFromList(session.copyIntakeAll()));
        }
    }

    private static final class DeckboxInventoryView implements net.minecraft.inventory.Inventory {
        private final net.minecraft.inventory.SimpleInventory mirror =
                new net.minecraft.inventory.SimpleInventory(com.spider.mtgcard.deckbox.DeckboxBlockEntity.INVENTORY_SIZE);

        private @org.jetbrains.annotations.Nullable com.spider.mtgcard.deckbox.DeckboxBlockEntity target;

        public void setTarget(@org.jetbrains.annotations.Nullable com.spider.mtgcard.deckbox.DeckboxBlockEntity be) {
            this.target = be;
        }

        private net.minecraft.inventory.Inventory live() {
            return (target != null) ? target : mirror;
        }

        @Override public int size() { return live().size(); }
        @Override public boolean isEmpty() { return live().isEmpty(); }
        @Override public ItemStack getStack(int slot) { return live().getStack(slot); }
        @Override public ItemStack removeStack(int slot, int amount) { return live().removeStack(slot, amount); }
        @Override public ItemStack removeStack(int slot) { return live().removeStack(slot); }
        @Override public void setStack(int slot, ItemStack stack) { live().setStack(slot, stack); }
        @Override public void markDirty() { live().markDirty(); }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override public void clear() { live().clear(); }
    }

    private boolean insertIntoActiveDeckboxMainGrid(PlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (deckboxPositions.isEmpty()) return false;

        int tab = props.get(4);
        tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));

        var pos = deckboxPositions.get(tab);
        var be = player.getEntityWorld().getBlockEntity(pos);
        if (!(be instanceof com.spider.mtgcard.deckbox.DeckboxBlockEntity dbe)) return false;

        final int mainGridSlots = DBX_ROWS * DBX_COLS; // 11*9
        for (int i = 0; i < mainGridSlots; i++) {
            ItemStack dst = dbe.getStack(i);

            // empty slot: drop it in
            if (dst.isEmpty()) {
                dbe.setStack(i, stack.copy());
                stack.setCount(0);
                dbe.markDirty();
                return true;
            }

            // merge if possible (cards are usually count 1, but safe anyway)
            if (ItemStack.areItemsAndComponentsEqual(dst, stack) && dst.getCount() < dst.getMaxCount()) {
                int move = Math.min(stack.getCount(), dst.getMaxCount() - dst.getCount());
                if (move > 0) {
                    dst.increment(move);
                    stack.decrement(move);
                    dbe.setStack(i, dst);
                    dbe.markDirty();
                    if (stack.isEmpty()) return true;
                }
            }
        }
        return stack.isEmpty();
    }
    private void giveDbCardToDestination(PlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        boolean wantsDeckbox = (props.get(5) == 1);
        boolean hasDeckbox = !deckboxPositions.isEmpty();

        if (wantsDeckbox && hasDeckbox) {
            ItemStack toPlace = stack.copy();
            boolean ok = insertIntoActiveDeckboxMainGrid(player, toPlace);
            if (ok) return; // inserted fully
            // if failed, fall through to player inv
        }

        boolean merged = player.getInventory().insertStack(stack);
        if (!merged && !stack.isEmpty()) {
            player.dropItem(stack, false);
        }
    }
    private void storeAllFromPlayerInventory(PlayerEntity player) {
        if (view == null) return;
        if (playerInvRef == null) return;

        // main inventory + hotbar only (0..35)
        for (int i = 0; i < 36; i++) {
            ItemStack st = playerInvRef.getStack(i);
            if (st == null || st.isEmpty()) continue;
            if (!st.isOf(ModItems.CARD)) continue;

            // append copies; clear player slot
            view.appendToIntake(st.copy());
            playerInvRef.setStack(i, ItemStack.EMPTY);
        }

        playerInvRef.markDirty();
        sendContentUpdates();
        syncPropsFromView();
    }
}
