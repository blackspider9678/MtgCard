package com.spider.mtgcard.deckcontrol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.db.search.CardMeta;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
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

public class DeckControlBlockEntity extends BlockEntity implements MenuProvider {

    public static final int LIBRARY_SLOTS = DeckboxBlockEntity.MAIN_SLOTS;

    private @Nullable BlockPos linkedDeckboxPos = null;
    private boolean lockedLink = false;
    private final List<DeckRef> libraryOrder = new ArrayList<>();

    private boolean wasPowered = false;
    private int cooldownTicks = 0;
    private static final int DRAW_COOLDOWN_TICKS = 5;

    private boolean pendingDeckboxRebuild = false;
    private @Nullable BlockPos pendingDeckboxPos = null;

    private boolean peekActive = false;
    public boolean isPeekActive() { return peekActive; }
    public void setPeekActive(boolean v) { peekActive = v; setChanged(); syncSelf(); }

    private int resolutionId = 0;
    private int cascadeSourceMv = 0;

    public DeckControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECK_CONTROL, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mtgcard.deck_control");
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

    private @Nullable com.spider.mtgcard.graveyard.GraveyardBlockEntity findAdjacentGraveyard(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos p = pos.relative(dir);
            var be = level.getBlockEntity(p);
            if (be instanceof com.spider.mtgcard.graveyard.GraveyardBlockEntity gbe) return gbe;
        }
        return null;
    }

    private List<ItemStack> tryPutIntoGraveyard(com.spider.mtgcard.graveyard.GraveyardBlockEntity gbe, List<ItemStack> stacks) {
        List<ItemStack> leftover = new ArrayList<>();
        final int GRAVE_START = 0;
        final int GRAVE_COUNT = 100;

        for (ItemStack in : stacks) {
            if (in == null || in.isEmpty()) continue;
            ItemStack stack = in.copy();

            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = GRAVE_START; i < GRAVE_START + GRAVE_COUNT && !stack.isEmpty(); i++) {
                    ItemStack cur = gbe.getItem(i);
                    if (pass == 0) {
                        if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, stack) && cur.getCount() < cur.getMaxStackSize()) {
                            int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                            cur.grow(can);
                            stack.shrink(can);
                            gbe.setItem(i, cur);
                        }
                    } else {
                        if (cur.isEmpty()) {
                            gbe.setItem(i, stack);
                            stack = ItemStack.EMPTY;
                        }
                    }
                }
            }

            if (!stack.isEmpty()) leftover.add(stack);
        }

        if (!leftover.isEmpty()) gbe.setChanged();
        return leftover;
    }

    private void ejectOutBack(ServerLevel level, BlockPos pos, Direction facing, List<ItemStack> stacks) {
        Direction outDir = facing.getOpposite();
        double x = pos.getX() + 0.5 + outDir.getStepX() * 0.6;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5 + outDir.getStepZ() * 0.6;

        for (ItemStack st : stacks) {
            if (st == null || st.isEmpty()) continue;
            ItemEntity ent = new ItemEntity(level, x, y, z, st.copy());
            ent.setDeltaMovement(outDir.getStepX() * 0.25, 0.15, outDir.getStepZ() * 0.25);
            level.addFreshEntity(ent);
        }
    }

    private record DeckRef(int slot, String key, int face) {
        static final Codec<DeckRef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("Slot").forGetter(DeckRef::slot),
                Codec.STRING.fieldOf("Key").forGetter(DeckRef::key),
                Codec.INT.optionalFieldOf("Face", 0).forGetter(DeckRef::face)
        ).apply(inst, DeckRef::new));
    }

    public void tick() {
        if (level == null) return;
        if (level.isClientSide()) return;
        if (cooldownTicks > 0) cooldownTicks--;

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

    public boolean hasLinkedDeckbox() {
        if (level == null || linkedDeckboxPos == null) return false;
        return level.getBlockEntity(linkedDeckboxPos) instanceof DeckboxBlockEntity;
    }

    public int getLibraryCount() {
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return 0;
        int used = 0;
        for (int i = 0; i < LIBRARY_SLOTS; i++) if (!db.getItem(i).isEmpty()) used++;
        return used;
    }

    private boolean isLibraryOrderConsistent(DeckboxBlockEntity db) {
        if (db == null) return false;

        int nonEmpty = 0;
        for (int i = 0; i < LIBRARY_SLOTS; i++) {
            if (!db.getItem(i).isEmpty()) nonEmpty++;
        }
        if (libraryOrder.size() != nonEmpty) return false;

        for (DeckRef ref : libraryOrder) {
            if (ref == null) return false;

            int slot = ref.slot();
            if (slot < 0 || slot >= LIBRARY_SLOTS) return false;

            ItemStack st = db.getItem(slot);
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

            ItemStack st = db.getItem(s);
            if (st.isEmpty()) continue;
            if (!computeKey(st).equals(ref.key())) continue;

            kept.add(ref);
            usedSlot[s] = true;
        }

        long seed = (level != null ? level.getGameTime() : 0L) ^ worldPosition.asLong();
        Random r = new Random(seed);

        for (int s = 0; s < LIBRARY_SLOTS; s++) {
            if (usedSlot[s]) continue;

            ItemStack st = db.getItem(s);
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

        if (libraryOrder.isEmpty() || !isLibraryOrderConsistent(db)) {
            rebuildLibraryOrder(db);
        }

        if (libraryOrder.size() > 1) {
            long seed = (level.getGameTime() * 31L) ^ worldPosition.asLong();
            Collections.shuffle(libraryOrder, new Random(seed));
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

        ItemStack stack = deckbox.getItem(ref.slot());
        if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
            reconcileLibraryOrder(deckbox);
            setChanged();
            syncSelf();
            return;
        }

        ItemStack removed = deckbox.removeItem(ref.slot(), deckbox.getItem(ref.slot()).getCount());
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

    public List<ItemStack> peekTopCopies(int n) {
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return List.of();

        ensureLinkedAndBuilt();

        n = Math.min(Math.max(0, n), libraryOrder.size());
        if (n <= 0) return List.of();

        for (int attempt = 0; attempt < 2; attempt++) {
            var out = new ArrayList<ItemStack>(n);

            for (int i = 0; i < n; i++) {
                DeckRef ref = libraryOrder.get(i);
                ItemStack st = db.getItem(ref.slot());

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

        var out = new ArrayList<ItemStack>(n);
        for (int i = 0; i < n && i < libraryOrder.size(); i++) {
            DeckRef ref = libraryOrder.get(i);
            ItemStack st = db.getItem(ref.slot());
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
        List<ItemStack> removedStacks = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (libraryOrder.isEmpty()) break;

            DeckRef ref = libraryOrder.remove(0);
            ItemStack stack = db.getItem(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
                reconcileLibraryOrder(db);
                if (!isLibraryOrderConsistent(db)) rebuildLibraryOrder(db);
                break;
            }

            ItemStack removed = db.removeItem(ref.slot(), db.getItem(ref.slot()).getCount());
            if (!removed.isEmpty()) removedStacks.add(removed);
        }

        if (!removedStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                List<ItemStack> leftover = tryPutIntoGraveyard(gbe, removedStacks);
                if (!leftover.isEmpty() && level instanceof ServerLevel sl) ejectOutBack(sl, worldPosition, facing, leftover);
            } else if (level instanceof ServerLevel sl) {
                ejectOutBack(sl, worldPosition, facing, removedStacks);
            }
        }

        db.sync();
        setChanged();
        syncSelf();
    }

    public boolean placeFromPlayer(Player player, int playerInvSlot, int indexFromTop) {
        if (level == null || level.isClientSide()) return false;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return false;

        ensureLinkedAndBuilt();

        if (playerInvSlot < 0 || playerInvSlot >= player.getInventory().getContainerSize()) return false;
        ItemStack st = player.getInventory().getItem(playerInvSlot);
        if (st.isEmpty()) return false;
        if (!st.is(com.spider.mtgcard.item.ModItems.CARD)) return false;

        int empty = -1;
        for (int i = 0; i < LIBRARY_SLOTS; i++) {
            if (db.getItem(i).isEmpty()) { empty = i; break; }
        }
        if (empty == -1) return false;

        ItemStack moved = st.split(1);
        if (moved.isEmpty()) return false;

        db.setItem(empty, moved);
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
        List<ItemStack> milledStacks = new ArrayList<>();

        for (int i = 0; i < looked.size(); i++) {
            if (!millSet.contains(i)) continue;

            DeckRef ref = looked.get(i);
            ItemStack stack = db.getItem(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

            ItemStack removed = db.removeItem(ref.slot(), db.getItem(ref.slot()).getCount());
            if (!removed.isEmpty()) milledStacks.add(removed);
        }

        if (!milledStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty() && level instanceof ServerLevel sl) ejectOutBack(sl, worldPosition, facing, leftover);
            } else if (level instanceof ServerLevel sl) {
                ejectOutBack(sl, worldPosition, facing, milledStacks);
            }
        }

        var keep = new ArrayList<DeckRef>();
        for (int i = 0; i < looked.size(); i++) if (!millSet.contains(i)) keep.add(looked.get(i));

        if (bottomRandom) Collections.shuffle(keep);
        libraryOrder.addAll(0, keep);

        db.sync();
        setChanged();
        syncSelf();
    }

    private static int mvOf(ItemStack st) { return CardMeta.read(st).mv(); }

    private static boolean isLand(ItemStack st) {
        String type = CardMeta.read(st).typeLine();
        return type != null && type.contains("Land");
    }

    private void putStacksOnBottom(DeckboxBlockEntity deckbox, List<ItemStack> stacks) {
        if (level == null) return;

        if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
        else rebuildLibraryOrder(deckbox);

        List<DeckRef> appended = new ArrayList<>();
        int idx = 0;

        for (int slot = LIBRARY_SLOTS - 1; slot >= 0 && idx < stacks.size(); slot--) {
            if (deckbox.getItem(slot).isEmpty()) {
                ItemStack placed = stacks.get(idx++);
                deckbox.setItem(slot, placed);
                appended.add(new DeckRef(slot, computeKey(placed), 0));
            }
        }

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        while (idx < stacks.size()) {
            ejectStack(level, worldPosition, facing, stacks.get(idx++));
        }

        libraryOrder.addAll(appended);
        setChanged();
    }

    private void ensureLinkedAndBuilt() {
        DeckboxBlockEntity deckbox = findOrLinkDeckbox();
        if (deckbox == null) return;
        if (libraryOrder.isEmpty()) rebuildLibraryOrder(deckbox);
    }

    private @Nullable DeckboxBlockEntity findOrLinkDeckbox() {
        if (level == null) return null;

        if (linkedDeckboxPos != null) {
            BlockEntity be = level.getBlockEntity(linkedDeckboxPos);
            if (be instanceof DeckboxBlockEntity db) return db;

            linkedDeckboxPos = null;
            if (lockedLink) return null;
        }

        if (lockedLink) return null;

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
            ItemStack stack = deckbox.getItem(i);
            if (stack.isEmpty()) continue;
            libraryOrder.add(new DeckRef(i, computeKey(stack), 0));
        }

        setChanged();
        syncSelf();
    }

    public void onNeighborDeckboxChanged(BlockPos deckboxPos) {
        if (level == null || level.isClientSide()) return;

        if (linkedDeckboxPos != null) {
            if (!linkedDeckboxPos.equals(deckboxPos)) return;
        } else {
            if (lockedLink) return;
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

    private static void ejectStack(Level level, BlockPos pos, Direction facing, ItemStack stack) {
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.6;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;

        ItemEntity itemEntity = new ItemEntity(level, x, y, z, stack);
        itemEntity.setDeltaMovement(
                facing.getStepX() * 0.25,
                0.05,
                facing.getStepZ() * 0.25
        );
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    private static String computeKey(ItemStack stack) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(stack.getItem().toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte) stack.getCount());
            md.update(stack.getComponents().toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return stack.getItem().toString() + "|" + stack.getCount() + "|" + stack.getComponents();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        long link = input.getLongOr("LinkedDeckbox", 0L);
        linkedDeckboxPos = (link != 0L) ? BlockPos.of(link) : null;

        lockedLink = input.getBooleanOr("LockedLink", false);
        peekActive = input.getBooleanOr("PeekActive", false);
        wasPowered = input.getBooleanOr("WasPowered", false);
        cooldownTicks = input.getIntOr("Cooldown", 0);
        cooldownTicks = input.getInt("Cooldown").orElse(0);

        libraryOrder.clear();
        var loaded = input.read("LibraryOrder", DeckRef.CODEC.listOf()).orElse(List.of());
        libraryOrder.addAll(loaded);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        output.putLong("LinkedDeckbox", linkedDeckboxPos != null ? linkedDeckboxPos.asLong() : 0L);
        output.putBoolean("LockedLink", lockedLink);
        output.putBoolean("PeekActive", peekActive);
        output.putBoolean("WasPowered", wasPowered);
        output.putInt("Cooldown", cooldownTicks);
        output.store("LibraryOrder", DeckRef.CODEC.listOf(), libraryOrder);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return this.saveCustomOnly(lookup);
    }

    private void syncSelf() {
        if (level == null || level.isClientSide()) return;
        var state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    private final Map<UUID, PendingCascade> pendingCascade = new HashMap<>();

    private static final class PendingCascade {
        final int sourceMv;
        final List<ItemStack> revealedActual;
        final int hitIndex;
        final int resolutionId;

        PendingCascade(int sourceMv, List<ItemStack> revealedActual, int hitIndex, int resolutionId) {
            this.sourceMv = sourceMv;
            this.revealedActual = revealedActual;
            this.hitIndex = hitIndex;
            this.resolutionId = resolutionId;
        }
    }

    public void cancelPendingCascade(ServerPlayer player) {
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
        if (pendingCascade.containsKey(player.getUUID())) return;

        int mv = Math.max(0, sourceManaValue);
        int localResolutionId = ++resolutionId;

        var revealed = new ArrayList<ItemStack>();
        int hitIndex = -1;

        while (!libraryOrder.isEmpty()) {
            DeckRef ref = libraryOrder.remove(0);

            ItemStack stack = deckbox.getItem(ref.slot());
            if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) {
                if (!libraryOrder.isEmpty()) reconcileLibraryOrder(deckbox);
                if (!isLibraryOrderConsistent(deckbox)) rebuildLibraryOrder(deckbox);

                bottomRandom(deckbox, revealed, localResolutionId);
                deckbox.sync();
                setChanged();
                syncSelf();
                return;
            }

            ItemStack removed = deckbox.removeItem(ref.slot(), deckbox.getItem(ref.slot()).getCount());
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
                break;
            }
        }

        deckbox.sync();
        reconcileLibraryOrder(deckbox);

        pendingCascade.put(player.getUUID(), new PendingCascade(mv, revealed, hitIndex, localResolutionId));
        indexAdd(player.getUUID(), this.worldPosition);

        var copies = revealed.stream().filter(s -> s != null && !s.isEmpty()).map(ItemStack::copy).toList();

        player.connection.send(ServerPlayNetworking.createClientboundPacket(
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

    private static final Map<UUID, Set<BlockPos>> PENDING_BY_PLAYER = new HashMap<>();

    private static void indexAdd(UUID playerId, BlockPos pos) {
        PENDING_BY_PLAYER.computeIfAbsent(playerId, k -> new HashSet<>()).add(pos);
    }

    private static void indexRemove(UUID playerId, BlockPos pos) {
        var set = PENDING_BY_PLAYER.get(playerId);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) PENDING_BY_PLAYER.remove(playerId);
    }

    public static Set<BlockPos> getPendingPositions(UUID playerId) {
        var set = PENDING_BY_PLAYER.get(playerId);
        return (set == null) ? Set.of() : Set.copyOf(set);
    }

    public void resolveOrderedScry(int lookedN, int topCount, int[] order, boolean bottomRandom) {
        if (level == null || level.isClientSide()) return;
        DeckboxBlockEntity db = findOrLinkDeckbox();
        if (db == null) return;

        ensureLinkedAndBuilt();
        lookedN = Math.min(lookedN, libraryOrder.size());
        if (lookedN <= 0) return;

        topCount = Math.max(0, Math.min(topCount, lookedN));

        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var top = new ArrayList<DeckRef>(topCount);
        var bottom = new ArrayList<DeckRef>(lookedN - topCount);

        for (int i = 0; i < lookedN; i++) {
            int idx = (order != null && i < order.length) ? order[i] : i;
            if (idx < 0 || idx >= looked.size()) continue;

            if (i < topCount) top.add(looked.get(idx));
            else bottom.add(looked.get(idx));
        }

        if (bottomRandom) Collections.shuffle(bottom);

        libraryOrder.addAll(0, top);
        libraryOrder.addAll(bottom);

        setChanged();
        syncSelf();
    }

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

        var looked = new ArrayList<DeckRef>(lookedN);
        for (int i = 0; i < lookedN; i++) looked.add(libraryOrder.remove(0));

        var millSet = new HashSet<Integer>();
        if (millOrder != null) millSet.addAll(millOrder);

        Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
        List<ItemStack> milledStacks = new ArrayList<>();
        if (millOrder != null) {
            for (int idx : millOrder) {
                if (idx < 0 || idx >= looked.size()) continue;
                DeckRef ref = looked.get(idx);

                ItemStack stack = db.getItem(ref.slot());
                if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

                ItemStack removed = db.removeItem(ref.slot(), db.getItem(ref.slot()).getCount());
                if (!removed.isEmpty()) milledStacks.add(removed);
            }
        } else {
            for (int i = 0; i < looked.size(); i++) {
                if (!millSet.contains(i)) continue;
                DeckRef ref = looked.get(i);

                ItemStack stack = db.getItem(ref.slot());
                if (stack.isEmpty() || !computeKey(stack).equals(ref.key())) continue;

                ItemStack removed = db.removeItem(ref.slot(), db.getItem(ref.slot()).getCount());
                if (!removed.isEmpty()) milledStacks.add(removed);
            }
        }

        if (!milledStacks.isEmpty()) {
            var gbe = findAdjacentGraveyard(level, this.worldPosition);
            if (gbe != null) {
                var leftover = tryPutIntoGraveyard(gbe, milledStacks);
                if (!leftover.isEmpty() && level instanceof ServerLevel sl) ejectOutBack(sl, worldPosition, facing, leftover);
            } else if (level instanceof ServerLevel sl) {
                ejectOutBack(sl, worldPosition, facing, milledStacks);
            }
        }

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

        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 100);
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
            if (level instanceof ServerLevel sl) ejectOutBack(sl, worldPosition, facing, leftover);
        }

        deckbox.sync();
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

        List<ItemStack> pulled = pullFromGraveyardSlots(gbe, 0, 200);
        List<ItemStack> leftover = insertIntoDeckboxMain(deckbox, pulled);
        if (!leftover.isEmpty()) {
            Direction facing = getBlockState().getValue(DeckControlBlock.FACING);
            if (level instanceof ServerLevel sl) ejectOutBack(sl, worldPosition, facing, leftover);
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
        final int END_EXCL = LIBRARY_SLOTS;

        for (ItemStack in : stacks) {
            if (in == null || in.isEmpty()) continue;
            ItemStack stack = in.copy();

            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = START; i < END_EXCL && !stack.isEmpty(); i++) {
                    ItemStack cur = db.getItem(i);

                    if (pass == 0) {
                        if (!cur.isEmpty()
                                && ItemStack.isSameItemSameComponents(cur, stack)
                                && cur.getCount() < cur.getMaxStackSize()) {
                            int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
                            cur.grow(can);
                            stack.shrink(can);
                            db.setItem(i, cur);
                        }
                    } else {
                        if (cur.isEmpty()) {
                            db.setItem(i, stack);
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
            ItemStack st = gbe.getItem(i);
            if (!st.isEmpty()) {
                out.add(st.copy());
                gbe.setItem(i, ItemStack.EMPTY);
            }
        }
        gbe.setChanged();
        return out;
    }

    private static void dropStackFromModelTop(Level level, BlockPos pos, Direction facing, ItemStack stack) {
        if (level == null || level.isClientSide() || stack == null || stack.isEmpty()) return;

        final double popOut = 0.12;
        final double jitter = 0.02;

        double x = pos.getX() + 0.5 + facing.getStepX();
        double y = pos.getY() + 0.5 + facing.getStepY();
        double z = pos.getZ() + 0.5 + facing.getStepZ();

        ItemEntity ent = new ItemEntity(level, x, y, z, stack.copy());

        Direction a, b;
        switch (facing.getAxis()) {
            case Y -> {
                a = Direction.EAST;
                b = Direction.SOUTH;
            }
            case X -> {
                a = Direction.UP;
                b = Direction.SOUTH;
            }
            case Z -> {
                a = Direction.EAST;
                b = Direction.UP;
            }
            default -> {
                a = Direction.EAST;
                b = Direction.SOUTH;
            }
        }

        double j1 = (level.getRandom().nextDouble() - 0.5) * jitter;
        double j2 = (level.getRandom().nextDouble() - 0.5) * jitter;

        double vx = facing.getStepX() * popOut + a.getStepX() * j1 + b.getStepX() * j2;
        double vy = facing.getStepY() * popOut + a.getStepY() * j1 + b.getStepY() * j2;
        double vz = facing.getStepZ() * popOut + a.getStepZ() * j1 + b.getStepZ() * j2;

        ent.setDeltaMovement(vx, vy, vz);
        ent.setDefaultPickUpDelay();
        level.addFreshEntity(ent);
    }

    private static Direction pickPerpendicular(Direction facing) {
        return switch (facing) {
            case UP, DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }
}