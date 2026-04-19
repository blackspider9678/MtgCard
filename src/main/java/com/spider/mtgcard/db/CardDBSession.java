// com/spider/mtgcard/db/CardDBSession.java
package com.spider.mtgcard.db;

import com.spider.mtgcard.db.search.SearchEngine;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Per-player logical DB (infinite list + 54-slot window + scroll). */
public final class CardDBSession implements CardDBView {
    private static final int ROWS=6, COLS=9, PAGE=ROWS*COLS;

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
                    return stack.is(ModItems.CARD);
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
        intakeAll.removeIf(s -> s == null || s.isEmpty());
        this.windowOffset = clampOffset(this.windowOffset);
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
        s.load();
        s.applyBackingToWindow();
        return s;
    }

    private CardDBSession(ServerLevel world, UUID playerId) {
        this.world = world;
        this.playerId = playerId;
    }

    /* ---------- Persistence ---------- */

    private void load() {
        var state = PlayerCardDBState.get(world);
        var list = state.getIntake(playerId);
        this.intakeAll.clear();
        if (list != null) CardDBState.applyIntakeToList(this.intakeAll, list);
        this.windowOffset = 0;
        this.projectingSearch = false;
    }

    public void ensureLoaded(ServerPlayer sp) {
        if (!(world instanceof ServerLevel sw)) return;

        this.owner = sp.getUUID();
        var st = PlayerCardDBState.get(sw);
        var saved = st.getIntake(owner);

        this.intakeAll.clear();
        CardDBState.applyIntakeToList(this.intakeAll, saved);
        this.windowOffset = 0;
        this.projectingSearch = false;
        applyBackingToWindow();
        this.window.setChanged();
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
        if (stack.isEmpty() || !stack.is(ModItems.CARD)) return;

        // Ensure unique identity for reliable remove-by-UID later
        ensureUid(stack);

        boolean anchoredToBottom = (this.windowOffset == getMaxWindowOffset());
        this.intakeAll.add(stack.copy());
        if (anchoredToBottom) this.windowOffset = getMaxWindowOffset();

        if (!projectingSearch) applyBackingToWindow();
        persist();
        window.setChanged();
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

        ItemStack removed = intakeAll.remove(idx);

        // 🔧 Do a full compact + redraw of the same page so no empties linger
        compactIntakeAndReprojectSamePage();

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
