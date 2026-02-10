package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DeckboxBlockEntity extends LootableContainerBlockEntity {

    // 11 x 9 main grid = 99 slots (0..98)
    public static final int DECKBOX_ROWS = 11;
    public static final int DECKBOX_COLS = 9;
    public static final int MAIN_SLOTS = DECKBOX_ROWS * DECKBOX_COLS; // 99

    // Side slots (99..101)
    public static final int SIDE_SLOTS = 3;

    // Total = 102 slots (0..101)
    public static final int INVENTORY_SIZE = MAIN_SLOTS + SIDE_SLOTS; // 102

    public static final int FIRST_SIDE_SLOT  = MAIN_SLOTS;     // 99
    public static final int SECOND_SIDE_SLOT = MAIN_SLOTS + 1; // 100
    public static final int THIRD_SIDE_SLOT  = MAIN_SLOTS + 2; // 101

    private static final Text NAME = Text.translatable("block.mtgcard.deckbox");

    private static final int[] AVAILABLE_SLOTS = java.util.stream.IntStream
            .range(0, INVENTORY_SIZE)
            .toArray();

    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

    // tint (persisted)
    private int rgbTint = 0xFFFFFF;
    public int getRgbTint() { return rgbTint; }
    public void setRgbTint(int rgb) { this.rgbTint = rgb & 0xFFFFFF; sync(); }

    public DeckboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECKBOX, pos, state);
    }

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
        var st = getCachedState();
        if (st.contains(DeckboxBlock.OPEN) && st.get(DeckboxBlock.OPEN) != open) {
            world.setBlockState(pos, st.with(DeckboxBlock.OPEN, open), Block.NOTIFY_ALL);
        }
    }

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    protected Text getContainerName() {
        return NAME;
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return inventory;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> stacks) {
        this.inventory = stacks;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null
                && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public PropertyDelegate createProperties() {
        return new PropertyDelegate() {
            @Override public int size() { return 1; }

            @Override
            public int get(int index) {
                return (index == 0) ? rgbTint : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    rgbTint = value & 0xFFFFFF;
                }
            }
        };
    }

    public int[] getAvailableSlots(Direction side) {
        return AVAILABLE_SLOTS;
    }

    /**
     * Comparator wants 0..15.
     * You requested “slots 0-98”, so ONLY count the main grid.
     */
    public int getComparatorLevelMainOnly() {
        int used = 0;
        for (int i = 0; i < FIRST_SIDE_SLOT; i++) { // 0..98
            if (!getStack(i).isEmpty()) used++;
        }

        if (used <= 0) return 0;

        // Map used slots to 1..15 similar to vanilla containers
        int level = 1 + (used * 14) / FIRST_SIDE_SLOT; // FIRST_SIDE_SLOT == 99
        if (level > 15) level = 15;
        return level;
    }

    /* ---------------- Save / Load ---------------- */

    @Override
    protected void readData(ReadView view) {
        super.readData(view);

        rgbTint = view.getInt("RgbTint", 0xFFFFFF);

        inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        if (!readLootTable(view)) {
            Inventories.readData(view, inventory);
        }
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        view.putInt("RgbTint", rgbTint);

        if (!writeLootTable(view)) {
            Inventories.writeData(view, inventory, false);
        }
    }

    /* ---------------- Screen handler ---------------- */

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new DeckboxScreenHandler(syncId, playerInventory, this, createProperties());
    }

    @Override
    public Text getDisplayName() {
        return NAME;
    }

    /* ---------------- Networking / Sync ---------------- */

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup) {
        return createNbt(lookup);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    public void clearForDropNoSync() {
        // Clear without triggering sync/comparator updates (we're being removed anyway)
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, ItemStack.EMPTY);
        }
        markDirty();
    }

    public void sync() {
        if (world == null) return;

        markDirty();

        if (!world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
            world.updateComparators(pos, getCachedState().getBlock()); // ✅ comparator updates

            // ✅ Notify adjacent Deck Control blocks that the deck changed
            for (Direction d : Direction.values()) {
                var be = world.getBlockEntity(pos.offset(d));
                if (be instanceof DeckControlBlockEntity dc) {
                    dc.onNeighborDeckboxChanged(pos);
                }
            }
        }
    }
}
