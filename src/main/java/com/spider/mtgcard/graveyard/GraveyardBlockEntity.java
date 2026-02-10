// com/spider/mtgcard/graveyard/GraveyardBlockEntity.java
package com.spider.mtgcard.graveyard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.registry.ModBlockEntities; // or ModBlocks.GRAVEYARD_BE if you kept it there
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class GraveyardBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, SidedInventory {

    private int viewers = 0;

    public void onViewerOpen() {
        if (world == null || world.isClient()) return;
        viewers++;
        if (viewers == 1) setOpen(true);
    }

    public void onViewerClose() {
        if (world == null || world.isClient()) return;
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0) setOpen(false);
    }

    private void setOpen(boolean open) {
        if (world == null) return;
        BlockState st = getCachedState();
        if (st.contains(GraveyardBlock.OPEN) && st.get(GraveyardBlock.OPEN) != open) {
            world.setBlockState(pos, st.with(GraveyardBlock.OPEN, open), Block.NOTIFY_ALL);
        }
    }

    public static final int SIDE_SIZE = 100; // 10x10
    public static final int TOTAL = 200;     // graveyard(100) + exile(100)

    private final DefaultedList<net.minecraft.item.ItemStack> items =
            DefaultedList.ofSize(TOTAL, net.minecraft.item.ItemStack.EMPTY);

    // ---- Hopper / automation (SidedInventory) ----

    private static final int[] GRAVEYARD_SLOTS = makeRange(0, SIDE_SIZE); // 0..99

    private static int[] makeRange(int start, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) out[i] = start + i;
        return out;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        // Hopper input goes ONLY to graveyard side (0..99)
        return GRAVEYARD_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot < 0 || slot >= TOTAL) return false;
        // only allow CARD items (both sides are “card-only”)
        return !stack.isEmpty() && stack.isOf(com.spider.mtgcard.item.ModItems.CARD);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // If you want to block hopper extraction completely, use:
        return false;
    }

    public GraveyardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVEYARD, pos, state); // <- match your registry location
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.mtgcard.graveyard");
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Override
    public @Nullable ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new GraveyardScreenHandler(
                syncId,
                inv,
                this.pos,
                ScreenHandlerContext.create(Objects.requireNonNull(world), this.pos)
        );
    }

    // ---- inventory access (ScreenHandler will use these) ----
    public DefaultedList<net.minecraft.item.ItemStack> getItems() { return items; }

    public net.minecraft.item.ItemStack getStack(int slot) { return items.get(slot); }
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        markDirty();
        syncSelf();
        updateComparators();
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack st = getStack(slot);
        if (st.isEmpty()) return ItemStack.EMPTY;
        items.set(slot, ItemStack.EMPTY);
        markDirty();
        syncSelf();
        updateComparators();
        return st;
    }

    private void syncSelf() {
        if (world == null || world.isClient()) return;
        world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    // ---- persistence (we’ll keep it simple for now) ----
    @Override
    protected void readData(ReadView view) {
        super.readData(view);

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
    protected void writeData(WriteView view) {
        super.writeData(view);

        var out = new java.util.ArrayList<SlotStack>();
        for (int i = 0; i < TOTAL; i++) {
            ItemStack st = items.get(i);
            if (!st.isEmpty()) out.add(new SlotStack(i, st));
        }
        view.put("Items", SlotStack.CODEC.listOf(), out);
    }

    // GraveyardBlockEntity.java (add inside class)

    private static final int GRID = 100; // 10x10
    private static final int GY_START = 0;
    private static final int EX_START = 100;

    public void exileAll() {
        if (world == null || world.isClient()) return;

        // Move all from graveyard -> exile
        moveRange(GY_START, EX_START);
    }

    public void returnAll() {
        if (world == null || world.isClient()) return;

        // Move all from exile -> graveyard
        moveRange(EX_START, GY_START);
    }

    private void moveRange(int fromStart, int toStart) {
        if (world == null) return;

        // 1) collect cards from "from" side in slot order
        java.util.ArrayList<net.minecraft.item.ItemStack> moving = new java.util.ArrayList<>();
        for (int i = 0; i < GRID; i++) {
            int fromSlot = fromStart + i;
            var st = items.get(fromSlot);
            if (!st.isEmpty()) {
                moving.add(st);
                items.set(fromSlot, net.minecraft.item.ItemStack.EMPTY);
            }
        }

        if (moving.isEmpty()) {
            markDirty();
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
            net.minecraft.util.math.Direction back = getBackDirection();
            for (; idx < moving.size(); idx++) {
                ejectStack(world, pos, back, moving.get(idx));
            }
        }

        markDirty();
        syncSelf();
        updateComparators();
    }

    private net.minecraft.util.math.Direction getBackDirection() {
        var state = getCachedState();
        // If your GraveyardBlock has FACING (you do), use it. Otherwise fallback.
        if (state != null && state.contains(com.spider.mtgcard.graveyard.GraveyardBlock.FACING)) {
            var facing = state.get(com.spider.mtgcard.graveyard.GraveyardBlock.FACING);
            return facing.getOpposite();
        }
        return net.minecraft.util.math.Direction.NORTH;
    }

    private static void ejectStack(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                   net.minecraft.util.math.Direction dir, net.minecraft.item.ItemStack stack) {
        double x = pos.getX() + 0.5 + dir.getOffsetX() * 0.6;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.6;

        var ent = new net.minecraft.entity.ItemEntity(world, x, y, z, stack);
        ent.setVelocity(dir.getOffsetX() * 0.25, 0.05, dir.getOffsetZ() * 0.25);
        ent.setToDefaultPickupDelay();
        world.spawnEntity(ent);
    }

    private record SlotStack(int slot, ItemStack stack) {
        static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("Slot").forGetter(SlotStack::slot),
                ItemStack.CODEC.fieldOf("Stack").forGetter(SlotStack::stack)
        ).apply(inst, SlotStack::new));
    }

    private void updateComparators() {
        if (world != null && !world.isClient()) {
            world.updateComparators(pos, getCachedState().getBlock());
        }
    }

    @Override
    public int size() {
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
    public ItemStack removeStack(int slot, int amount) {
        ItemStack cur = getStack(slot);
        if (cur.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken = cur.split(amount);
        if (cur.isEmpty()) items.set(slot, ItemStack.EMPTY);

        markDirty();
        syncSelf();
        updateComparators();
        return taken;
    }

    @Override
    public void clear() {
        for (int i = 0; i < TOTAL; i++) items.set(i, ItemStack.EMPTY);
        markDirty();
        syncSelf();
        updateComparators();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }
}
