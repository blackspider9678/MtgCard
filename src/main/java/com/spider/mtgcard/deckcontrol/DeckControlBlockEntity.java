package com.spider.mtgcard.deckcontrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.db.search.CardMeta;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
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
    public void setPeekActive(boolean v) { peekActive = v; markDirty(); syncSelf(); }

    // ----- Resolution / Cascade transaction -----
    private int resolutionId = 0;

    private int cascadeSourceMv = 0;

    public DeckControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECK_CONTROL, pos, state);
    }

    /* ---------------- Screen opening (Extended) ---------------- */

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.mtgcard.deck_control");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new DeckControlScreenHandler(
                syncId,
                inv,
                this.pos,
                ScreenHandlerContext.create(Objects.requireNonNull(world), this.pos)
        );
    }

    @org.jetbrains.annotations.Nullable
    private com.spider.mtgcard.graveyard.GraveyardBlockEntity findAdjacentGraveyard(net.minecraft.world.World world, BlockPos pos) {
        for (var dir : net.minecraft.util.math.Direction.values()) {
            BlockPos p = pos.offset(dir);
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
                        if (!cur.isEmpty() && ItemStack.areItemsAndComponentsEqual(cur, stack) && cur.getCount() < cur.getMaxCount()) {
                            int can = Math.min(stack.getCount(), cur.getMaxCount() - cur.getCount());
                            cur.increment(can);
                            stack.decrement(can);
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

    private void ejectOutBack(net.minecraft.server.world.ServerWorld world, BlockPos pos, net.minecraft.util.math.Direction facing, java.util.List<ItemStack> stacks) {
        var outDir = facing.getOpposite();
        double x = pos.getX() + 0.5 + outDir.getOffsetX() * 0.6;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5 + outDir.getOffsetZ() * 0.6;

        for (ItemStack st : stacks) {
            if (st == null || st.isEmpty()) continue;
            var ent = new net.minecraft.entity.ItemEntity(world, x, y, z, st.copy());
            ent.setVelocity(outDir.getOffsetX() * 0.25, 0.15, outDir.getOffsetZ() * 0.25);
            world.spawnEntity(ent);
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
        if (world == null) return;

        if (world.isClient()) return;
        if (cooldownTicks > 0) cooldownTicks--;

        // Rebuild if deckbox changed (debounced)
        if (pendingDeckboxRebuild && pendingDeckboxPos != null) {
            var be = world.getBlockEntity(pendingDeckboxPos);
            if (be instanceof DeckboxBlockEntity db) {
                if (libraryOrder.isEmpty()) rebuildLibraryOrder(db);
                else {
                    reconcileLibraryOrder(db);
                    if (!isLibraryOrderConsistent(db)) rebuildLibraryOrder(db);
                }
            }
            pendingDeckboxRebuild = false;
            pendingDeckboxPos = null;
            markDirty();
            syncSelf();
        }

        boolean powered = world.isReceivingRedstonePower(pos);
        if (powered && !wasPowered && cooldownTicks <= 0) {
            drawTopAndEject();
            cooldownTicks = DRAW_COOLDOWN_TICKS;
        }
        wasPowered = powered;
    }

    /* ---------------- Status helpers for ScreenHandler props ---------------- */

    public boolean hasLinkedDeckbox() {
        if (world == null || linkedDeckboxPos == null) return false;
        return world.getBlockEntity(linkedDeckboxPos) instanceof DeckboxBlockEntity;
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

        long seed = (world != null ? world.getTime() : 0L) ^ pos.asLong();
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

        markDirty();
    }


    public void shuffle() {
        if (world == null || world.isClient()) return;

        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        // Ensure we shuffle a *valid* order, otherwise the next draw/peek will rebuild
        if (libraryOrder.isEmpty() || !isLibraryOrderConsistent(db)) {
            rebuildLibraryOrder(db);
        }

        if (libraryOrder.size() > 1) {
            // Optional: stable-ish server-side randomness
            long seed = (world.getTime() * 31L) ^ pos.asLong();
            Collections.shuffle(libraryOrder, new Random(seed));
            // If you prefer fully random each click, just use Collections.shuffle(libraryOrder);
        }

        markDirty();
        syncSelf();
    }


    public void drawTopAndEject() {
        if (world == null || world.isClient()) return;

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

            markDirty();
            syncSelf();
            return;
        }

        ItemStack removed = deckbox.removeStack(ref.slot());
        if (removed.isEmpty()) {
            reconcileLibraryOrder(deckbox);
            if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);
            markDirty();
            syncSelf();
            return;
        }

        deckbox.sync();

        Direction facing = getCachedState().get(DeckControlBlock.FACING);
        ejectStack(world, pos, facing, removed);

        markDirty();
        syncSelf();
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
        if (world == null || world.isClient()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        n = Math.min(Math.max(1, n), libraryOrder.size());
        if (n <= 0) return;

        Direction facing = getCachedState().get(DeckControlBlock.FACING);

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
            var gbe = findAdjacentGraveyard(world, this.pos);
            if (gbe != null) {
                List<ItemStack> leftover = tryPutIntoGraveyard(gbe, removedStacks);
                if (!leftover.isEmpty()) ejectOutBack((ServerWorld) world, pos, facing, leftover);

            } else {
                ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, removedStacks);
            }
        }

        db.sync();
        markDirty();
        syncSelf();
    }

    /** Insert 1 card item from player's inventory into library at indexFromTop (0..size). */
    public boolean placeFromPlayer(PlayerEntity player, int playerInvSlot, int indexFromTop) {
        if (world == null || world.isClient()) return false;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return false;

        ensureLinkedAndBuilt();

        if (playerInvSlot < 0 || playerInvSlot >= player.getInventory().size()) return false;
        ItemStack st = player.getInventory().getStack(playerInvSlot);
        if (st.isEmpty()) return false;

        if (!st.isOf(com.spider.mtgcard.item.ModItems.CARD)) return false;

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

        markDirty();
        syncSelf();
        return true;
    }

    public boolean placeFromPlayerBottom(PlayerEntity player, int playerInvSlot) {
        return placeFromPlayer(player, playerInvSlot, Integer.MAX_VALUE);
    }

    /** Server-side scry resolution: keepOrder are indices into the looked list in desired order. */
    public void resolveScry(List<Integer> keepOrder, boolean bottomRandom, int lookedN) {
        if (world == null || world.isClient()) return;
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

        markDirty();
        syncSelf();
    }

    /** Surveil: indices in toMill are removed (temp drop), rest stay on top. */
    public void resolveSurveil(PlayerEntity player, List<Integer> toMill, boolean bottomRandom, int lookedN) {
        if (world == null || world.isClient()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var millSet = new HashSet<Integer>();
        if (toMill != null) millSet.addAll(toMill);

        Direction facing = getCachedState().get(DeckControlBlock.FACING);

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
            var gbe = findAdjacentGraveyard(world, this.pos);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty()) ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, leftover);
            } else {
                ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, milledStacks);
            }
        }

        // Keep = everything not milled stays on top (in original order)
        var keep = new ArrayList<DeckRef>();
        for (int i = 0; i < looked.size(); i++) if (!millSet.contains(i)) keep.add(looked.get(i));

        if (bottomRandom) Collections.shuffle(keep);
        libraryOrder.addAll(0, keep);

        db.sync();
        markDirty();
        syncSelf();
    }

    /* ---------------- Cascade ---------------- */

    private static int mvOf(ItemStack st) { return CardMeta.read(st).mv(); }

    private static boolean isLand(ItemStack st) {
        String type = CardMeta.read(st).typeLine();
        return type != null && type.contains("Land");
    }

    private void putStacksOnBottom(DeckboxBlockEntity deckbox, List<ItemStack> stacks) {
        if (world == null) return;

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

        Direction facing = getCachedState().get(DeckControlBlock.FACING);
        while (idx < stacks.size()) {
            ejectStack(world, pos, facing, stacks.get(idx++));
        }

        // ✅ appended cards are bottom of library
        libraryOrder.addAll(appended);

        markDirty();
    }

    /* ---------------- Linking / Building ---------------- */

    private void ensureLinkedAndBuilt() {
        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;
        if (libraryOrder.isEmpty()) rebuildLibraryOrder(deckbox);
    }

    private @Nullable DeckboxBlockEntity findOrLinkDeckbox() {
        if (world == null) return null;

        // stored link first
        if (linkedDeckboxPos != null) {
            BlockEntity be = world.getBlockEntity(linkedDeckboxPos);
            if (be instanceof DeckboxBlockEntity db) return db;

            // linked deckbox gone
            linkedDeckboxPos = null;

            // locked? do not relink automatically
            if (lockedLink) return null;
        }

        if (lockedLink) return null;

        // scan neighbors once; lock on first
        for (Direction d : Direction.values()) {
            BlockPos p = pos.offset(d);
            BlockEntity be = world.getBlockEntity(p);
            if (be instanceof DeckboxBlockEntity db) {
                linkedDeckboxPos = p;
                lockedLink = true;
                rebuildLibraryOrder(db);
                markDirty();
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

        markDirty();
        syncSelf();
    }

    public void onNeighborDeckboxChanged(BlockPos deckboxPos) {
        if (world == null || world.isClient()) return;

        if (linkedDeckboxPos != null) {
            if (!linkedDeckboxPos.equals(deckboxPos)) return;
        } else {
            if (lockedLink) return;
            if (pos.getManhattanDistance(deckboxPos) != 1) return;

            linkedDeckboxPos = deckboxPos;
            lockedLink = true;
        }

        pendingDeckboxRebuild = true;
        pendingDeckboxPos = deckboxPos;
        markDirty();
    }

    public void clearLinkAndUnlock() {
        linkedDeckboxPos = null;
        lockedLink = false;
        libraryOrder.clear();
        pendingDeckboxRebuild = false;
        pendingDeckboxPos = null;
        markDirty();
        syncSelf();
    }

    /* ---------------- Ejection + keying ---------------- */

    private static void ejectStack(World world, BlockPos pos, Direction facing, ItemStack stack) {
        double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.6;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.6;

        ItemEntity itemEntity = new ItemEntity(world, x, y, z, stack);
        itemEntity.setVelocity(
                facing.getOffsetX() * 0.25,
                0.05,
                facing.getOffsetZ() * 0.25
        );
        itemEntity.setToDefaultPickupDelay();
        world.spawnEntity(itemEntity);
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
    protected void readData(ReadView view) {
        super.readData(view);

        long link = view.getLong("LinkedDeckbox", 0L);
        linkedDeckboxPos = (link != 0L) ? BlockPos.fromLong(link) : null;

        lockedLink = view.getBoolean("LockedLink", false);
        peekActive = view.getBoolean("PeekActive", false);
        wasPowered = view.getBoolean("WasPowered", false);
        cooldownTicks = view.getInt("Cooldown", 0);

        libraryOrder.clear();
        var loaded = view.read("LibraryOrder", DeckRef.CODEC.listOf()).orElse(List.of());
        libraryOrder.addAll(loaded);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        view.putLong("LinkedDeckbox", linkedDeckboxPos != null ? linkedDeckboxPos.asLong() : 0L);
        view.putBoolean("LockedLink", lockedLink);

        view.putBoolean("PeekActive", peekActive);
        view.putBoolean("WasPowered", wasPowered);
        view.putInt("Cooldown", cooldownTicks);

        view.put("LibraryOrder", DeckRef.CODEC.listOf(), libraryOrder);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        return createNbt(lookup);
    }

    private void syncSelf() {
        if (world == null || world.isClient()) return;
        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
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

    public void cancelPendingCascade(ServerPlayerEntity player) {
        // treat cancel as "exile" (i.e., do NOT cast, just bottom everything)
        resolveCascade(player, false);
    }

    public boolean hasPendingCascade(ServerPlayerEntity player) {
        return pendingCascade.containsKey(player.getUuid());
    }

    public void startCascade(ServerPlayerEntity player, int sourceManaValue) {
        if (world == null || world.isClient()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        if (libraryOrder.isEmpty()) rebuildLibraryOrder(deckbox);
        if (libraryOrder.isEmpty()) return;

        // If they already have a pending cascade, ignore (prevents spam)
        if (pendingCascade.containsKey(player.getUuid())) return;

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
                markDirty();
                syncSelf();
                return;
            }

            ItemStack removed = deckbox.removeStack(ref.slot());
            if (removed.isEmpty()) {
                if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
                if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);

                bottomRandom(deckbox, revealed, localResolutionId);
                deckbox.sync();
                markDirty();
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

        pendingCascade.put(player.getUuid(), new PendingCascade(mv, revealed, hitIndex, localResolutionId));
        indexAdd(player.getUuid(), this.pos);

        // Send UI overlay using COPIES (never empties)
        var copies = revealed.stream().filter(s -> s != null && !s.isEmpty()).map(ItemStack::copy).toList();

        player.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(
                new DeckControlPackets.CascadeS2C(this.pos, mv, hitIndex, copies)
        ));

        markDirty();
        syncSelf();
    }
    public void resolveCascade(ServerPlayerEntity player, boolean cast) {
        if (world == null || world.isClient()) return;

        PendingCascade pc = pendingCascade.remove(player.getUuid());
        indexRemove(player.getUuid(), this.pos);
        if (pc == null) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) {
            // no deckbox => just drop everything in front (including hit)
            dropStacksInFront(pc.revealedActual);
            markDirty();
            syncSelf();
            return;
        }

        var pile = new ArrayList<>(pc.revealedActual);

        if (cast && pc.hitIndex >= 0 && pc.hitIndex < pile.size()) {
            ItemStack hit = pile.remove(pc.hitIndex);
            Direction facing = getCachedState().get(DeckControlBlock.FACING);
            ejectStack(world, pos, facing, hit);
        }
        // exile = do nothing special; hit remains in pile

        // bottom all revealed in random order
        bottomRandom(deckbox, pile, pc.resolutionId);

        deckbox.sync();
        markDirty();
        syncSelf();
    }
    private void bottomRandom(DeckboxBlockEntity deckbox, List<ItemStack> stacks, int resId) {
        if (stacks == null || stacks.isEmpty()) return;
        long seed = (world.getTime() * 31L) ^ pos.asLong() ^ ((long) resId * 1315423911L);
        Collections.shuffle(stacks, new Random(seed));
        putStacksOnBottom(deckbox, stacks);
    }

    private void dropStacksInFront(List<ItemStack> stacks) {
        if (world == null || stacks == null) return;
        Direction facing = getCachedState().get(DeckControlBlock.FACING);
        for (ItemStack st : stacks) {
            if (st != null && !st.isEmpty()) ejectStack(world, pos, facing, st);
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
        if (world == null || world.isClient()) return;
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

        markDirty();
        syncSelf();
    }

    /**
     * Ordered Surveil:
     * - keepTopOrder: indices into looked list that stay on top (in that exact order)
     * - millOrder: indices into looked list to mill (eject) (order doesn't *need* to matter, but we keep it)
     */
    public void resolveSurveilOrdered(ServerPlayerEntity player,
                                      List<Integer> keepTopOrder,
                                      List<Integer> millOrder,
                                      boolean bottomRandom,
                                      int lookedN) {
        if (world == null || world.isClient()) return;
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

        Direction facing = getCachedState().get(DeckControlBlock.FACING);

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
            var gbe = findAdjacentGraveyard(world, this.pos);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty()) ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, leftover);
            } else {
                ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, milledStacks);
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
        markDirty();
        syncSelf();
    }

    public void shuffleGraveyardIntoLibrary(ServerPlayerEntity player) {
        if (world == null || world.isClient()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        var gbe = findAdjacentGraveyard(world, this.pos);
        if (gbe == null) return;

        // pull GY slots 0..99
        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 100);

        // insert into deckbox main slots, leftover eject out back
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getCachedState().get(DeckControlBlock.FACING);
            ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, leftover);
        }

        deckbox.sync();

        // rebuild order + shuffle
        reconcileLibraryOrder(deckbox);
        if (!libraryOrder.isEmpty()) Collections.shuffle(libraryOrder);

        markDirty();
        syncSelf();
    }

    public void resetDeck(ServerPlayerEntity player) {
        if (world == null || world.isClient()) return;

        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;

        var gbe = findAdjacentGraveyard(world, this.pos);
        if (gbe == null) return;

        // pull GY + EXILE slots 0..199
        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 200);

        // insert into deckbox, leftover eject out back
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getCachedState().get(DeckControlBlock.FACING);
            ejectOutBack((net.minecraft.server.world.ServerWorld) world, pos, facing, leftover);
        }

        deckbox.sync();

        reconcileLibraryOrder(deckbox);
        if (!libraryOrder.isEmpty()) Collections.shuffle(libraryOrder);

        markDirty();
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
                                && ItemStack.areItemsAndComponentsEqual(cur, stack)
                                && cur.getCount() < cur.getMaxCount()) {
                            int can = Math.min(stack.getCount(), cur.getMaxCount() - cur.getCount());
                            cur.increment(can);
                            stack.decrement(can);
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

}
