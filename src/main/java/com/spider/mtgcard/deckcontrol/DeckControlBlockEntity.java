package com.spider.mtgcard.deckcontrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.db.search.CardMeta;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.item.ModItemTags;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class DeckControlBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public static final int LIBRARY_SLOTS = DeckboxBlockEntity.MAIN_SLOTS; // 99

    // Link + order
    private @Nullable BlockPos linkedDeckboxPos = null;
    private boolean lockedLink = false; // locks to first discovered deckbox
    private final List<DeckRef> libraryOrder = new ArrayList<>();
    private final Map<String, List<DeckRef>> namedPoolOrders = new LinkedHashMap<>();

    // Redstone draw
    private boolean wasPowered = false;
    private int cooldownTicks = 0;
    private static final int DRAW_COOLDOWN_TICKS = 5;

    // Debounced rebuild when deckbox changes
    private boolean pendingDeckboxRebuild = false;
    private @Nullable BlockPos pendingDeckboxPos = null;

    // Peek toggle (UI)
    private boolean peekActive = false;
    public boolean isPeekActive() { return peekActive; }
    public void setPeekActive(boolean v) { peekActive = v; setChanged(); syncSelf(); }

    private String selectedGame = TcgGameRegistry.MTG;
    public String getSelectedGame() { return selectedGame; }
    public void setSelectedGame(String game) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        if (normalized.isBlank()) normalized = TcgGameRegistry.MTG;
        if (selectedGame.equals(normalized)) return;
        selectedGame = normalized;
        setChanged();
        syncSelf();
    }

    // ----- Resolution / Cascade transaction -----
    private int resolutionId = 0;

    private int cascadeSourceMv = 0;

    public DeckControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECK_CONTROL, pos, state);
    }

    /* ---------------- Screen opening (Extended) ---------------- */

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mtgcard.deck_control");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.worldPosition;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DeckControlScreenHandler(
                syncId,
                inv,
                this.worldPosition,
                ContainerLevelAccess.create(Objects.requireNonNull(level), this.worldPosition)
        );
    }

    @org.jetbrains.annotations.Nullable
    private com.spider.mtgcard.graveyard.GraveyardBlockEntity findAdjacentGraveyard(net.minecraft.world.level.Level world, BlockPos pos) {
        for (var dir : net.minecraft.core.Direction.values()) {
            BlockPos p = pos.relative(dir);
            var be = world.getBlockEntity(p);
            if (be instanceof com.spider.mtgcard.graveyard.GraveyardBlockEntity gbe) return gbe;
        }
        return null;
    }

    private java.util.List<ItemStack> tryPutIntoGraveyard(com.spider.mtgcard.graveyard.GraveyardBlockEntity gbe, java.util.List<ItemStack> stacks) {
        java.util.List<ItemStack> leftover = new java.util.ArrayList<>();
        final int GRAVE_START = 0;
        final int GRAVE_COUNT = 100;

        for (ItemStack in : stacks) {
            if (in == null || in.isEmpty()) continue;
            ItemStack stack = in.copy();

            // try merge first, then empty
            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = GRAVE_START; i < GRAVE_START + GRAVE_COUNT && !stack.isEmpty(); i++) {
                    ItemStack cur = gbe.getStack(i);
                    if (pass == 0) {
                        // merge pass
                        if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack) && cur.getCount() < cur.getMaxStackSize()) {
                            int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                            cur.grow(can);
                            stack.shrink(can);
                            gbe.setStack(i, cur);
                        }
                    } else {
                        // empty slot pass
                        if (cur.isEmpty()) {
                            gbe.setStack(i, stack);
                            stack = ItemStack.EMPTY;
                        }
                    }
                }
            }

            if (!stack.isEmpty()) leftover.add(stack);
        }

        if (!leftover.isEmpty()) gbe.markDirty();
        return leftover;
    }

    private void ejectOutBack(net.minecraft.server.level.ServerLevel world, BlockPos pos, net.minecraft.core.Direction facing, java.util.List<ItemStack> stacks) {
        var outDir = facing.getOpposite();
        double x = pos.getX() + 0.5 + outDir.getStepX() * 0.6;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5 + outDir.getStepZ() * 0.6;

        for (ItemStack st : stacks) {
            if (st == null || st.isEmpty()) continue;
            var ent = new net.minecraft.world.entity.item.ItemEntity(world, x, y, z, st.copy());
            ent.setDeltaMovement(outDir.getStepX() * 0.25, 0.15, outDir.getStepZ() * 0.25);
            world.addFreshEntity(ent);
        }
    }

    private record DeckRef(int slot, String key, int face) {
        static final Codec<DeckRef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("Slot").forGetter(DeckRef::slot),
                Codec.STRING.fieldOf("Key").forGetter(DeckRef::key),
                Codec.INT.optionalFieldOf("Face", 0).forGetter(DeckRef::face)
        ).apply(inst, DeckRef::new));
    }

    /* ---------------- Tick ---------------- */

    public void tick() {
        if (level == null) return;

        if (level.isClientSide()) return;
        if (cooldownTicks > 0) cooldownTicks--;

        // Rebuild if deckbox changed (debounced)
        if (pendingDeckboxRebuild && pendingDeckboxPos != null) {
            var be = level.getBlockEntity(pendingDeckboxPos);
            if (be instanceof DeckboxBlockEntity db) {
                if (libraryOrder.isEmpty()) rebuildLibraryOrder(db);
                else {
                    reconcileLibraryOrder(db);
                    if (!isLibraryOrderConsistent(db)) rebuildLibraryOrder(db);
                }
            }
            pendingDeckboxRebuild = false;
            pendingDeckboxPos = null;
            setChanged();
            syncSelf();
        }

        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered && !wasPowered && cooldownTicks <= 0) {
            drawTopAndEject();
            cooldownTicks = DRAW_COOLDOWN_TICKS;
        }
        wasPowered = powered;
    }

    /* ---------------- Status helpers for ScreenHandler props ---------------- */

    public boolean hasLinkedDeckbox() {
        return findOrLinkDeckbox() != null;
    }

    private record SavedPool(String name, List<DeckRef> order) {
        static final Codec<SavedPool> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("Name").forGetter(SavedPool::name),
                DeckRef.CODEC.listOf().fieldOf("Order").forGetter(SavedPool::order)
        ).apply(inst, SavedPool::new));
    }

    public int getLibraryCount() {
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return 0;
        int used = 0;
        for (int i = 0; i < LIBRARY_SLOTS; i++) if (!db.getStack(i).isEmpty()) used++;
        return used;
    }

    /* ---------------- Public Actions ---------------- */

    private boolean isLibraryOrderConsistent(DeckboxBlockEntity db) {
        if (db == null) return false;

        // quick count check
        int nonEmpty = 0;
        for (int i = 0; i < LIBRARY_SLOTS; i++) {
            if (!db.getStack(i).isEmpty()) nonEmpty++;
        }
        if (libraryOrder.size() != nonEmpty) return false;

        // deep check
        for (DeckRef ref : libraryOrder) {
            if (ref == null) return false;

            int slot = ref.slot();
            if (slot < 0 || slot >= LIBRARY_SLOTS) return false;

            ItemStack st = db.getStack(slot);
            if (st.isEmpty()) return false;

            String keyNow = computeKey(st);
            if (!keyNow.equals(ref.key())) return false;
        }
        return true;
    }

    private void reconcileLibraryOrder(DeckboxBlockEntity db) {
        if (db == null) return;

        ArrayList<DeckRef> kept = new ArrayList<>(libraryOrder.size());
        boolean[] usedSlot = new boolean[LIBRARY_SLOTS];

        for (DeckRef ref : libraryOrder) {
            if (ref == null) continue;

            int s = ref.slot();
            if (s < 0 || s >= LIBRARY_SLOTS) continue;

            ItemStack st = db.getStack(s);
            if (st.isEmpty()) continue;

            if (!computeKey(st).equals(ref.key())) continue;

            kept.add(ref);
            usedSlot[s] = true;
        }

        long seed = (level != null ? level.getGameTime() : 0L) ^ worldPosition.asLong();
        Random r = new Random(seed);

        for (int s = 0; s < LIBRARY_SLOTS; s++) {
            if (usedSlot[s]) continue;

            ItemStack st = db.getStack(s);
            if (st.isEmpty()) continue;

            DeckRef ref = new DeckRef(s, computeKey(st), 0);
            int insertAt = kept.isEmpty() ? 0 : r.nextInt(kept.size() + 1);
            kept.add(insertAt, ref);
        }

        libraryOrder.clear();
        libraryOrder.addAll(kept);

        setChanged();
    }


    public void shuffle() {
        if (level == null || level.isClientSide()) return;

        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        // Ensure we shuffle a *valid* order, otherwise the next draw/peek will rebuild
        if (libraryOrder.isEmpty() || !isLibraryOrderConsistent(db)) {
            rebuildLibraryOrder(db);
        }

        if (libraryOrder.size() > 1) {
            // Optional: stable-ish server-side randomness
            long seed = (level.getGameTime() * 31L) ^ worldPosition.asLong();
            Collections.shuffle(libraryOrder, new Random(seed));
            // If you prefer fully random each click, just use Collections.shuffle(libraryOrder);
        }

        setChanged();
        syncSelf();
    }


    public void drawTopAndEject() {
        if (level == null || level.isClientSide()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        if (libraryOrder.isEmpty()) {
            rebuildLibraryOrder(deckbox);
            if (libraryOrder.isEmpty()) return;
        }

        DeckRef ref = libraryOrder.remove(0);

        ItemStack stack = deckbox.getStack(ref.slot());
        if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
            reconcileLibraryOrder(deckbox);

            setChanged();
            syncSelf();
            return;
        }

        ItemStack removed = deckbox.removeStack(ref.slot());
        if (removed.isEmpty()) {
            reconcileLibraryOrder(deckbox);
            if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);
            setChanged();
            syncSelf();
            return;
        }

        deckbox.sync();

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        dropStackFromModelTop(level, worldPosition, facing, removed);

        setChanged();
        syncSelf();
    }

    /** Add-on API: shuffle one filtered deck pool without affecting any other pool. */
    public void shufflePool(String poolId, java.util.function.Predicate<ItemStack> membership) {
        if (level == null || level.isClientSide() || membership == null) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;
        List<DeckRef> order = reconcilePool(poolId, db, membership);
        if (order.size() > 1) {
            long seed = (level.getGameTime() * 31L) ^ worldPosition.asLong() ^ normalizePoolId(poolId).hashCode();
            Collections.shuffle(order, new Random(seed));
        }
        setChanged();
        syncSelf();
    }

    /** Add-on API: draw and eject the top card of one filtered deck pool. */
    public boolean drawPoolTopAndEject(String poolId, java.util.function.Predicate<ItemStack> membership) {
        if (level == null || level.isClientSide() || membership == null) return false;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return false;
        List<DeckRef> order = reconcilePool(poolId, db, membership);
        if (order.isEmpty()) return false;
        DeckRef ref = order.remove(0);
        ItemStack stack = db.getStack(ref.slot());
        if (stack.isEmpty() || !membership.test(stack) || !computeKey(stack).equals(ref.key())) {
            reconcilePool(poolId, db, membership);
            return false;
        }
        ItemStack removed = db.removeStack(ref.slot());
        if (removed.isEmpty()) return false;
        for (List<DeckRef> other : namedPoolOrders.values()) other.removeIf(r -> r.slot() == ref.slot());
        db.sync();
        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        dropStackFromModelTop(level, worldPosition, facing, removed);
        setChanged();
        syncSelf();
        return true;
    }

    /** Add-on API: move one player inventory card to the bottom of a filtered pool. */
    public boolean recyclePlayerCardToBottom(ServerPlayer player, int inventorySlot, String poolId,
                                             java.util.function.Predicate<ItemStack> membership) {
        if (level == null || level.isClientSide() || player == null || membership == null) return false;
        if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) return false;
        ItemStack selected = player.getInventory().getItem(inventorySlot);
        if (selected.isEmpty() || !membership.test(selected)) return false;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return false;
        int empty = -1;
        for (int i = 0; i < LIBRARY_SLOTS; i++) if (db.getStack(i).isEmpty()) { empty = i; break; }
        if (empty < 0) return false;
        ItemStack moved = selected.copy();
        moved.setCount(1);
        selected.shrink(1);
        db.setStack(empty, moved);
        List<DeckRef> order = reconcilePool(poolId, db, membership);
        final int destinationSlot = empty;
        order.removeIf(r -> r.slot() == destinationSlot);
        order.add(new DeckRef(destinationSlot, computeKey(moved), 0));
        db.sync();
        player.getInventory().setChanged();
        setChanged();
        syncSelf();
        return true;
    }

    private List<DeckRef> reconcilePool(String poolId, DeckboxBlockEntity db,
                                        java.util.function.Predicate<ItemStack> membership) {
        String id = normalizePoolId(poolId);
        List<DeckRef> current = namedPoolOrders.computeIfAbsent(id, ignored -> new ArrayList<>());
        ArrayList<DeckRef> kept = new ArrayList<>();
        boolean[] used = new boolean[LIBRARY_SLOTS];
        for (DeckRef ref : current) {
            if (ref == null || ref.slot() < 0 || ref.slot() >= LIBRARY_SLOTS || used[ref.slot()]) continue;
            ItemStack stack = db.getStack(ref.slot());
            if (!stack.isEmpty() && membership.test(stack) && computeKey(stack).equals(ref.key())) {
                kept.add(ref);
                used[ref.slot()] = true;
            }
        }
        for (int slot = 0; slot < LIBRARY_SLOTS; slot++) {
            if (used[slot]) continue;
            ItemStack stack = db.getStack(slot);
            if (!stack.isEmpty() && membership.test(stack)) kept.add(new DeckRef(slot, computeKey(stack), 0));
        }
        current.clear();
        current.addAll(kept);
        return current;
    }

    private static String normalizePoolId(String poolId) {
        String id = poolId == null ? "" : poolId.trim().toLowerCase(Locale.ROOT);
        id = id.replaceAll("[^a-z0-9_.:-]", "_");
        return id.isBlank() ? "default" : id;
    }

    public List<ItemStack> takeTopCards(int n) {
        if (level == null || level.isClientSide()) return List.of();

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return List.of();

        ensureLinkedAndBuilt();
        n = Math.min(Math.max(0, n), libraryOrder.size());
        if (n <= 0) return List.of();

        List<ItemStack> removedStacks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (libraryOrder.isEmpty()) break;

            DeckRef ref = libraryOrder.remove(0);
            ItemStack stack = deckbox.getStack(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
                reconcileLibraryOrder(deckbox);
                if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);
                break;
            }

            ItemStack removed = deckbox.removeStack(ref.slot());
            if (!removed.isEmpty()) removedStacks.add(removed);
        }

        deckbox.sync();
        setChanged();
        syncSelf();
        return List.copyOf(removedStacks);
    }

    public void putCardsOnBottom(List<ItemStack> stacks) {
        if (level == null || level.isClientSide()) return;
        if (stacks == null || stacks.isEmpty()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            copies.add(stack.copy());
        }
        if (copies.isEmpty()) return;

        putStacksOnBottom(deckbox, copies);
        deckbox.sync();
        setChanged();
        syncSelf();
    }

    public @Nullable BlockPos getLinkedDeckboxPos() {
        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return null;
        return linkedDeckboxPos == null ? null : linkedDeckboxPos.immutable();
    }

    public List<ItemStack> peekTopCopies(int n) {
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return List.of();

        ensureLinkedAndBuilt();

        n = Math.min(Math.max(0, n), libraryOrder.size());
        if (n <= 0) return List.of();

        // Try once, if we detect a stale ref rebuild and try again
        for (int attempt = 0; attempt < 2; attempt++) {
            var out = new ArrayList<ItemStack>(n);

            for (int i = 0; i < n; i++) {
                DeckRef ref = libraryOrder.get(i);
                ItemStack st = db.getStack(ref.slot());

                // If anything is stale, rebuild and retry
                if (st.isEmpty() || !computeKey(st).equals(ref.key())) {
                    if (!libraryOrder.isEmpty()) reconcileLibraryOrder(db);
                    else rebuildLibraryOrder(db);

                    if (!isLibraryOrderConsistent(db)) rebuildLibraryOrder(db);

                    out.clear();
                    break;
                }

                out.add(st.copy());
            }

            if (out.size() == n) return out;
        }

        // If it keeps being inconsistent, just return what we can (should be rare)
        var out = new ArrayList<ItemStack>(n);
        for (int i = 0; i < n && i < libraryOrder.size(); i++) {
            DeckRef ref = libraryOrder.get(i);
            ItemStack st = db.getStack(ref.slot());
            if (!st.isEmpty() && computeKey(st).equals(ref.key())) out.add(st.copy());
        }
        return out;
    }

    public void millTop(int n) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        n = Math.min(Math.max(1, n), libraryOrder.size());
        if (n <= 0) return;

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);

        // Collect removed stacks first
        List<ItemStack> removedStacks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (libraryOrder.isEmpty()) break;

            DeckRef ref = libraryOrder.remove(0);
            ItemStack stack = db.getStack(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
                reconcileLibraryOrder(db);
                if (!isLibraryOrderConsistent(db)) rebuildLibraryOrder(db);
                break;
            }

            ItemStack removed = db.removeStack(ref.slot());
            if (!removed.isEmpty()) removedStacks.add(removed);
        }

        // Try adjacent graveyard first, leftover ejects out the back
        if (!removedStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                List<ItemStack> leftover = tryPutIntoGraveyard(gbe, removedStacks);
                if (!leftover.isEmpty()) ejectOutBack((ServerLevel) level, worldPosition, facing, leftover);

            } else {
                ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, removedStacks);
            }
        }

        db.sync();
        setChanged();
        syncSelf();
    }

    /** Insert 1 card item from player's inventory into library at indexFromTop (0..size). */
    public boolean placeFromPlayer(Player player, int playerInvSlot, int indexFromTop) {
        if (level == null || level.isClientSide()) return false;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return false;

        ensureLinkedAndBuilt();

        if (playerInvSlot < 0 || playerInvSlot >= player.getInventory().getContainerSize()) return false;
        ItemStack st = player.getInventory().getItem(playerInvSlot);
        if (st.isEmpty()) return false;

        if (!st.is(ModItemTags.TCG_CARD)) return false;

        int empty = -1;
        for (int i = 0; i < LIBRARY_SLOTS; i++) {
            if (db.getStack(i).isEmpty()) { empty = i; break; }
        }
        if (empty == -1) return false;

        ItemStack moved = st.split(1);
        if (moved.isEmpty()) return false;

        db.setStack(empty, moved);
        db.sync();

        indexFromTop = Math.max(0, Math.min(indexFromTop, libraryOrder.size()));
        libraryOrder.add(indexFromTop, new DeckRef(empty, computeKey(moved), 0));

        setChanged();
        syncSelf();
        return true;
    }

    public boolean placeFromPlayerBottom(Player player, int playerInvSlot) {
        return placeFromPlayer(player, playerInvSlot, Integer.MAX_VALUE);
    }

    /** Server-side scry resolution: keepOrder are indices into the looked list in desired order. */
    public void resolveScry(List<Integer> keepOrder, boolean bottomRandom, int lookedN) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var kept = new ArrayList<DeckRef>();
        if (keepOrder != null) {
            for (int idx : keepOrder) {
                if (idx >= 0 && idx < looked.size()) kept.add(looked.get(idx));
            }
        }

        var bottom = new ArrayList<DeckRef>();
        for (int i = 0; i < looked.size(); i++) {
            if (!kept.contains(looked.get(i))) bottom.add(looked.get(i));
        }
        if (bottomRandom) Collections.shuffle(bottom);

        libraryOrder.addAll(0, kept);
        libraryOrder.addAll(bottom);

        setChanged();
        syncSelf();
    }

    /** Surveil: indices in toMill are removed (temp drop), rest stay on top. */
    public void resolveSurveil(Player player, List<Integer> toMill, boolean bottomRandom, int lookedN) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var millSet = new HashSet<Integer>();
        if (toMill != null) millSet.addAll(toMill);

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);

        // Remove milled stacks from deckbox and collect them
        List<ItemStack> milledStacks = new ArrayList<>();
        for (int i = 0; i < looked.size(); i++) {
            if (!millSet.contains(i)) continue;

            DeckRef ref = looked.get(i);
            ItemStack stack = db.getStack(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

            ItemStack removed = db.removeStack(ref.slot());
            if (!removed.isEmpty()) milledStacks.add(removed);
        }

        // Put milled into graveyard (or eject out back)
        if (!milledStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty()) ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, leftover);
            } else {
                ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, milledStacks);
            }
        }

        // Keep = everything not milled stays on top (in original order)
        var keep = new ArrayList<DeckRef>();
        for (int i = 0; i < looked.size(); i++) if (!millSet.contains(i)) keep.add(looked.get(i));

        if (bottomRandom) Collections.shuffle(keep);
        libraryOrder.addAll(0, keep);

        db.sync();
        setChanged();
        syncSelf();
    }

    /* ---------------- Cascade ---------------- */

    private static int mvOf(ItemStack st) { return CardMeta.read(st).mv(); }

    private static boolean isLand(ItemStack st) {
        String type = CardMeta.read(st).typeLine();
        return type != null && type.contains("Land");
    }

    private void putStacksOnBottom(DeckboxBlockEntity deckbox, List<ItemStack> stacks) {
        if (level == null) return;

        // keep current order intact, then append these at the bottom in the exact order we place them
        if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
        else rebuildLibraryOrder(deckbox);

        List<DeckRef> appended = new ArrayList<>();
        int idx = 0;

        for (int slot = LIBRARY_SLOTS - 1; slot >= 0 && idx < stacks.size(); slot--) {
            if (deckbox.getStack(slot).isEmpty()) {
                ItemStack placed = stacks.get(idx++);
                deckbox.setStack(slot, placed);
                appended.add(new DeckRef(slot, computeKey(placed), 0));
            }
        }

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        while (idx < stacks.size()) {
            ejectStack(level, worldPosition, facing, stacks.get(idx++));
        }

        // ✅ appended cards are bottom of library
        libraryOrder.addAll(appended);

        setChanged();
    }

    /* ---------------- Linking / Building ---------------- */

    private void ensureLinkedAndBuilt() {
        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;
        if (libraryOrder.isEmpty()) rebuildLibraryOrder(deckbox);
    }

    private @Nullable DeckboxBlockEntity findOrLinkDeckbox() {
        if (level == null) return null;

        // stored link first
        if (linkedDeckboxPos != null) {
            BlockEntity be = level.getBlockEntity(linkedDeckboxPos);
            if (be instanceof DeckboxBlockEntity db) return db;

            // linked deckbox gone
            clearLinkAndUnlock();
        }

        if (lockedLink) return null;

        // scan neighbors once; lock on first
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof DeckboxBlockEntity db) {
                linkedDeckboxPos = p;
                lockedLink = true;
                rebuildLibraryOrder(db);
                setChanged();
                syncSelf();
                return db;
            }
        }

        return null;
    }

    private void rebuildLibraryOrder(DeckboxBlockEntity deckbox) {
        libraryOrder.clear();

        for (int i = 0; i < LIBRARY_SLOTS; i++) {
            ItemStack stack = deckbox.getStack(i);
            if (stack.isEmpty()) continue;
            libraryOrder.add(new DeckRef(i, computeKey(stack), 0));
        }

        setChanged();
        syncSelf();
    }

    public void onNeighborDeckboxChanged(BlockPos deckboxPos) {
        if (level == null || level.isClientSide()) return;

        if (linkedDeckboxPos != null
                && !(level.getBlockEntity(linkedDeckboxPos) instanceof DeckboxBlockEntity)) {
            clearLinkAndUnlock();
        }

        if (linkedDeckboxPos != null) {
            if (!linkedDeckboxPos.equals(deckboxPos)) return;
        } else {
            if (lockedLink) {
                lockedLink = false;
                libraryOrder.clear();
            }
            if (worldPosition.distManhattan(deckboxPos) != 1) return;

            linkedDeckboxPos = deckboxPos;
            lockedLink = true;
        }

        pendingDeckboxRebuild = true;
        pendingDeckboxPos = deckboxPos;
        setChanged();
    }

    public void clearLinkAndUnlock() {
        linkedDeckboxPos = null;
        lockedLink = false;
        libraryOrder.clear();
        pendingDeckboxRebuild = false;
        pendingDeckboxPos = null;
        setChanged();
        syncSelf();
    }

    /* ---------------- Ejection + keying ---------------- */

    private static void ejectStack(Level world, BlockPos pos, Direction facing, ItemStack stack) {
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.6;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;

        ItemEntity itemEntity = new ItemEntity(world, x, y, z, stack);
        itemEntity.setDeltaMovement(
                facing.getStepX() * 0.25,
                0.05,
                facing.getStepZ() * 0.25
        );
        itemEntity.setDefaultPickUpDelay();
        world.addFreshEntity(itemEntity);
    }

    private static String computeKey(ItemStack stack) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(stack.getItem().toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte) stack.getCount());

            // ✅ 1.21+: hash components (covers custom data, card meta, etc.)
            md.update(stack.getComponents().toString().getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return stack.getItem().toString() + "|" + stack.getCount() + "|" + stack.getComponents();
        }
    }

    /* ---------------- Save / Load ---------------- */

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        long link = view.getLongOr("LinkedDeckbox", 0L);
        linkedDeckboxPos = (link != 0L) ? BlockPos.of(link) : null;

        lockedLink = view.getBooleanOr("LockedLink", false);
        peekActive = view.getBooleanOr("PeekActive", false);
        selectedGame = TcgGameRegistry.normalizeGameId(view.getStringOr("SelectedGame", TcgGameRegistry.MTG));
        if (selectedGame.isBlank()) selectedGame = TcgGameRegistry.MTG;
        wasPowered = view.getBooleanOr("WasPowered", false);
        cooldownTicks = view.getIntOr("Cooldown", 0);

        libraryOrder.clear();
        var loaded = view.read("LibraryOrder", DeckRef.CODEC.listOf()).orElse(List.of());
        libraryOrder.addAll(loaded);
        namedPoolOrders.clear();
        for (SavedPool pool : view.read("NamedPoolOrders", SavedPool.CODEC.listOf()).orElse(List.of())) {
            if (pool != null) namedPoolOrders.put(normalizePoolId(pool.name()), new ArrayList<>(pool.order()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        view.putLong("LinkedDeckbox", linkedDeckboxPos != null ? linkedDeckboxPos.asLong() : 0L);
        view.putBoolean("LockedLink", lockedLink);

        view.putBoolean("PeekActive", peekActive);
        view.putString("SelectedGame", selectedGame);
        view.putBoolean("WasPowered", wasPowered);
        view.putInt("Cooldown", cooldownTicks);

        view.store("LibraryOrder", DeckRef.CODEC.listOf(), libraryOrder);
        List<SavedPool> pools = namedPoolOrders.entrySet().stream()
                .map(e -> new SavedPool(e.getKey(), List.copyOf(e.getValue()))).toList();
        view.store("NamedPoolOrders", SavedPool.CODEC.listOf(), pools);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveWithoutMetadata(lookup);
    }

    private void syncSelf() {
        if (level == null || level.isClientSide()) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // pending cascade by player UUID
    private final Map<UUID, PendingCascade> pendingCascade = new HashMap<>();

    private static final class PendingCascade {
        final int sourceMv;
        final List<ItemStack> revealedActual; // the REAL removed stacks (server truth)
        final int hitIndex;                   // index in revealedActual, -1 if none
        final int resolutionId;

        PendingCascade(int sourceMv, List<ItemStack> revealedActual, int hitIndex, int resolutionId) {
            this.sourceMv = sourceMv;
            this.revealedActual = revealedActual;
            this.hitIndex = hitIndex;
            this.resolutionId = resolutionId;
        }
    }

    // DeckControlBlockEntity.java

    public void cancelPendingCascade(ServerPlayer player) {
        // treat cancel as "exile" (i.e., do NOT cast, just bottom everything)
        resolveCascade(player, false);
    }

    public boolean hasPendingCascade(ServerPlayer player) {
        return pendingCascade.containsKey(player.getUUID());
    }

    public void startCascade(ServerPlayer player, int sourceManaValue) {
        if (level == null || level.isClientSide()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        if (libraryOrder.isEmpty()) rebuildLibraryOrder(deckbox);
        if (libraryOrder.isEmpty()) return;

        // If they already have a pending cascade, ignore (prevents spam)
        if (pendingCascade.containsKey(player.getUUID())) return;

        int mv = Math.max(0, sourceManaValue);
        int localResolutionId = ++resolutionId;

        var revealed = new ArrayList<ItemStack>();
        int hitIndex = -1;

        while (!libraryOrder.isEmpty()) {
            DeckRef ref = libraryOrder.remove(0);

            ItemStack stack = deckbox.getStack(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
                if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
                if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);

                bottomRandom(deckbox, revealed, localResolutionId);
                deckbox.sync();
                setChanged();
                syncSelf();
                return;
            }

            ItemStack removed = deckbox.removeStack(ref.slot());
            if (removed.isEmpty()) {
                if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
                if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);

                bottomRandom(deckbox, revealed, localResolutionId);
                deckbox.sync();
                setChanged();
                syncSelf();
                return;
            }

            revealed.add(removed);

            if (hitIndex < 0 && !isLand(removed) && mvOf(removed) < mv) {
                hitIndex = revealed.size() - 1;
                break; // stop immediately when we hit
            }
        }

        deckbox.sync();
        reconcileLibraryOrder(deckbox); // ✅ preserve remaining order

        pendingCascade.put(player.getUUID(), new PendingCascade(mv, revealed, hitIndex, localResolutionId));
        indexAdd(player.getUUID(), this.worldPosition);

        // Send UI overlay using COPIES (never empties)
        var copies = revealed.stream().filter(s -> s != null && !s.isEmpty()).map(ItemStack::copy).toList();

        player.connection.send(ServerPlayNetworking.createS2CPacket(
                new DeckControlPackets.CascadeS2C(this.worldPosition, mv, hitIndex, copies)
        ));

        setChanged();
        syncSelf();
    }
    public void resolveCascade(ServerPlayer player, boolean cast) {
        if (level == null || level.isClientSide()) return;

        PendingCascade pc = pendingCascade.remove(player.getUUID());
        indexRemove(player.getUUID(), this.worldPosition);
        if (pc == null) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) {
            // no deckbox => just drop everything in front (including hit)
            dropStacksInFront(pc.revealedActual);
            setChanged();
            syncSelf();
            return;
        }

        var pile = new ArrayList<>(pc.revealedActual);

        if (cast && pc.hitIndex >= 0 && pc.hitIndex < pile.size()) {
            ItemStack hit = pile.remove(pc.hitIndex);
            Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
            ejectStack(level, worldPosition, facing, hit);
        }
        // exile = do nothing special; hit remains in pile

        // bottom all revealed in random order
        bottomRandom(deckbox, pile, pc.resolutionId);

        deckbox.sync();
        setChanged();
        syncSelf();
    }
    private void bottomRandom(DeckboxBlockEntity deckbox, List<ItemStack> stacks, int resId) {
        if (stacks == null || stacks.isEmpty()) return;
        long seed = (level.getGameTime() * 31L) ^ worldPosition.asLong() ^ ((long) resId * 1315423911L);
        Collections.shuffle(stacks, new Random(seed));
        putStacksOnBottom(deckbox, stacks);
    }

    private void dropStacksInFront(List<ItemStack> stacks) {
        if (level == null || stacks == null) return;
        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        for (ItemStack st : stacks) {
            if (st != null && !st.isEmpty()) ejectStack(level, worldPosition, facing, st);
        }
    }

    // DeckControlBlockEntity.java
    private static final Map<UUID, java.util.Set<BlockPos>> PENDING_BY_PLAYER = new HashMap<>();

    private static void indexAdd(UUID playerId, BlockPos pos) {
        PENDING_BY_PLAYER.computeIfAbsent(playerId, k -> new java.util.HashSet<>()).add(pos);
    }

    private static void indexRemove(UUID playerId, BlockPos pos) {
        var set = PENDING_BY_PLAYER.get(playerId);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) PENDING_BY_PLAYER.remove(playerId);
    }

    // Used by disconnect hook
    public static java.util.Set<BlockPos> getPendingPositions(UUID playerId) {
        var set = PENDING_BY_PLAYER.get(playerId);
        return (set == null) ? java.util.Set.of() : java.util.Set.copyOf(set);
    }

    /** Ordered Scry: topOrder + bottomOrder are indices into the looked list (0..lookedN-1). */
    public void resolveOrderedScry(int lookedN, int topCount, int[] order, boolean bottomRandom) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        topCount = Math.max(0, Math.min(topCount, lookedN));

        // Pull looked refs off the top
        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        // Build top/bottom using the permutation
        var top = new ArrayList<DeckRef>(topCount);
        var bottom = new ArrayList<DeckRef>(lookedN - topCount);

        for (int i = 0; i < lookedN; i++) {
            int idx = (order != null && i < order.length) ? order[i] : i;
            if (idx < 0 || idx >= looked.size()) continue;

            if (i < topCount) top.add(looked.get(idx));
            else bottom.add(looked.get(idx));
        }

        if (bottomRandom) Collections.shuffle(bottom);

        // Put top back on top
        libraryOrder.addAll(0, top);

        // Put bottom on the *bottom of the library* (append)
        libraryOrder.addAll(bottom);

        setChanged();
        syncSelf();
    }

    /**
     * Ordered Surveil:
     * - keepTopOrder: indices into looked list that stay on top (in that exact order)
     * - millOrder: indices into looked list to mill (eject) (order doesn't *need* to matter, but we keep it)
     */
    public void resolveSurveilOrdered(ServerPlayer player,
                                      List<Integer> keepTopOrder,
                                      List<Integer> millOrder,
                                      boolean bottomRandom,
                                      int lookedN) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        // Pull the looked cards off the top
        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var millSet = new HashSet<Integer>();
        if (millOrder != null) millSet.addAll(millOrder);

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);

        // Remove milled stacks from deckbox in the exact UI "graveyard row" order
        List<ItemStack> milledStacks = new ArrayList<>();
        if (millOrder != null) {
            for (int idx : millOrder) {
                if (idx < 0 || idx >= looked.size()) continue;
                DeckRef ref = looked.get(idx);

                ItemStack stack = db.getStack(ref.slot());
                if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

                ItemStack removed = db.removeStack(ref.slot());
                if (!removed.isEmpty()) milledStacks.add(removed);
            }
        } else {
            for (int i = 0; i < looked.size(); i++) {
                if (!millSet.contains(i)) continue;
                DeckRef ref = looked.get(i);

                ItemStack stack = db.getStack(ref.slot());
                if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

                ItemStack removed = db.removeStack(ref.slot());
                if (!removed.isEmpty()) milledStacks.add(removed);
            }
        }

        // Put milled into graveyard (or eject out back)
        if (!milledStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty()) ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, leftover);
            } else {
                ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, milledStacks);
            }
        }

        // Keep: rebuild top in exact order
        var keep = new ArrayList<DeckRef>(lookedN);
        if (keepTopOrder != null) {
            for (int idx : keepTopOrder) {
                if (idx < 0 || idx >= looked.size()) continue;
                if (millSet.contains(idx)) continue;
                keep.add(looked.get(idx));
            }
        } else {
            for (int i = 0; i < looked.size(); i++) if (!millSet.contains(i)) keep.add(looked.get(i));
        }

        if (bottomRandom) Collections.shuffle(keep);

        // Put kept cards back on top
        libraryOrder.addAll(0, keep);

        db.sync();
        setChanged();
        syncSelf();
    }

    public void shuffleGraveyardIntoLibrary(ServerPlayer player) {
        if (level == null || level.isClientSide()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        var gbe = findAdjacentGraveyard(level, this.worldPosition);
        if (gbe == null) return;

        // pull GY slots 0..99
        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 100);

        // insert into deckbox main slots, leftover eject out back
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
            ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, leftover);
        }

        deckbox.sync();

        // rebuild order + shuffle
        reconcileLibraryOrder(deckbox);
        if (!libraryOrder.isEmpty()) Collections.shuffle(libraryOrder);

        setChanged();
        syncSelf();
    }

    public void resetDeck(ServerPlayer player) {
        if (level == null || level.isClientSide()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        var gbe = findAdjacentGraveyard(level, this.worldPosition);
        if (gbe == null) return;

        // pull GY + EXILE slots 0..199
        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 200);

        // insert into deckbox, leftover eject out back
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
            ejectOutBack((net.minecraft.server.level.ServerLevel) level, worldPosition, facing, leftover);
        }

        deckbox.sync();

        reconcileLibraryOrder(deckbox);
        if (!libraryOrder.isEmpty()) Collections.shuffle(libraryOrder);

        setChanged();
        syncSelf();
    }

    private List<ItemStack> insertIntoDeckboxMain(DeckboxBlockEntity db, List<ItemStack> stacks) {
        List<ItemStack> leftover = new ArrayList<>();
        final int START = 0;
        final int END_EXCL = LIBRARY_SLOTS; // 99

        for (ItemStack in : stacks) {
            if (in == null || in.isEmpty()) continue;
            ItemStack stack = in.copy();

            // merge pass then empty pass
            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = START; i < END_EXCL && !stack.isEmpty(); i++) {
                    ItemStack cur = db.getStack(i);

                    if (pass == 0) {
                        if (!cur.isEmpty()
                                && ItemStack.isSameItemSameComponents(cur, stack)
                                && cur.getCount() < cur.getMaxStackSize()) {
                            int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                            cur.grow(can);
                            stack.shrink(can);
                            db.setStack(i, cur);
                        }
                    } else {
                        if (cur.isEmpty()) {
                            db.setStack(i, stack);
                            stack = ItemStack.EMPTY;
                        }
                    }
                }
            }

            if (!stack.isEmpty()) leftover.add(stack);
        }

        return leftover;
    }

    private List<ItemStack> pullFromGraveyardSlots(com.spider.mtgcard.graveyard.GraveyardBlockEntity gbe, int start, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = start; i < start + count; i++) {
            ItemStack st = gbe.getStack(i);
            if (!st.isEmpty()) {
                out.add(st.copy());
                gbe.setStack(i, ItemStack.EMPTY);
            }
        }
        gbe.markDirty();
        return out;
    }

    private static void dropStackFromModelTop(Level world, BlockPos pos, Direction facing, ItemStack stack) {
        if (world == null || world.isClientSide() || stack == null || stack.isEmpty()) return;

        final double popOut   = 0.12;
        final double jitter   = 0.02;

        double x = pos.getX() + 0.5 + facing.getStepX();
        double y = pos.getY() + 0.5 + facing.getStepY();
        double z = pos.getZ() + 0.5 + facing.getStepZ();

        ItemEntity ent = new ItemEntity(world, x, y, z, stack.copy());

        // Pick two perpendicular directions (always valid)
        Direction a, b;
        switch (facing.getAxis()) {
            case Y -> { // facing UP/DOWN -> jitter in X/Z plane
                a = Direction.EAST;
                b = Direction.SOUTH;
            }
            case X -> { // facing EAST/WEST -> jitter in Y/Z plane
                a = Direction.UP;
                b = Direction.SOUTH;
            }
            case Z -> { // facing NORTH/SOUTH -> jitter in X/Y plane
                a = Direction.EAST;
                b = Direction.UP;
            }
            default -> { // shouldn't happen
                a = Direction.EAST;
                b = Direction.SOUTH;
            }
        }

        double j1 = (world.random.nextDouble() - 0.5) * jitter;
        double j2 = (world.random.nextDouble() - 0.5) * jitter;

        double vx = facing.getStepX() * popOut + a.getStepX() * j1 + b.getStepX() * j2;
        double vy = facing.getStepY() * popOut + a.getStepY() * j1 + b.getStepY() * j2;
        double vz = facing.getStepZ() * popOut + a.getStepZ() * j1 + b.getStepZ() * j2;

        ent.setDeltaMovement(vx, vy, vz);
        ent.setDefaultPickUpDelay();
        world.addFreshEntity(ent);
    }


    /**
     * Returns a direction perpendicular to 'facing' that we can use for jitter.
     * We want a stable choice:
     * - If facing is vertical (UP/DOWN), use NORTH as first axis.
     * - If facing is horizontal, use UP as first axis (so jitter can include vertical a bit if desired).
     */
    private static Direction pickPerpendicular(Direction facing) {
        return switch (facing) {
            case UP, DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }


}
