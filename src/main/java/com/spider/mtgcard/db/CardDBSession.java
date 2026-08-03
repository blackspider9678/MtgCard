// com/spider/mtgcard/db/CardDBSession.java
package com.spider.mtgcard.db;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.api.CardDatabaseCards;
import com.spider.mtgcard.db.search.SearchEngine;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.util.StackData;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Per-player logical DB (infinite list + 54-slot window + scroll). */
public final class CardDBSession implements CardDBView {
    private static final int ROWS=6, COLS=9, PAGE=ROWS*COLS;
    private static final long SLOW_LOAD_LOG_MS = 1_000L;

    private final ServerLevel world;
    private final UUID playerId;

    private final List<ItemStack> intakeAll = new ArrayList<>();
    private int windowOffset = 0;
    private boolean projectingSearch = false;
    // add this field near the other booleans
    private boolean suppressPersistOnce = false;
    private boolean syncingWindow = false;

    private UUID owner;

    private boolean uiFrozen = false;
    private boolean needsReproject = false;


    public List<ItemStack> getIntakeAll() { return intakeAll; }

    public net.minecraft.world.SimpleContainer getWindow() {
        return window;
    }

    /** Packs non-empty window stacks to the left/up, then fills from backing so the page is continuous. */
    // Add/replace this helper
    private void compactWindowLocalAndTopUpFromBacking() {
        // Silence markDirty side-effects while we rewrite window slots
        syncingWindow = true;
        try {
            final ArrayList<ItemStack> kept = new ArrayList<>(PAGE);
            for (int i = 0; i < PAGE; i++) {
                ItemStack st = window.getItem(i);
                if (st != null && !st.isEmpty()) kept.add(st);
            }

            for (int i = 0; i < PAGE; i++) window.setItem(i, ItemStack.EMPTY);

            int pos = 0;
            for (ItemStack st : kept) window.setItem(pos++, st);

            int startIdx = windowOffset + kept.size();
            while (pos < PAGE) {
                ItemStack src = (startIdx < intakeAll.size()) ? intakeAll.get(startIdx++).copy() : ItemStack.EMPTY;
                window.setItem(pos++, src);
            }
        } finally {
            syncingWindow = false;                  // re-enable markDirty
        }
        // IMPORTANT: do NOT call window.markDirty() here.
        // The caller (markDirty below) will notify once, after writeback/persist.
    }

    // CardDBSession.java
    public void setUiFrozen(boolean frozen) {
        this.uiFrozen = frozen;
        if (!frozen && needsReproject) {
            needsReproject = false;
            applyBackingToWindow();          // redraw now that we’re unfrozen
            // do a notify without side effects
            suppressPersistOnce = true;
            window.setChanged();
        }
    }

    private final net.minecraft.world.SimpleContainer window =
            new net.minecraft.world.SimpleContainer(54) {
                @Override public boolean canPlaceItem(int slot, ItemStack stack) {
                    return stack.is(ModItemTags.TCG_CARD);
                }
                @Override
                public void setChanged() {
                    // 1) Ignore during bulk programmatic writes
                    if (syncingWindow) {
                        return;
                    }

                    // 2) One-shot suppression: skip side-effects but STILL notify listeners to refresh UI
                    if (suppressPersistOnce) {
                        suppressPersistOnce = false;
                        super.setChanged();       // <-- this notifies the client to redraw
                        return;
                    }

                    // 3) Normal: view-only window, never mirror window -> backing here
                    super.setChanged();
                }

                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            };

    // Project rows at [windowOffset .. windowOffset+53] into window
    public void projectWindow(List<SearchEngine.Row> view, int windowOffset) {
        if (uiFrozen) { needsReproject = true; return; }
        if (syncingWindow) return;
        syncingWindow = true;
        try {
            for (int i = 0; i < 54; i++) {
                int idx = windowOffset + i;
                var st = (idx >= 0 && idx < view.size()) ? view.get(idx).stack : ItemStack.EMPTY;
                window.setItem(i, st.isEmpty() ? ItemStack.EMPTY : st.copy());
            }
        } finally {
            syncingWindow = false;
        }
        // notify the client without any write-back side effects
        suppressPersistOnce = true;
        window.setChanged();
    }


