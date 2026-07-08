package com.spider.mtgcard.db;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;


public class CardDatabaseScreenHandler extends AbstractContainerMenu {
    public static final int TAB_BASE = 300_000;

    private static final int WINDOW_SLOTS = 54;

    public static final int SCROLL_ROW_UP   = -1;
    public static final int SCROLL_ROW_DOWN = +1;
    public static final int SCROLL_PAGE_UP  = -2;
    public static final int SCROLL_PAGE_DOWN = +2;
    public static final int SCROLL_TOP      = -3;
    public static final int SCROLL_BOTTOM   = +3;

    public static final int SET_OFFSET_BASE = 10_000;
    public static final int DB_INTERACT_BASE = 1_000;
    private static final int DB_INTERACT_STRIDE = 8;
    public static final int DB_INTERACT_LEFT = 0;
    public static final int DB_INTERACT_RIGHT = 1;
    public static final int DB_INTERACT_SHIFT_LEFT = 2;
    public static final int DB_INTERACT_SHIFT_RIGHT = 3;

    private static final int DBX_ROWS = com.spider.mtgcard.deckbox.DeckboxBlockEntity.DECKBOX_ROWS;
    private static final int DBX_COLS = com.spider.mtgcard.deckbox.DeckboxBlockEntity.DECKBOX_COLS;

    public static final int DB_LEFT_GUTTER = 18;
    public static final int DB_TOP_BAR = 18;
    public static final int SLOT_SIZE = 18;

    public static final int DB_ROWS = 6;
    public static final int DB_COLS = 9;

    public static final int DB_INNER_PAD_X = 8;
    public static final int DB_INNER_PAD_Y = 18;

    public static final int DB_GRID_X = DB_INNER_PAD_X + DB_LEFT_GUTTER;
    public static final int DB_GRID_Y = DB_INNER_PAD_Y + DB_TOP_BAR;

    private DeckboxInventoryView deckboxView = null;
    private String activeQuery = "";

    private final Container windowInv;

    @org.jetbrains.annotations.Nullable
    private final CardDBView view;

    @org.jetbrains.annotations.Nullable
    private final CardDBSession session;

    private java.util.List<ItemStack> intakeAll = new java.util.ArrayList<>();
    private com.spider.mtgcard.db.search.ScryfallQuery currentQuery = new com.spider.mtgcard.db.search.ScryfallQuery();
    private java.util.List<com.spider.mtgcard.db.search.SearchEngine.Row> currentView = java.util.List.of();
    private int windowOffset = 0;

    private static final int PROP_INTAKE_COUNT = 0;
    private static final int PROP_WINDOW_OFFSET = 1;
    private static final int PROP_SEARCH_TOTAL = 2;
    private static final int PROP_DECKBOX_COUNT = 3;
    private static final int PROP_ACTIVE_DECKBOX_TAB = 4;
    private static final int PROP_ROUTE_MODE = 5;
    private static final int PROP_SORT_KEY = 6;
    private static final int PROP_SORT_DIR = 7;
    private static final int PROP_SORT_ENABLED = 8;

    private final SimpleContainerData props = new SimpleContainerData(9);
    public static final int BTN_STORE_ALL = 10;
    public static final int BTN_TOGGLE_ROUTE = 11;

    private Inventory playerInvRef;

    private java.util.List<BlockPos> deckboxPositions = java.util.List.of();
    private int activeDeckboxIndex = 0;

    private boolean uiFrozen = false;
    private boolean needsReproject = false;
    private boolean reprojectingNow = false;
    private boolean suppressWindowOnTake = false;

    private java.util.List<String> tabCommander = java.util.List.of();
    private java.util.List<String> tabPartner = java.util.List.of();

    private String activeOrder = "name";
    private String activeDir = "asc";
    private boolean sortPinned = false;

    public int getClientRouteMode() { return props.get(PROP_ROUTE_MODE); }
    public int getLastSearchTotal() { return props.get(PROP_SEARCH_TOTAL); }
    public int getClientIntakeCount() { return props.get(PROP_INTAKE_COUNT); }
    public int getClientWindowOffset() { return props.get(PROP_WINDOW_OFFSET); }
    public int getClientDeckboxCount() { return props.get(PROP_DECKBOX_COUNT); }
    public int getClientActiveDeckboxTab() { return props.get(PROP_ACTIVE_DECKBOX_TAB); }
    public String getClientSortOrder() { return sortKeyFromId(props.get(PROP_SORT_KEY)); }
    public boolean isClientSortAscending() { return props.get(PROP_SORT_DIR) == 0; }
    public boolean isClientSortPinned() { return props.get(PROP_SORT_ENABLED) != 0; }

    public int getClientMaxWindowOffset() {
        return pageAlignedMaxOffset(getClientIntakeCount());
    }

