// TODO(Ravel): Failed to fully resolve file: null cannot be cast to non-null type com.intellij.psi.PsiJavaCodeReferenceElement
package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DeckboxBlockEntity extends RandomizableContainerBlockEntity {

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

    private static final Component NAME = Component.translatable("block.mtgcard.deckbox");

    private static final int[] AVAILABLE_SLOTS = java.util.stream.IntStream
            .range(0, INVENTORY_SIZE)
            .toArray();

    private NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);

    // tint (persisted)
    private int rgbTint = 0xFFFFFF;
    public int getRgbTint() { return rgbTint; }
    public void setRgbTint(int rgb) { this.rgbTint = rgb & 0xFFFFFF; sync(); }

    public DeckboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECKBOX, pos, state);
    }

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
        var st = getBlockState();
        if (st.hasProperty(DeckboxBlock.OPEN) && st.getValue(DeckboxBlock.OPEN) != open) {
            level.setBlock(worldPosition, st.setValue(DeckboxBlock.OPEN, open), Block.UPDATE_ALL);
        }
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    protected Component getDefaultName() {
        return NAME;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> stacks) {
        this.inventory = stacks;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    public ContainerData createProperties() {
        return new ContainerData() {
            @Override public int getCount() { return 1; }

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
            if (!getItem(i).isEmpty()) used++;
        }

        if (used <= 0) return 0;

        // Map used slots to 1..15 similar to vanilla containers
        int level = 1 + (used * 14) / FIRST_SIDE_SLOT; // FIRST_SIDE_SLOT == 99
        if (level > 15) level = 15;
        return level;
    }

    /* ---------------- Save / Load ---------------- */

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        rgbTint = view.getIntOr("RgbTint", 0xFFFFFF);

        inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
        if (!tryLoadLootTable(view)) {
            ContainerHelper.loadAllItems(view, inventory);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        view.putInt("RgbTint", rgbTint);

        if (!trySaveLootTable(view)) {
            ContainerHelper.saveAllItems(view, inventory, false);
        }
    }

    /* ---------------- Screen handler ---------------- */

    @Override
    protected AbstractContainerMenu createMenu(int syncId, Inventory playerInventory) {
        return new DeckboxScreenHandler(syncId, playerInventory, this, createProperties());
    }

    @Override
    public Component getDisplayName() {
        return NAME;
    }

    /* ---------------- Networking / Sync ---------------- */

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveWithoutMetadata(lookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void clearForDropNoSync() {
        // Clear without triggering sync/comparator updates (we're being removed anyway)
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    public void sync() {
        if (level == null) return;

        setChanged();

        if (!level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); // ✅ comparator updates

            // ✅ Notify adjacent Deck Control blocks that the deck changed
            for (Direction d : Direction.values()) {
                var be = level.getBlockEntity(worldPosition.relative(d));
                if (be instanceof DeckControlBlockEntity dc) {
                    dc.onNeighborDeckboxChanged(worldPosition);
                }
            }
        }
    }
}