    /** Removes all EMPTY entries from the backing list, clamps offset, redraws the same page. */
    /** Removes all EMPTY entries from the backing list, clamps offset, redraws the same page. */
    public void compactIntakeAndReprojectSamePage() {
        if (uiFrozen) { needsReproject = true; persist(); return; }
        normalizeStorage();
        this.windowOffset = clampOffset(this.windowOffset);
        if (projectingSearch) {
            persist();
            return;
        }
        // quiet redraw only
        reprojectQuietly();
        // persist explicit (no window.markDirty side-effects)
        persist();
    }

    /** Redraw current page into the 6×9 window without writing back or persisting. */
    private void reprojectQuietly() {
        if (uiFrozen) { needsReproject = true; return; }
        syncingWindow = true;
        try {
            for (int i = 0; i < PAGE; i++) {
                int idx = windowOffset + i;
                ItemStack st = (idx >= 0 && idx < intakeAll.size()) ? intakeAll.get(idx) : ItemStack.EMPTY;
                window.setItem(i, st.copy());
            }
            // prevent markDirty side-effects once
            suppressPersistOnce = true;
            window.setChanged();
        } finally {
            syncingWindow = false;
        }
    }


    /** ensure any appended card has a unique mtg_uid */
    // CardDBSession.java
    private static void ensureUid(ItemStack st) {
        if (st == null || st.isEmpty()) return;

        // Read current CUSTOM_DATA (don’t lose other fields the preview/manager needs!)
        var comp = st.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, null);
        String uid = "";
        net.minecraft.nbt.CompoundTag nbtExisting = null;
        if (comp != null) {
            nbtExisting = comp.copyTag();
            uid = nbtExisting.getString("mtg_uid").orElse("");
        }

