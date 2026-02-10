// com/spider/mtgcard/graveyard/GraveyardBlockEntity.java
package com.spider.mtgcard.graveyard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.registry.ModBlockEntities; // or ModBlocks.GRAVEYARD_BE if you kept it there

import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class GraveyardBlockEntity extends BlockEntity implements MenuProvider, WorldlyContainer {

    private int viewers = 0;

    public void onViewerOpen() {
        if (level == null || level.isClientSide()) return;
        viewers++;
        if (viewers == 1) setOpen(true);
    }

    public void onViewerClose() {
        if (level == null || level.isClientSide()) return;
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0) setOpen(false);
    }

    private void setOpen(boolean open) {
        if (level == null) return;
        BlockState st = getBlockState();
        if (st.hasProperty(GraveyardBlock.OPEN) && st.getValue(GraveyardBlock.OPEN) != open) {
            level.setBlock(worldPosition, st.setValue(GraveyardBlock.OPEN, open), Block.UPDATE_ALL);
        }
    }

    public static final int SIDE_SIZE = 100; // 10x10
    public static final int TOTAL = 200;     // graveyard(100) + exile(100)

    private final NonNullList<net.minecraft.world.item.ItemStack> items =
            NonNullList.withSize(TOTAL, net.minecraft.world.item.ItemStack.EMPTY);

    // ---- Hopper / automation (SidedInventory) ----

    private static final int[] GRAVEYARD_SLOTS = makeRange(0, SIDE_SIZE); // 0..99

    private static int[] makeRange(int start, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = start + i;
        return out;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // Hopper input goes ONLY to graveyard side (0..99)
        return GRAVEYARD_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot < 0 || slot >= TOTAL) return false;
        // only allow CARD items (both sides are “card-only”)
        return !stack.isEmpty() && stack.is(com.spider.mtgcard.item.ModItems.CARD);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        // If you want to block hopper extraction completely, use:
        return false;
    }

    public GraveyardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVEYARD, pos, state); // <- match your registry location
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mtgcard.graveyard");
    }

    @Override
    public boolean shouldCloseCurrentScreen() {
        return MenuProvider.super.shouldCloseCurrentScreen();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new GraveyardScreenHandler(
                syncId,
                inv,
                this.worldPosition,
                ContainerLevelAccess.create(Objects.requireNonNull(level), this.worldPosition)
        );
    }

    // ---- inventory access (ScreenHandler will use these) ----
    public NonNullList<net.minecraft.world.item.ItemStack> getItems() { return items; }

    public net.minecraft.world.item.ItemStack getItem(int slot) { return items.get(slot); }
    public net.minecraft.world.item.ItemStack getStack(int slot) { return getItem(slot); }
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
        syncSelf();
        updateComparators();
    }
    public void setStack(int slot, ItemStack stack) { setItem(slot, stack); }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack st = getItem(slot);
        if (st.isEmpty()) return ItemStack.EMPTY;
        items.set(slot, ItemStack.EMPTY);
        setChanged();
        syncSelf();
        updateComparators();
        return st;
    }

    public ItemStack removeStack(int slot) {
        return removeItemNoUpdate(slot);
    }

    public void markDirty() {
        setChanged();
    }

    private void syncSelf() {
        if (level == null || level.isClientSide()) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---- persistence (we’ll keep it simple for now) ----
    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        // Clear first
        for (int i = 0; i < TOTAL; i++) items.set(i, ItemStack.EMPTY);

        var list = view.read("Items", SlotStack.CODEC.listOf()).orElse(java.util.List.of());
        for (SlotStack ss : list) {
            int s = ss.slot();
            if (s >= 0 && s < TOTAL) {
                items.set(s, ss.stack());
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        var out = new java.util.ArrayList<SlotStack>();
        for (int i = 0; i < TOTAL; i++) {
            ItemStack st = items.get(i);
            if (!st.isEmpty()) out.add(new SlotStack(i, st));
        }
        view.store("Items", SlotStack.CODEC.listOf(), out);
    }

    // GraveyardBlockEntity.java (add inside class)

    private static final int GRID = 100; // 10x10
    private static final int GY_START = 0;
    private static final int EX_START = 100;

    public void exileAll() {
        if (level == null || level.isClientSide()) return;

        // Move all from graveyard -> exile
        moveRange(GY_START, EX_START);
    }

    public void returnAll() {
        if (level == null || level.isClientSide()) return;

        // Move all from exile -> graveyard
        moveRange(EX_START, GY_START);
    }

    private void moveRange(int fromStart, int toStart) {
        if (level == null) return;

        // 1) collect cards from "from" side in slot order
        java.util.ArrayList<net.minecraft.world.item.ItemStack> moving = new java.util.ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            int fromSlot = fromStart + i;
            var st = items.get(fromSlot);
            if (!st.isEmpty()) {
                moving.add(st);
                items.set(fromSlot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }

        if (moving.isEmpty()) {
            setChanged();
            syncSelf();
            return;
        }

        // 2) place into "to" side, first empty slots in order
        int idx = 0;
        for (int i = 0; i < GRID && idx < moving.size(); i++) {
            int toSlot = toStart + i;
            if (items.get(toSlot).isEmpty()) {
                items.set(toSlot, moving.get(idx++));
            }
        }

        // 3) overflow -> eject out the back of the block
        if (idx < moving.size()) {
            net.minecraft.core.Direction back = getBackDirection();
            for (; idx < moving.size(); idx++) {
                ejectStack(level, worldPosition, back, moving.get(idx));
            }
        }

        setChanged();
        syncSelf();
        updateComparators();
    }

    private net.minecraft.core.Direction getBackDirection() {
        var state = getBlockState();
        // If your GraveyardBlock has FACING (you do), use it. Otherwise fallback.
        if (state != null && state.hasProperty(com.spider.mtgcard.graveyard.GraveyardBlock.FACING)) {
            var facing = state.getValue(com.spider.mtgcard.graveyard.GraveyardBlock.FACING);
            return facing.getOpposite();
        }
        return net.minecraft.core.Direction.NORTH;
    }

    private static void ejectStack(net.minecraft.world.level.Level world, net.minecraft.core.BlockPos pos,
                                   net.minecraft.core.Direction dir, net.minecraft.world.item.ItemStack stack) {
        double x = pos.getX() + 0.5 + dir.getStepX() * 0.6;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.6;

        var ent = new net.minecraft.world.entity.item.ItemEntity(world, x, y, z, stack);
        ent.setDeltaMovement(dir.getStepX() * 0.25, 0.05, dir.getStepZ() * 0.25);
        ent.setDefaultPickUpDelay();
        world.addFreshEntity(ent);
    }

    private record SlotStack(int slot, ItemStack stack) {
        static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("Slot").forGetter(SlotStack::slot),
                ItemStack.CODEC.fieldOf("Stack").forGetter(SlotStack::stack)
        ).apply(inst, SlotStack::new));
    }

    private void updateComparators() {
        if (level != null && !level.isClientSide()) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public int getContainerSize() {
        return TOTAL;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < TOTAL; i++) {
            if (!items.get(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack cur = getItem(slot);
        if (cur.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken = cur.split(amount);
        if (cur.isEmpty()) items.set(slot, ItemStack.EMPTY);

        setChanged();
        syncSelf();
        updateComparators();
        return taken;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < TOTAL; i++) items.set(i, ItemStack.EMPTY);
        setChanged();
        syncSelf();
        updateComparators();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