    private static String normalizeSortKey(String order) {
        if (order == null || order.isBlank()) return "name";
        return switch (order.toLowerCase(java.util.Locale.ROOT)) {
            case "mv", "cmc" -> "mv";
            case "price", "usd" -> "price";
            case "type" -> "type";
            case "power" -> "power";
            case "toughness" -> "toughness";
            case "rarity" -> "rarity";
            default -> "name";
        };
    }

    private static String normalizeSortDir(String dir) {
        return (dir != null && dir.equalsIgnoreCase("desc")) ? "desc" : "asc";
    }

    private static int sortKeyToId(String order) {
        return switch (normalizeSortKey(order)) {
            case "mv" -> 1;
            case "price" -> 2;
            case "type" -> 3;
            case "power" -> 4;
            case "toughness" -> 5;
            case "rarity" -> 6;
            default -> 0;
        };
    }

    private static String sortKeyFromId(int id) {
        return switch (id) {
            case 1 -> "mv";
            case 2 -> "price";
            case 3 -> "type";
            case 4 -> "power";
            case 5 -> "toughness";
            case 6 -> "rarity";
            default -> "name";
        };
    }

    private static int pageAlignedMaxOffset(int totalEntries) {
        int rowsTotal = (int) Math.ceil(Math.max(0, totalEntries) / (double) DB_COLS);
        int startRow = Math.max(0, rowsTotal - DB_ROWS);
        return startRow * DB_COLS;
    }

    private static int clampPageOffset(int requestedOffset, int totalEntries) {
        int maxOffset = pageAlignedMaxOffset(totalEntries);
        int clamped = Math.max(0, Math.min(requestedOffset, maxOffset));
        return (clamped / DB_COLS) * DB_COLS;
    }

    private boolean isProjectionActive() {
        return sortPinned || (activeQuery != null && !activeQuery.isBlank());
    }

    private int getProjectionTotalCount() {
        int total = props.get(PROP_SEARCH_TOTAL);
        return Math.max(0, total);
    }

    private int nextOffsetForButton(int currentOffset, int totalEntries, int id) {
        return switch (id) {
            case SCROLL_ROW_UP -> clampPageOffset(currentOffset - DB_COLS, totalEntries);
            case SCROLL_ROW_DOWN -> clampPageOffset(currentOffset + DB_COLS, totalEntries);
            case SCROLL_PAGE_UP -> clampPageOffset(currentOffset - WINDOW_SLOTS, totalEntries);
            case SCROLL_PAGE_DOWN -> clampPageOffset(currentOffset + WINDOW_SLOTS, totalEntries);
            case SCROLL_TOP -> 0;
            case SCROLL_BOTTOM -> pageAlignedMaxOffset(totalEntries);
            default -> clampPageOffset(currentOffset, totalEntries);
        };
    }

    public void sendDeckboxTabNamesTo(ServerPlayer sp) {
        sendTabNamesToClient(sp);
    }

    public CardDatabaseScreenHandler(int syncId, Inventory inv) {
        this(syncId, inv, BlockPos.ZERO);
    }

