// com/spider/mtgcard/db/CardDatabaseBlockEntity.java
package com.spider.mtgcard.db;

import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.registry.ModRegistry;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardDatabaseBlockEntity extends BlockEntity {
    /** Layout constants. */
    private static final int ROWS = 6, COLS = 9, PAGE = ROWS * COLS;

    /** Unbounded logical intake (all cards added by players live here). */
    private final List<ItemStack> intakeAll = new ArrayList<>(); // each is a CARD stack

    /** Guard to prevent recursive sync while we bulk-fill the window. */
    private boolean syncingWindow = false;

    /** If true, window shows a transient projection (e.g., search results). */
    private boolean projectingSearch = false;

    private static int binderCountFromCards(long totalCards) {
        if (totalCards <= 0) return 0;
        long bc = (totalCards + 399) / 400; // ceil
        return (int)Math.min(245L, bc);
    }
    private int clientBinderCount = 0;   // used on client for rendering
    private int serverBinderCount = 0;   // server authoritative for sync


    /** 54-slot visible “window” that Screen/Handler bind to. */
    private final net.minecraft.world.SimpleContainer window =
            new net.minecraft.world.SimpleContainer(PAGE) {
                @Override public boolean canPlaceItem(int slot, ItemStack stack) {
                    return stack.is(ModItems.CARD);
                }
                @Override public void setChanged() {
                    // If *we* are updating the window from code, don't bounce updates back
                    if (syncingWindow) return;

                    // Player changed the 54-slot window -> push to backing + persist
                    // (Only when not projecting; projections should not mutate intakeAll.)
                    if (!projectingSearch) {
                        CardDatabaseBlockEntity.this.applyWindowToBacking();
                        CardDatabaseBlockEntity.this.persistToState();
                        CardDatabaseBlockEntity.this.setChanged();
                    }
                    super.setChanged();
                }
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            };

    /** Start index in backing list that the window shows (always row-aligned). */
    private int windowOffset = 0;

    /** Virtual “stored” map (your infinite store—kept as-is). */
    private final Map<String, Long> store = new HashMap<>();
    private long totalCount = 0L;

    private boolean loadedFromState = false;

    public CardDatabaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_DB, pos, state);
    }

    /** Expose the 54-slot window to the ScreenHandler. */
    public net.minecraft.world.Container getIntakeInv() { return this.window; }
    public net.minecraft.world.Container getWindowInventory() { return this.window; }

    /* ------------------------ Row-aligned offset helpers ------------------------ */

    /** Max offset that keeps the window row-aligned with the last row visible. */
    public int getMaxWindowOffset() {
        int rowsTotal = (int) Math.ceil(intakeAll.size() / (double) COLS);
        int startRow  = Math.max(0, rowsTotal - ROWS);
        return startRow * COLS;
    }

    /** Clamp to [0, max] and snap to row boundary. */
    private int clampOffset(int off) {
        off = Math.max(0, Math.min(off, getMaxWindowOffset()));
        return (off / COLS) * COLS;
    }

    public int getWindowOffset() { return this.windowOffset; }

    /* ------------------------ Window <-> Backing ------------------------ */

    /** Append a CARD stack to the unbounded intake and refresh the window. (SERVER ONLY) */
    public void appendToIntake(ItemStack stack) {
        if (!(level instanceof ServerLevel)) return;
        if (stack.isEmpty() || !stack.is(ModItems.CARD)) return;

        // were we already showing the bottom-most page?
        boolean anchoredToBottom = (this.windowOffset == getMaxWindowOffset());

        // add the new card into the unbounded backing list
        this.intakeAll.add(stack.copy());

        // if we were at the bottom, keep us bottom-aligned (so partial row shows padded empties)
        if (anchoredToBottom) {
            this.windowOffset = getMaxWindowOffset();
        }

        final int MAX_INTAKE = 50_000;
        if (this.intakeAll.size() >= MAX_INTAKE) {
            if (this.level instanceof ServerLevel sw) {
                var p = sw.getNearestPlayer(this.worldPosition.getX()+0.5, this.worldPosition.getY()+0.5, this.worldPosition.getZ()+0.5, 8.0, false);
                if (p != null) p.displayClientMessage(net.minecraft.network.chat.Component.literal("Card Database intake is full."), false);
            }
            return;
        }

        // If we are currently projecting a search, don't mutate projection here –
        // let the UI re-run the search to refresh the projection. We still persist.
        if (!projectingSearch) {
            // reflect backing -> visible 6x9 window (fills remaining cells with EMPTY)
            applyBackingToWindow();
        }

        // persist & notify
        persistToState();
        updateBinderVisual();
        setChanged();
        this.window.setChanged(); // nudge the handler to push an update
    }

    /** Force window to reflect backing (keeping current offset). */
    public void refreshWindow() {
        // If search is projected, keep showing projection; else show backing.
        if (!projectingSearch) applyBackingToWindow();
    }

    /** Server-side: set the visible window offset (row scroll, etc.). */
    public void setWindowOffset(int off) {
        // If projecting search, offset is meaningless (projection always starts at 0).
        if (projectingSearch) return;
        int newOff = clampOffset(off);
        if (newOff == this.windowOffset) return;
        this.windowOffset = newOff;
        applyBackingToWindow();
    }

    public void shiftWindow(int delta) { setWindowOffset(this.windowOffset + delta); }

    /** Fill window slots from backing list slice [offset, offset+54). */
    private void applyBackingToWindow() {
        syncingWindow = true;
        try {
            for (int i = 0; i < PAGE; i++) {
                int idx = windowOffset + i;
                ItemStack st = (idx >= 0 && idx < intakeAll.size()) ? intakeAll.get(idx) : ItemStack.EMPTY;
                window.setItem(i, st.copy());
            }
        } finally {
            syncingWindow = false;
        }
    }

    /** Write the 54-slot window back into the backing list. */
    private void applyWindowToBacking() {
        // Ensure backing list is big enough
        int need = windowOffset + PAGE;
        while (intakeAll.size() < need) intakeAll.add(ItemStack.EMPTY);

        // Copy window slice into backing
        for (int i = 0; i < PAGE; i++) {
            int idx = windowOffset + i;
            ItemStack w = window.getItem(i).copy();
            intakeAll.set(idx, w);
        }

        // Trim trailing empties to keep list compact
        int trim = intakeAll.size() - 1;
        while (trim >= 0 && intakeAll.get(trim).isEmpty()) trim--;
        intakeAll.subList(trim + 1, intakeAll.size()).clear();

        // Keep offset row-aligned and within bounds after trims
        this.windowOffset = clampOffset(this.windowOffset);
    }

    /** Total count of items currently in the backing list. */
    public int getIntakeCount() { return intakeAll.size(); }

    /* ------------------------ Search projection helpers ------------------------ */

    /** Project a result set into the visible 54-slot window (does not mutate intakeAll). */
    public void projectSearchResults(List<ItemStack> results) {
        if (!(level instanceof ServerLevel)) return;
        projectingSearch = true;

        syncingWindow = true;
        try {
            for (int i = 0; i < PAGE; i++) {
                ItemStack st = (i >= 0 && i < results.size()) ? results.get(i) : ItemStack.EMPTY;
                window.setItem(i, st == null ? ItemStack.EMPTY : st.copy());
            }
        } finally {
            syncingWindow = false;
        }

        // force listeners to refresh container slots
        window.setChanged();
        // visual ping (optional)
        if (this.level != null) {
            this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Clear any search projection and restore the normal intake window slice. */
    public void clearSearchProjection() {
        projectingSearch = false;
        applyBackingToWindow();
        window.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isProjectingSearch() { return projectingSearch; }

    /* ------------------------ Persistence with CardDBState ------------------------ */

    /** Load from world state (store + intake list). */
    public void ensureLoaded() {
        if (!(level instanceof ServerLevel sw)) return;
        var st = CardDBState.get(sw);

        // restore virtual store
        var saved = st.getStore(worldPosition);
        this.store.clear();
        this.store.putAll(saved);
        recomputeTotal();

        // restore intake
        var intakeNbt = st.getIntake(worldPosition);
        this.intakeAll.clear();
        if (intakeNbt != null) CardDBState.applyIntakeToList(this.intakeAll, intakeNbt);

        // open anchored to the TOP, row-aligned
        this.windowOffset = 0;
        projectingSearch = false;
        applyBackingToWindow();
        updateBinderVisual();
    }

    public List<ItemStack> getIntakeAllReadonly() {
        return java.util.Collections.unmodifiableList(this.intakeAll);
    }

    public List<ItemStack> copyIntakeAll() {
        var out = new ArrayList<ItemStack>(intakeAll.size());
        for (var st : intakeAll) {
            out.add(st == null ? ItemStack.EMPTY : st.copy());
        }
        return out;
    }

    /** Persist current intake window/backing + store into world state. */
    private void persistToState() {
        if (!(level instanceof ServerLevel sw)) return;
        var st = CardDBState.get(sw);
        st.putStore(worldPosition, snapshot());
        st.putIntake(worldPosition, CardDBState.snapshotFromList(this.intakeAll));
    }

    /** Called once from ticker to lazy-load from CardDBState when the chunk is ready. */
    void serverTick() {
        if (loadedFromState) return;
        if (this.level == null || this.level.isClientSide()) return;

        loadedFromState = true;
        ensureLoaded();
    }

    /** Remove our data when block breaks. */
    public void removeFromState() {
        if (!(level instanceof ServerLevel sw)) return;
        CardDBState.get(sw).remove(worldPosition);
    }

    /* ------------------------ Optional “Store All” ------------------------ */

    public void absorbFromIntake() {
        if (this.level == null || this.level.isClientSide()) return;

        boolean any = false;
        for (int i = 0; i < intakeAll.size(); i++) {
            ItemStack st = intakeAll.get(i);
            if (st.isEmpty() || !st.is(ModItems.CARD)) continue;

            long moved = st.getCount();
            String key = keyOf(st);
            store.merge(key, moved, Long::sum);
            totalCount += moved;

            intakeAll.set(i, ItemStack.EMPTY);
            any = true;
        }

        // clear window too
        syncingWindow = true;
        for (int i = 0; i < PAGE; i++) window.setItem(i, ItemStack.EMPTY);
        syncingWindow = false;
        window.setChanged();

        intakeAll.clear();
        this.windowOffset = 0;
        projectingSearch = false;

        if (any) {
            level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.15f, 1.2f);
            level.levelEvent(2002, worldPosition, 0);
            updateBinderVisual();
            persistToState();
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ------------------------ Helpers ------------------------ */

    public long getTotalCount() { return totalCount; }

    private void recomputeTotal() {
        long t = 0L;
        for (long v : store.values()) t += v;
        totalCount = t;
    }

    Map<String, Long> snapshot() { return new HashMap<>(store); }

    private static String keyOf(ItemStack stack) {
        var comp = stack.getOrDefault(DataComponents.CUSTOM_DATA, null);
        var root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);

        String name = meta.getString("name").orElse("");
        String set  = meta.getString("set").orElse("");
        String col  = meta.getString("collector_number").orElse("");
        boolean foil = root.getCompound("mtg_flags").map(n -> n.getBoolean("mtg_foil").orElse(false)).orElse(false);

        return (set + "|" + col + "|" + (foil ? "F" : "N") + "|" + name).trim();
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        view.putInt("BinderCount", this.serverBinderCount);
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        // ReadView uses Optional-style getters in your mappings
        this.clientBinderCount = view.getIntOr("BinderCount", this.serverBinderCount);
    }

    // Sent when chunk data is sent to client
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // This will include writeData(...) automatically in your version
        return super.getUpdateTag(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private void updateBinderVisual() {
        long totalCards = this.totalCount + this.intakeAll.size();
        int newCount = binderCountFromCards(totalCards);

        if (newCount != this.serverBinderCount) {
            this.serverBinderCount = newCount;
            setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }
    public int getClientBinderCount() { return clientBinderCount; }

}