        if (uid == null || uid.isBlank()) {
            // MERGE: reuse existing NBT if present, otherwise start new
            net.minecraft.nbt.CompoundTag nbt = (nbtExisting != null) ? nbtExisting : new net.minecraft.nbt.CompoundTag();
            nbt.putString("mtg_uid", UUID.randomUUID().toString());
            st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    CustomData.of(nbt));
        }
    }


    private static String readUid(ItemStack st) {
        var comp = st.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, null);
        if (comp == null) return "";
        var nbt = comp.copyTag();
        return nbt.getString("mtg_uid").orElse("");
    }

    public void compactAndTopUp(List<SearchEngine.Row> view, int windowOffset) {
        if (syncingWindow) return;
        syncingWindow = true;
        try {
            // 1) collect non-empty in order
            ArrayList<ItemStack> kept = new ArrayList<>(54);
            for (int i = 0; i < 54; i++) {
                var st = window.getItem(i);
                if (st != null && !st.isEmpty()) kept.add(st);
            }

            // 2) clear
            for (int i = 0; i < 54; i++) window.setItem(i, ItemStack.EMPTY);

            // 3) put back kept first
            int pos = 0;
            for (var st : kept) window.setItem(pos++, st);

            // 4) top-up from backing view so page stays continuous
            int startIdx = windowOffset + kept.size();
            while (pos < 54) {
                ItemStack fill = ItemStack.EMPTY;
                if (startIdx < view.size()) {
                    var row = view.get(startIdx++);
                    if (row != null && row.stack != null && !row.stack.isEmpty()) {
                        fill = row.stack.copy();
                    }
                }
                window.setItem(pos++, fill);
            }

            window.setChanged();
        } finally {
            syncingWindow = false;
        }
    }

    @Override
    public boolean removeFromIntakeByUid(String uid) {
        if (uid == null || uid.isBlank()) return false;
        for (int i = 0; i < intakeAll.size(); i++) {
            var s = intakeAll.get(i);
            if (uid.equals(readUid(s))) {
                intakeAll.remove(i);
                return true;
            }
        }
        return false;
    }

    public static CardDBSession forPlayer(ServerPlayer player) {
        var sw = player.level();
        var s = new CardDBSession(sw, player.getUUID());
        s.ensureLoaded(player);
        return s;
    }

    private CardDBSession(ServerLevel world, UUID playerId) {
        this.world = world;
        this.playerId = playerId;
    }

    /* ---------- Persistence ---------- */

    private void load() {
        long startedNs = System.nanoTime();
        var state = PlayerCardDBState.get(world);
        var list = state.getIntake(playerId);
        this.intakeAll.clear();
        if (list != null) CardDBState.applyIntakeToList(this.intakeAll, list);
        int loaded = this.intakeAll.size();
        boolean changed = normalizeStorage();
        this.windowOffset = 0;
        this.projectingSearch = false;
        if (changed) persist();
        logSlowLoad("load", loaded, changed, startedNs);
    }

    public void ensureLoaded(ServerPlayer sp) {
        if (!(world instanceof ServerLevel sw)) return;

        long startedNs = System.nanoTime();
        this.owner = sp.getUUID();
        var st = PlayerCardDBState.get(sw);
        var saved = st.getIntake(owner);

        this.intakeAll.clear();
        CardDBState.applyIntakeToList(this.intakeAll, saved);
        int loaded = this.intakeAll.size();
        boolean changed = normalizeStorage();
        this.windowOffset = 0;
        this.projectingSearch = false;
        applyBackingToWindow();
        this.window.setChanged();
        if (changed) persist();
        logSlowLoad("ensureLoaded", loaded, changed, startedNs);
    }

    private void persist() {
        var state = PlayerCardDBState.get(world);
        ListTag snap = CardDBState.snapshotFromList(this.intakeAll);
        state.putIntake(playerId, snap);
    }

    /* ---------- View impl ---------- */
    @Override public int getIntakeCount() { return intakeAll.size(); }
    @Override public int getWindowOffset() { return windowOffset; }

    @Override
    public int getMaxWindowOffset() {
        int rowsTotal = (int)Math.ceil(intakeAll.size() / (double) COLS);
        int startRow = Math.max(0, rowsTotal - ROWS);
        return startRow * COLS;
    }

    private int clampOffset(int off) {
        off = Math.max(0, Math.min(off, getMaxWindowOffset()));
        return (off / COLS) * COLS;
    }

    @Override
    public void setWindowOffset(int off) {
        int newOff = clampOffset(off);
        if (newOff == this.windowOffset) return;
        this.windowOffset = newOff;
        if (!projectingSearch) {
            applyBackingToWindow();
        }
    }

    @Override public void shiftWindow(int delta) { setWindowOffset(windowOffset + delta); }
    @Override public Container getWindowInventory() { return window; }

    @Override
    public void appendToIntake(ItemStack stack) {
        if (stack == null) return;
        appendAllToIntake(java.util.List.of(stack));
    }

    @Override
    public void appendAllToIntake(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return;

        boolean anchoredToBottom = (this.windowOffset == getMaxWindowOffset());
        int added = 0;

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !stack.is(ModItemTags.TCG_CARD)) continue;

            if (addToGroupedStorage(stack)) added++;
        }

        if (added == 0) return;
        if (anchoredToBottom) this.windowOffset = getMaxWindowOffset();

        if (!projectingSearch) applyBackingToWindow();
        persist();
        window.setChanged();
    }

    public ItemStack takeOneLike(ItemStack visibleStack) {
        if (visibleStack == null || visibleStack.isEmpty()) return ItemStack.EMPTY;

        String key = CardDatabaseCards.databaseKey(visibleStack);
        if (key.isBlank()) return ItemStack.EMPTY;

        for (int i = 0; i < intakeAll.size(); i++) {
            ItemStack stored = intakeAll.get(i);
            if (stored == null || stored.isEmpty()) continue;
            if (!key.equals(CardDatabaseCards.databaseKey(stored))) continue;
            return takeOneAtIndex(i);
        }

        return ItemStack.EMPTY;
    }

    /** Remove by UID then redraw same page. */
    public void removeByUidAndReprojectSamePage(String uid) {
        if (uid == null || uid.isBlank()) return;
        if (!removeFromIntakeByUid(uid)) return;
        this.windowOffset = clampOffset(this.windowOffset);
        reprojectQuietly();
        persist();
    }

    /** Removes the backing entry that corresponds to a visible window slot (0..53)
     *  when NOT in search projection. Then redraws the same page.
     */
    /** Removes the backing entry that corresponds to a visible window slot (0..53) when NOT in search projection. */
    public boolean removeAtWindowSlot(int windowSlot) {
        if (projectingSearch) return false;
        if (windowSlot < 0 || windowSlot >= PAGE) return false;

        int idx = windowOffset + windowSlot;
        if (idx < 0 || idx >= intakeAll.size()) return false;

        ItemStack removed = takeOneAtIndex(idx);

        // takeOneAtIndex redraws and persists the same page.
        return removed != null && !removed.isEmpty();
    }



    @Override public boolean isProjectingSearch() { return projectingSearch; }

    @Override
    public List<ItemStack> copyIntakeAll() {
        var out = new ArrayList<ItemStack>(intakeAll.size());
        for (var st : intakeAll) out.add(st == null ? ItemStack.EMPTY : st.copy());
        return out;
    }

    @Override
    public void clearSearchProjection() {
        projectingSearch = false;
        applyBackingToWindow();
        window.setChanged();
    }

    /* ---------- Internal helpers ---------- */
    private void applyBackingToWindow() {
        if (uiFrozen) { needsReproject = true; return; }
        syncingWindow = true;
        try {
            for (int i=0;i<PAGE;i++) {
                int idx = windowOffset + i;
                ItemStack st = (idx>=0 && idx<intakeAll.size()) ? intakeAll.get(idx) : ItemStack.EMPTY;
                window.setItem(i, st.copy());
            }
        } finally {
            syncingWindow = false;
        }
        // notify the client; no persistence
        suppressPersistOnce = true;
        window.setChanged();
    }


    private void applyWindowToBacking() {
        int need = windowOffset + PAGE;
        while (intakeAll.size() < need) intakeAll.add(ItemStack.EMPTY);
        for (int i=0;i<PAGE;i++) {
            int idx = windowOffset + i;
            intakeAll.set(idx, window.getItem(i).copy());
        }
        int trim = intakeAll.size()-1;
        while (trim >= 0 && intakeAll.get(trim).isEmpty()) trim--;
        intakeAll.subList(trim+1, intakeAll.size()).clear();
        this.windowOffset = clampOffset(this.windowOffset);
    }

    private boolean normalizeStorage() {
        if (intakeAll.isEmpty()) return false;

        ArrayList<ItemStack> original = new ArrayList<>(intakeAll);
        LinkedHashMap<String, ItemStack> grouped = new LinkedHashMap<>(Math.max(16, original.size()));
        intakeAll.clear();

        boolean changed = false;
        for (ItemStack stack : original) {
            if (stack == null || stack.isEmpty() || !stack.is(ModItemTags.TCG_CARD)) {
                changed = true;
                continue;
            }

            long count = CardDatabaseCards.databaseCount(stack);
            if (count <= 0L) {
                changed = true;
                continue;
            }

            ItemStack storedCopy = CardDatabaseCards.copyForDatabase(stack, count);
            if (storedCopy.isEmpty()) {
                changed = true;
                continue;
            }

            String key = CardDatabaseCards.databaseKey(storedCopy);
            if (key.isBlank()) {
                changed = true;
                continue;
            }

            ItemStack existing = grouped.get(key);
            if (existing == null) {
                grouped.put(key, storedCopy);
            } else {
                long merged = CardDatabaseCards.saturatedAdd(CardDatabaseCards.databaseCount(existing), count);
                CardDatabaseCards.setDatabaseCount(existing, merged);
                changed = true;
            }
        }

        intakeAll.addAll(grouped.values());

        if (original.size() != intakeAll.size()) return true;
        for (int i = 0; i < original.size(); i++) {
            ItemStack before = original.get(i);
            ItemStack after = intakeAll.get(i);
            if (!CardDatabaseCards.databaseKey(before).equals(CardDatabaseCards.databaseKey(after))) return true;
            if (CardDatabaseCards.databaseCount(before) != CardDatabaseCards.databaseCount(after)) return true;
            if (before.getCount() != 1) return true;
            if (!readUid(before).isBlank()) return true;
            if (!hasCachedDatabaseFields(before)) return true;
        }
        return changed;
    }

    private static boolean hasCachedDatabaseFields(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var root = StackData.readCustom(stack);
        return root.getLong(CardDatabaseCards.DB_COUNT_KEY).orElse(0L) > 0L
                && !root.getString(CardDatabaseCards.DB_STACK_KEY).orElse("").isBlank();
    }

    private boolean addToGroupedStorage(ItemStack source) {
        if (source == null || source.isEmpty() || !source.is(ModItemTags.TCG_CARD)) return false;

        long count = CardDatabaseCards.databaseCount(source);
        if (count <= 0L) return false;

        ItemStack storedCopy = CardDatabaseCards.copyForDatabase(source, count);
        if (storedCopy.isEmpty()) return false;

        String key = CardDatabaseCards.databaseKey(storedCopy);
        if (key.isBlank()) return false;

        for (int i = 0; i < intakeAll.size(); i++) {
            ItemStack existing = intakeAll.get(i);
            if (existing == null || existing.isEmpty()) continue;
            if (!key.equals(CardDatabaseCards.databaseKey(existing))) continue;

            long merged = CardDatabaseCards.saturatedAdd(CardDatabaseCards.databaseCount(existing), count);
            CardDatabaseCards.setDatabaseCount(existing, merged);
            intakeAll.set(i, existing);
            return true;
        }

        intakeAll.add(storedCopy);
        return true;
    }

    private void logSlowLoad(String stage, int loadedCount, boolean changed, long startedNs) {
        long elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L;
        if (elapsedMs >= SLOW_LOAD_LOG_MS) {
            Mtgcard.LOGGER.warn("[MTGCard] Card Database {} took {} ms (loaded={}, stored={}, changed={})",
                    stage, elapsedMs, loadedCount, intakeAll.size(), changed);
        }
    }

    private ItemStack takeOneAtIndex(int index) {
        if (index < 0 || index >= intakeAll.size()) return ItemStack.EMPTY;

        ItemStack stored = intakeAll.get(index);
        if (stored == null || stored.isEmpty()) return ItemStack.EMPTY;

        long count = CardDatabaseCards.databaseCount(stored);
        if (count <= 0L) return ItemStack.EMPTY;

        ItemStack extracted = CardDatabaseCards.copyForExtraction(stored);
        if (count <= 1L) {
            intakeAll.remove(index);
        } else {
            CardDatabaseCards.setDatabaseCount(stored, count - 1L);
            intakeAll.set(index, stored);
        }

        this.windowOffset = clampOffset(this.windowOffset);
        if (!projectingSearch) {
            applyBackingToWindow();
        }
        persist();

        return extracted;
    }


    /* ----- Search projection used by handler ----- */
    // CardDBSession.projectSearchResults(...)
    public void projectSearchResults(List<ItemStack> results) {
        if (uiFrozen) { needsReproject = true; return; }
        projectingSearch = true;
        syncingWindow = true;
        try {
            for (int i = 0; i < 54; i++) {
                var st = (i < results.size()) ? results.get(i) : ItemStack.EMPTY;
                window.setItem(i, st.isEmpty() ? ItemStack.EMPTY : st.copy());
            }
        } finally {
            syncingWindow = false;
        }
        suppressPersistOnce = true;  // consume next markDirty
        window.setChanged();          // notify GUI, no writeback/persist
    }


}