    public CardDatabaseScreenHandler(int syncId, Inventory inv, BlockPos pos) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session = null;
        this.view = null;
        this.windowInv = new SimpleContainer(54);
        this.deckboxPositions = findNeighborDeckboxes(inv.player.level(), pos);
        init(inv);
    }

    public CardDatabaseScreenHandler(int syncId, Inventory inv, CardDBSession session, BlockPos pos) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session = java.util.Objects.requireNonNull(session);
        this.view = session;
        this.windowInv = session.getWindow();
        this.deckboxPositions = findNeighborDeckboxes(inv.player.level(), pos);
        init(inv);
        session.compactIntakeAndReprojectSamePage();
    }

    public CardDatabaseScreenHandler(int syncId, Inventory inv, CardDBSession session) {
        super(ModScreenHandlers.CARD_DB, syncId);
        this.session = java.util.Objects.requireNonNull(session, "session");
        this.view = session;
        this.windowInv = session.getWindow();
        init(inv);
        session.compactIntakeAndReprojectSamePage();
    }

    private static java.util.List<BlockPos> findNeighborDeckboxes(net.minecraft.world.level.Level world, BlockPos pos) {
        if (world == null) return java.util.List.of();

        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = pos.relative(d);
            var st = world.getBlockState(p);
            if (com.spider.mtgcard.registry.ModBlocks.isDeckbox(st)) {
                out.add(p);
            }
        }
        return out;
    }

    private static String readCustomNameFromCard(ItemStack st) {
        var comp = st.getOrDefault(DataComponents.CUSTOM_DATA, null);
        if (comp == null) return "";
        var nbt = comp.copyTag();
        var compsOpt = nbt.getCompound("components");
        if (compsOpt.isEmpty()) return "";
        var comps = compsOpt.get();
        return comps.getString("minecraft:custom_name").orElse("");
    }

    private static String safeCommanderNameFromDeckbox(com.spider.mtgcard.deckbox.DeckboxBlockEntity be, int slot) {
        if (be == null) return "";
        var st = be.getItem(slot);
        if (st == null || st.isEmpty()) return "";
        String nm = readCustomNameFromCard(st);
        return nm == null ? "" : nm;
    }

    private void sendTabNamesToClient(ServerPlayer sp) {
        if (deckboxPositions.isEmpty()) return;

        java.util.ArrayList<com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.Entry> entries =
                new java.util.ArrayList<>(deckboxPositions.size());

        for (var p : deckboxPositions) {
            var be = sp.level().getBlockEntity(p);
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
                new com.spider.mtgcard.net.payload.DeckboxTabNamesPayload(this.containerId, entries)
        );
    }

    private void guardedReprojectSamePage() {
        if (view == null) return;
        reprojectingNow = true;
        try {
            reprojectCurrentPage();
            broadcastChanges();
            syncPropsFromView();
        } finally {
            reprojectingNow = false;
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput action, Player player) {
        if (isWindowSlot(slotIndex)) {
            if (player.level().isClientSide()) {
                return;
            }

            if (!(this.view instanceof CardDBSession sess)) {
                return;
            }

            if (action == ContainerInput.QUICK_MOVE) {
                handleWindowQuickMove(slotIndex, player, sess);
                return;
            }

            if (action == ContainerInput.PICKUP) {
                handleWindowPickup(slotIndex, button, player, sess);
                return;
            }

            // Treat the visible DB window as a specialized view-only area:
            // only normal pickup and shift-move are supported.
            return;
        }

        super.clicked(slotIndex, button, action, player);
    }

    private boolean isWindowSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < WINDOW_SLOTS;
    }

    private void handleWindowQuickMove(int slotIndex, Player player, CardDBSession sess) {
        Slot slot = (slotIndex < this.slots.size()) ? this.slots.get(slotIndex) : null;
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack clicked = slot.getItem().copy();
        String uid = readUid(clicked);

        this.suppressWindowOnTake = true;
        sess.setUiFrozen(true);
        try {
            slot.set(ItemStack.EMPTY);
            slot.setChanged();

            giveDbCardToDestination(player, clicked);

            if (uid != null && !uid.isBlank()) {
                sess.removeFromIntakeByUid(uid);
            }

            sess.compactIntakeAndReprojectSamePage();
            syncAfterWindowMutation(sess);
        } finally {
            sess.setUiFrozen(false);
            this.suppressWindowOnTake = false;
        }
    }

    private void handleWindowPickup(int slotIndex, int button, Player player, CardDBSession sess) {
        handleTerminalGridAction(slotIndex, button == 1 ? DB_INTERACT_RIGHT : DB_INTERACT_LEFT, player, sess);
    }

    private void handleTerminalGridAction(int slotIndex, int action, Player player, CardDBSession sess) {
        if (slotIndex < 0 || slotIndex >= WINDOW_SLOTS || player == null || sess == null) {
            return;
        }

        ItemStack carried = this.getCarried();
        if (!carried.isEmpty()) {
            if (carried.is(Items.BUNDLE)) {
                if (action == DB_INTERACT_RIGHT || action == DB_INTERACT_SHIFT_RIGHT) {
                    if (dumpCardsFromCarriedBundle(sess, carried, action == DB_INTERACT_SHIFT_RIGHT)) {
                        this.setCarried(carried);
                        syncAfterWindowMutation(sess);
                    }
                }
                return;
            }

            if (!carried.is(ModItems.CARD)) {
                return;
            }

            int moveCount = (action == DB_INTERACT_RIGHT || action == DB_INTERACT_SHIFT_RIGHT)
                    ? 1
                    : carried.getCount();
            if (moveCount <= 0) {
                return;
            }

            ItemStack toStore = carried.copy();
            toStore.setCount(Math.min(moveCount, carried.getCount()));
            clearUid(toStore);
            sess.appendToIntake(toStore);

            carried.shrink(toStore.getCount());
            this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            syncAfterWindowMutation(sess);
            return;
        }

        Slot slot = (slotIndex < this.slots.size()) ? this.slots.get(slotIndex) : null;
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack clicked = slot.getItem().copy();
        String uid = readUid(clicked);
        if (uid == null || uid.isBlank()) {
            return;
        }

        int moveCount = switch (action) {
            case DB_INTERACT_RIGHT, DB_INTERACT_SHIFT_RIGHT -> 1;
            default -> clicked.getCount();
        };

        ItemStack extracted = takeFromDatabase(sess, uid, moveCount);
        if (extracted.isEmpty()) {
            return;
        }

        if (action == DB_INTERACT_SHIFT_LEFT || action == DB_INTERACT_SHIFT_RIGHT) {
            giveDbCardToDestination(player, extracted);
        } else {
            this.setCarried(extracted);
        }
        syncAfterWindowMutation(sess);
    }

    private static String readUid(ItemStack st) {
        var comp = st.getOrDefault(DataComponents.CUSTOM_DATA, null);
        if (comp == null) return "";
        var nbt = comp.copyTag();
        return nbt.getString("mtg_uid").orElse("");
    }

    private static void clearUid(ItemStack stack) {
        var comp = stack.getOrDefault(DataComponents.CUSTOM_DATA, null);
        if (comp == null) return;

        var nbt = comp.copyTag();
        nbt.remove("mtg_uid");

        if (nbt.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    private boolean dumpCardsFromCarriedBundle(CardDBSession sess, ItemStack bundle, boolean dumpAll) {
        if (sess == null || bundle == null || bundle.isEmpty() || !bundle.is(Items.BUNDLE)) {
            return false;
        }

        java.util.List<ItemStack> contents = readBundleContents(bundle);
        if (contents.isEmpty()) {
            return false;
        }

        java.util.ArrayList<ItemStack> toStore = new java.util.ArrayList<>();
        java.util.ArrayList<ItemStack> remaining = new java.util.ArrayList<>();
        boolean movedAny = false;

        for (ItemStack entry : contents) {
            if (entry == null || entry.isEmpty()) continue;

            ItemStack copy = entry.copy();
            if (copy.is(ModItems.CARD) && (dumpAll || !movedAny)) {
                ItemStack card = copy.copy();
                int moveCount = dumpAll ? copy.getCount() : 1;
                card.setCount(moveCount);
                copy.shrink(moveCount);
                clearUid(card);
                toStore.add(card);
                movedAny = true;

                if (!copy.isEmpty()) {
                    remaining.add(copy);
                }
                continue;
            }

            remaining.add(copy);
        }

        if (toStore.isEmpty()) {
            return false;
        }

        sess.appendAllToIntake(toStore);
        writeBundleContents(bundle, remaining);
        return true;
    }

    private static java.util.List<ItemStack> readBundleContents(ItemStack bundle) {
        if (bundle == null || bundle.isEmpty() || !bundle.is(Items.BUNDLE)) {
            return java.util.List.of();
        }

        BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>();
        try {
            java.lang.reflect.Method itemsMethod = BundleContents.class.getMethod("items");
            Object value = itemsMethod.invoke(contents);
            if (value instanceof Iterable<?> iterable) {
                for (Object obj : iterable) {
                    if (obj instanceof ItemStack stack && !stack.isEmpty()) {
                        out.add(stack.copy());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void writeBundleContents(ItemStack bundle, java.util.List<ItemStack> contents) {
        if (bundle == null || bundle.isEmpty() || !bundle.is(Items.BUNDLE)) {
            return;
        }

        java.util.ArrayList<ItemStackTemplate> templates = new java.util.ArrayList<>();
        if (contents != null) {
            for (ItemStack stack : contents) {
                if (stack != null && !stack.isEmpty()) {
                    templates.add(ItemStackTemplate.fromNonEmptyStack(stack.copy()));
                }
            }
        }

        if (templates.isEmpty()) {
            bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        } else {
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));
        }
    }

    private ItemStack takeFromDatabase(CardDBSession sess, String uid, int amount) {
        if (uid == null || uid.isBlank() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        var intake = sess.getIntakeAll();
        for (int i = 0; i < intake.size(); i++) {
            ItemStack backing = intake.get(i);
            if (backing == null || backing.isEmpty()) continue;
            if (!uid.equals(readUid(backing))) continue;

            int moveCount = Math.min(amount, backing.getCount());
            if (moveCount <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack extracted = backing.copy();
            extracted.setCount(moveCount);

            if (moveCount >= backing.getCount()) {
                intake.remove(i);
            } else {
                backing.shrink(moveCount);
                intake.set(i, backing);
            }

            sess.compactIntakeAndReprojectSamePage();
            return extracted;
        }

        return ItemStack.EMPTY;
    }

    private void syncAfterWindowMutation(CardDBSession sess) {
        if (isProjectionActive()) {
            this.reprojectingNow = true;
            try {
                reprojectCurrentPage();
            } finally {
                this.reprojectingNow = false;
            }
        }

        broadcastChanges();
        syncPropsFromView();
    }

    private void syncPropsFromView() {
        if (view == null) return;
        props.set(PROP_INTAKE_COUNT, view.getIntakeCount());
        props.set(PROP_WINDOW_OFFSET, view.getWindowOffset());
        this.broadcastChanges();
    }

    private void syncSortProps() {
        props.set(PROP_SORT_KEY, sortKeyToId(activeOrder));
        props.set(PROP_SORT_DIR, "desc".equals(activeDir) ? 1 : 0);
        props.set(PROP_SORT_ENABLED, sortPinned ? 1 : 0);
        broadcastChanges();
    }

    private void loadSortPreference(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            syncSortProps();
            return;
        }

        var pref = PlayerCardDBState.get(sp.level()).getSortPreference(sp.getUUID());
        this.activeOrder = normalizeSortKey(pref.order());
        this.activeDir = pref.ascending() ? "asc" : "desc";
        this.sortPinned = pref.enabled();
        syncSortProps();
    }

    public void rememberSortPreference(String order, String dir, boolean enabled) {
        this.activeOrder = normalizeSortKey(order);
        this.activeDir = normalizeSortDir(dir);
        this.sortPinned = enabled;

        if (this.playerInvRef != null && this.playerInvRef.player instanceof ServerPlayer sp) {
            PlayerCardDBState.get(sp.level()).putSortPreference(
                    sp.getUUID(),
                    this.activeOrder,
                    "asc".equals(this.activeDir),
                    this.sortPinned
            );
        }

        syncSortProps();
    }

    private void syncDeckboxPropsAndTarget() {
        props.set(3, deckboxPositions.size());
        props.set(4, Math.max(0, Math.min(deckboxPositions.size() - 1, props.get(4))));
        broadcastChanges();
    }

    private void updateDeckboxTargetFromTab(int tabIdx) {
        if (deckboxView == null) return;
        if (view == null || deckboxPositions.isEmpty()) {
            deckboxView.setTarget(null);
            return;
        }
        tabIdx = Math.max(0, Math.min(deckboxPositions.size() - 1, tabIdx));
    }

    private void syncDeckboxProps() {
        props.set(3, deckboxPositions.size());
        int tab = props.get(4);
        if (deckboxPositions.isEmpty()) {
            props.set(4, 0);
        } else {
            props.set(4, Math.max(0, Math.min(deckboxPositions.size() - 1, tab)));
        }
        broadcastChanges();
    }

    private void setDeckboxTarget(Player player, int tabIdx) {
        if (deckboxView == null) return;
        if (deckboxPositions.isEmpty()) {
            deckboxView.setTarget(null);
            return;
        }
        tabIdx = Math.max(0, Math.min(deckboxPositions.size() - 1, tabIdx));
        var pos = deckboxPositions.get(tabIdx);
        var be = player.level().getBlockEntity(pos);
        if (be instanceof com.spider.mtgcard.deckbox.DeckboxBlockEntity dbe) {
            deckboxView.setTarget(dbe);
        } else {
            deckboxView.setTarget(null);
        }
    }

    private void init(Inventory playerInv) {
        this.playerInvRef = playerInv;

        this.addDataSlots(props);
        loadSortPreference(playerInv.player);

        props.set(PROP_DECKBOX_COUNT, deckboxPositions.size());
        props.set(PROP_ROUTE_MODE, 0);

        if (deckboxPositions.isEmpty()) props.set(PROP_ROUTE_MODE, 0);

        int tab = props.get(PROP_ACTIVE_DECKBOX_TAB);
        if (deckboxPositions.isEmpty()) {
            tab = 0;
        } else {
            tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));
        }
        props.set(PROP_ACTIVE_DECKBOX_TAB, tab);

        final int x0 = DB_GRID_X;
        final int y0 = DB_GRID_Y;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r * 9 + c;
                int sx = x0 + c * SLOT_SIZE;
                int sy = y0 + r * SLOT_SIZE;

                this.addSlot(new Slot(this.windowInv, idx, sx, sy) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public void onQuickCraft(ItemStack newStack, ItemStack original) {
                    }

                    @Override
                    public void onTake(Player player, ItemStack taken) {
                        if (player.level().isClientSide()) return;
                        if (suppressWindowOnTake) return;

                        String uid = readUid(taken);
                        if (!uid.isEmpty() && view instanceof CardDBSession s) {
                            s.removeFromIntakeByUid(uid);
                            s.compactIntakeAndReprojectSamePage();
                            if (isProjectionActive()) {
                                reprojectingNow = true;
                                try {
                                    reprojectCurrentPage();
                                } finally {
                                    reprojectingNow = false;
                                }
                            }
                        }
                    }

                    @Override
                    public ItemStack remove(int amount) {
                        ItemStack cur = this.getItem();
                        if (cur.isEmpty()) return ItemStack.EMPTY;

                        int n = Math.min(amount, cur.getCount());
                        ItemStack out = cur.copy();
                        out.setCount(n);

                        this.set(ItemStack.EMPTY);
                        this.setChanged();
                        return out;
                    }
                });
            }
        }

        final int invX = DB_GRID_X;
        final int invY0 = 166;
        final int invRowStep = 19;

        for (int r = 0; r < 3; r++) {
            int y = invY0 + r * invRowStep;
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(playerInv, c + r * 9 + 9, invX + c * 18, y));
            }
        }

        final int hotbarY = 227;
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(playerInv, c, invX + c * 18, hotbarY));
        }

        if (!deckboxPositions.isEmpty()) {
            if (deckboxView == null) deckboxView = new DeckboxInventoryView();

            if (!playerInv.player.level().isClientSide()) {
                props.set(PROP_ACTIVE_DECKBOX_TAB, 0);
                setDeckboxTarget(playerInv.player, 0);

                if (playerInv.player instanceof ServerPlayer sp) {
                    sendTabNamesToClient(sp);
                }
            }

            final int panelX = 193 + 14;
            final int dx0 = panelX + 16;
            final int dy0 = 29;

            for (int r = 0; r < DBX_ROWS; r++) {
                for (int c = 0; c < DBX_COLS; c++) {
                    int idx = r * DBX_COLS + c;
                    int sx = dx0 + c * 18;
                    int sy = dy0 + r * 18;

                    this.addSlot(new Slot(deckboxView, idx, sx, sy) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return stack.is(ModItems.CARD);
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return true;
                        }
                    });
                }
            }

            int sideX = dx0 + DBX_COLS * 18 + 3;
            int sideY = dy0;

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.FIRST_SIDE_SLOT, sideX, sideY) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.BUNDLE);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return true;
                }
            });

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.SECOND_SIDE_SLOT, sideX, sideY + 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.CARD);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return true;
                }
            });

            this.addSlot(new Slot(deckboxView, com.spider.mtgcard.deckbox.DeckboxBlockEntity.THIRD_SIDE_SLOT, sideX, sideY + 36) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.CARD);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return true;
                }
            });
        }

        if (view != null) {
            props.set(PROP_INTAKE_COUNT, view.getIntakeCount());
            props.set(PROP_WINDOW_OFFSET, view.getWindowOffset());
        }

        if (!playerInv.player.level().isClientSide() && view != null && sortPinned) {
            view.setWindowOffset(0);
            reprojectCurrentPage();
        }

        this.broadcastChanges();
    }

    public void setActiveQuery(String q) {
        this.activeQuery = (q == null) ? "" : q;
    }

    public void applySearch(String q, String order, String dir) {
        if (this.view == null) return;

        setActiveQuery(q);
        this.activeOrder = normalizeSortKey(order);
        this.activeDir = normalizeSortDir(dir);
        syncSortProps();

        if (!isProjectionActive()) {
            view.clearSearchProjection();
            props.set(PROP_SEARCH_TOTAL, -1);
            view.setWindowOffset(0);
            props.set(PROP_WINDOW_OFFSET, 0);
            syncPropsFromView();
            return;
        }

        view.setWindowOffset(0);
        props.set(PROP_WINDOW_OFFSET, 0);
        reprojectCurrentPage();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (view == null || player.level().isClientSide()) return false;

        if (id >= DB_INTERACT_BASE && id < DB_INTERACT_BASE + WINDOW_SLOTS * DB_INTERACT_STRIDE) {
            int encoded = id - DB_INTERACT_BASE;
            int slot = encoded / DB_INTERACT_STRIDE;
            int action = encoded % DB_INTERACT_STRIDE;
            if (view instanceof CardDBSession sess) {
                handleTerminalGridAction(slot, action, player, sess);
            }
            return true;
        }

        if (id >= TAB_BASE && id < TAB_BASE + 1000) {
            int tab = id - TAB_BASE;
            if (deckboxPositions.isEmpty()) return false;

            tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));
            props.set(PROP_ACTIVE_DECKBOX_TAB, tab);
            if (deckboxPositions.isEmpty()) props.set(PROP_ROUTE_MODE, 0);

            setDeckboxTarget(player, tab);
            if (player instanceof ServerPlayer sp) {
                sendTabNamesToClient(sp);
            }
            syncDeckboxProps();
            return true;
        }

        if (id >= SET_OFFSET_BASE) {
            int requestedOffset = id - SET_OFFSET_BASE;
            if (isProjectionActive()) {
                view.setWindowOffset(clampPageOffset(requestedOffset, getProjectionTotalCount()));
                reprojectCurrentPage();
            } else {
                view.setWindowOffset(requestedOffset);
                syncPropsFromView();
            }
            return true;
        }

        if (id == BTN_STORE_ALL) {
            storeAllFromPlayerInventory(player);
            return true;
        }

        if (id == BTN_TOGGLE_ROUTE) {
            if (deckboxPositions.isEmpty()) {
                props.set(PROP_ROUTE_MODE, 0);
                broadcastChanges();
                return true;
            }
            props.set(PROP_ROUTE_MODE, (props.get(PROP_ROUTE_MODE) == 1) ? 0 : 1);
            broadcastChanges();
            return true;
        }

        if (id == SCROLL_ROW_UP || id == SCROLL_ROW_DOWN || id == SCROLL_PAGE_UP
                || id == SCROLL_PAGE_DOWN || id == SCROLL_TOP || id == SCROLL_BOTTOM) {
            if (isProjectionActive()) {
                int nextOffset = nextOffsetForButton(view.getWindowOffset(), getProjectionTotalCount(), id);
                view.setWindowOffset(nextOffset);
                reprojectCurrentPage();
            } else {
                switch (id) {
                    case SCROLL_ROW_UP -> view.shiftWindow(-DB_COLS);
                    case SCROLL_ROW_DOWN -> view.shiftWindow(+DB_COLS);
                    case SCROLL_PAGE_UP -> view.shiftWindow(-WINDOW_SLOTS);
                    case SCROLL_PAGE_DOWN -> view.shiftWindow(+WINDOW_SLOTS);
                    case SCROLL_TOP -> view.setWindowOffset(0);
                    case SCROLL_BOTTOM -> view.setWindowOffset(view.getMaxWindowOffset());
                    default -> {
                    }
                }

                props.set(PROP_SEARCH_TOTAL, -1);
                broadcastChanges();
                syncPropsFromView();
            }
            return true;
        }

        return false;
    }

    public com.spider.mtgcard.db.search.SearchResults runSearch(
            com.spider.mtgcard.db.search.Filters f,
            String q,
            com.spider.mtgcard.db.search.PageCursor cursor
    ) {
        var res = new com.spider.mtgcard.db.search.SearchResults();
        if (view == null) {
            res.total = 0;
            res.items = java.util.List.of();
            res.next = null;
            return res;
        }

        final int offset = (cursor != null) ? cursor.offset() : 0;
        final int limit = 54;
        final String order = activeOrder;
        final String dir = activeDir;

        var parsed = com.spider.mtgcard.db.search.ScryfallQuery.parse((q == null ? "" : q), limit, offset, order, dir);
        var page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);

        var items = new java.util.ArrayList<com.spider.mtgcard.db.search.IndexRecord>(page.items().size());
        for (var row : page.items()) {
            items.add(com.spider.mtgcard.db.search.SearchEngine.toIndexRecord(row));
        }

        res.total = page.total();
        res.items = items;
        res.next = (page.nextOffset() >= 0) ? new com.spider.mtgcard.db.search.PageCursor(page.nextOffset(), limit) : null;
        return res;
    }

    private void rebuildAndProject() {
        currentView = buildViewRows(intakeAll, currentQuery);
        int maxOffset = Math.max(0, currentView.size() - 54);
        if (windowOffset > maxOffset) windowOffset = maxOffset;
        session.projectWindow(currentView, windowOffset);
        this.broadcastChanges();
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
                (activeQuery == null ? "" : activeQuery), limit, offset, activeOrder, activeDir
        );
        var page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);

        int clampedOffset = clampPageOffset(offset, page.total());
        if (clampedOffset != offset) {
            view.setWindowOffset(clampedOffset);
            offset = clampedOffset;
            parsed = com.spider.mtgcard.db.search.ScryfallQuery.parse(
                    (activeQuery == null ? "" : activeQuery), limit, offset, activeOrder, activeDir
            );
            page = com.spider.mtgcard.db.search.SearchEngine.search(view.copyIntakeAll(), parsed);
        }

        var toShow = new java.util.ArrayList<ItemStack>(Math.min(54, page.items().size()));
        for (var row : page.items()) {
            toShow.add(row.stack == null ? ItemStack.EMPTY : row.stack.copy());
        }

        view.projectSearchResults(toShow);
        props.set(PROP_SEARCH_TOTAL, page.total());
        broadcastChanges();
        syncPropsFromView();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (player.level().isClientSide()) {
            return ItemStack.EMPTY;
        }

        ItemStack empty = ItemStack.EMPTY;
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return empty;

        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return empty;

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        final int beEnd = WINDOW_SLOTS;

        CardDBSession sess = (view instanceof CardDBSession) ? (CardDBSession) view : null;

        if (slotIndex < beEnd) {
            String uid = readUid(stackInSlot);
            suppressWindowOnTake = true;
            if (sess != null) sess.setUiFrozen(true);

            try {
                ItemStack toGive = stackInSlot.copy();
                stackInSlot.setCount(0);
                slot.set(ItemStack.EMPTY);
                slot.setChanged();

                giveDbCardToDestination(player, toGive);

                if (uid != null && !uid.isBlank() && sess != null) {
                    sess.removeFromIntakeByUid(uid);
                    sess.compactIntakeAndReprojectSamePage();
                    if (isProjectionActive()) {
                        reprojectingNow = true;
                        try {
                            reprojectCurrentPage();
                        } finally {
                            reprojectingNow = false;
                        }
                    }
                }

                broadcastChanges();
                if (view != null) syncPropsFromView();
            } finally {
                if (sess != null) sess.setUiFrozen(false);
                suppressWindowOnTake = false;
            }
            return original;
        }

        if (!stackInSlot.is(ModItems.CARD)) return empty;

        if (!player.level().isClientSide() && view != null) {
            view.appendToIntake(stackInSlot);
            stackInSlot.setCount(0);

            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (sess != null) {
                sess.compactIntakeAndReprojectSamePage();
                if (isProjectionActive()) {
                    reprojectingNow = true;
                    try {
                        reprojectCurrentPage();
                    } finally {
                        reprojectingNow = false;
                    }
                }
            }

            broadcastChanges();
            syncPropsFromView();
            return original;
        }

        boolean placed = this.moveItemStackTo(stackInSlot, 0, beEnd, false);
        if (!placed && view != null && !player.level().isClientSide()) {
            view.appendToIntake(stackInSlot);
            stackInSlot.setCount(0);
            placed = true;
        }
        if (!placed) return empty;

        if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        if (!player.level().isClientSide()) {
            if (view instanceof CardDBSession s) {
                s.compactIntakeAndReprojectSamePage();
                if (isProjectionActive()) {
                    reprojectCurrentPage();
                }
            }
            broadcastChanges();
            if (view != null) syncPropsFromView();
        }

        return original;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (reprojectingNow) return;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;

        if (this.view instanceof CardDBSession session && player instanceof ServerPlayer sp) {
            var sw = sp.level();
            var st = PlayerCardDBState.get(sw);
            st.putIntake(sp.getUUID(), CardDBState.snapshotFromList(session.copyIntakeAll()));
        }
    }

    private static final class DeckboxInventoryView implements Container {
        private final SimpleContainer mirror =
                new SimpleContainer(com.spider.mtgcard.deckbox.DeckboxBlockEntity.INVENTORY_SIZE);

        private @org.jetbrains.annotations.Nullable com.spider.mtgcard.deckbox.DeckboxBlockEntity target;

        public void setTarget(@org.jetbrains.annotations.Nullable com.spider.mtgcard.deckbox.DeckboxBlockEntity be) {
            this.target = be;
        }

        private Container live() {
            return (target != null) ? target : mirror;
        }

        @Override public int getContainerSize() { return live().getContainerSize(); }
        @Override public boolean isEmpty() { return live().isEmpty(); }
        @Override public ItemStack getItem(int slot) { return live().getItem(slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return live().removeItem(slot, amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return live().removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, ItemStack stack) { live().setItem(slot, stack); }
        @Override public void setChanged() { live().setChanged(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { live().clearContent(); }
    }

    private boolean insertIntoActiveDeckboxMainGrid(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (deckboxPositions.isEmpty()) return false;

        int tab = props.get(4);
        tab = Math.max(0, Math.min(deckboxPositions.size() - 1, tab));

        var pos = deckboxPositions.get(tab);
        var be = player.level().getBlockEntity(pos);
        if (!(be instanceof com.spider.mtgcard.deckbox.DeckboxBlockEntity dbe)) return false;

        final int mainGridSlots = DBX_ROWS * DBX_COLS;
        for (int i = 0; i < mainGridSlots; i++) {
            ItemStack dst = dbe.getItem(i);

            if (dst.isEmpty()) {
                dbe.setItem(i, stack.copy());
                stack.setCount(0);
                dbe.setChanged();
                return true;
            }

            if (ItemStack.isSameItemSameComponents(dst, stack) && dst.getCount() < dst.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), dst.getMaxStackSize() - dst.getCount());
                if (move > 0) {
                    dst.grow(move);
                    stack.shrink(move);
                    dbe.setItem(i, dst);
                    dbe.setChanged();
                    if (stack.isEmpty()) return true;
                }
            }
        }
        return stack.isEmpty();
    }

    private void giveDbCardToDestination(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        boolean wantsDeckbox = (props.get(5) == 1);
        boolean hasDeckbox = !deckboxPositions.isEmpty();

        if (wantsDeckbox && hasDeckbox) {
            ItemStack toPlace = stack.copy();
            boolean ok = insertIntoActiveDeckboxMainGrid(player, toPlace);
            if (ok) return;
        }

        boolean merged = player.getInventory().add(stack);
        if (!merged && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private void storeAllFromPlayerInventory(Player player) {
        if (view == null) return;
        if (playerInvRef == null) return;

        java.util.ArrayList<ItemStack> toStore = new java.util.ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack st = playerInvRef.getItem(i);
            if (st == null || st.isEmpty()) continue;
            if (!st.is(ModItems.CARD)) continue;

            toStore.add(st.copy());
            playerInvRef.setItem(i, ItemStack.EMPTY);
        }

        if (toStore.isEmpty()) return;

        view.appendAllToIntake(toStore);
        playerInvRef.setChanged();
        if (isProjectionActive()) {
            reprojectCurrentPage();
            return;
        }
        broadcastChanges();
        syncPropsFromView();
    }
}
