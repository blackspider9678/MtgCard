package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;

import static com.spider.mtgcard.deckbox.DeckboxBlockEntity.*;

public class DeckboxScreenHandler extends AbstractContainerMenu {
    private static final int INVENTORY_SIZE = DeckboxBlockEntity.INVENTORY_SIZE; // 102
    private final Container inventory;

    // ---- slot index helpers (container side) ----
    private static final int GRID_START = 0;
    private static final int GRID_END_EXCL =
            DeckboxBlockEntity.DECKBOX_ROWS * DeckboxBlockEntity.DECKBOX_COLS; // 99

    // the top side slot (bundle-only)
    private static final int BUNDLE_SLOT = DeckboxBlockEntity.FIRST_SIDE_SLOT; // 99

    // the two card-only side slots as a half-open range [100, 102)
    private static final int SIDE_CARD_START = DeckboxBlockEntity.SECOND_SIDE_SLOT;      // 100
    private static final int SIDE_CARD_END_EXCL = DeckboxBlockEntity.THIRD_SIDE_SLOT+1;  // 102

    private final ContainerData props;

    public int getRgbTint() {
        return this.props.get(0);
    }


    public DeckboxScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, new SimpleContainer(INVENTORY_SIZE), new SimpleContainerData(1));
    }

    public DeckboxScreenHandler(int syncId, Inventory playerInventory, Container inventory) {
        this(syncId, playerInventory, inventory, new SimpleContainerData(1));
    }

    public DeckboxScreenHandler(int syncId, Inventory playerInventory, Container inventory, ContainerData props) {
        super(ModScreenHandlers.DECKBOX, syncId);
        checkContainerSize(inventory, INVENTORY_SIZE);
        this.inventory = inventory;

        this.props = props;
        this.addDataSlots(props);

        inventory.startOpen(playerInventory.player);

        if (inventory instanceof DeckboxBlockEntity dbe) {
            dbe.onViewerOpen();
        }

        // DeckboxScreenHandler.java (inside constructor)

        final int GRID_X = 16;
        final int GRID_Y = 22;

        final int SIDE_X = 181;
        final int SIDE_Y = 22;

        final int PLAYER_X = 26;
        final int PLAYER_Y = 226;

        // Grid: 11 x 9 (CARD-ONLY)
        for (int row = 0; row < DECKBOX_ROWS; ++row) {
            for (int col = 0; col < DECKBOX_COLS; ++col) {
                int index = col + row * DECKBOX_COLS; // 0..98
                this.addSlot(new CardOnlySlot(inventory, index, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Side column (3 slots)
        this.addSlot(new BundleOnlySlot(inventory, FIRST_SIDE_SLOT,  SIDE_X, SIDE_Y + 0 * 18));
        this.addSlot(new CardOnlySlot  (inventory, SECOND_SIDE_SLOT, SIDE_X, SIDE_Y + 1 * 18)); // Commander
        this.addSlot(new CardOnlySlot  (inventory, THIRD_SIDE_SLOT,  SIDE_X, SIDE_Y + 2 * 18)); // Partner

        // Player inventory
        addPlayerSlots(playerInventory, PLAYER_X, PLAYER_Y);
    }

    private void addPlayerSlots(Inventory playerInv, int left, int top) {
        // main inventory (3 rows)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        // hotbar
        int hotbarTop = top + 58;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, left + col * 18, hotbarTop));
        }
    }

    private static boolean isBundle(ItemStack stack) {
        return stack.getItem() == net.minecraft.world.item.Items.BUNDLE;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.inventory.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot clicked = this.slots.get(slotIndex);
        if (clicked == null || !clicked.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = clicked.getItem();
        result = stack.copy();

        int containerSlots = this.inventory.getContainerSize();         // 102
        int playerStart = containerSlots;                    // first player slot
        int playerEndExcl = this.slots.size();               // after hotbar

        boolean isFromContainer = slotIndex < containerSlots;

        if (isFromContainer) {
            // container -> player
            if (!this.moveItemStackTo(stack, playerStart, playerEndExcl, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player -> container
            if (isBundle(stack)) {
                if (!this.moveItemStackTo(stack, BUNDLE_SLOT, BUNDLE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (isCard(stack)) {
                // grid then the two card-only side slots
                if (!this.moveItemStackTo(stack, GRID_START, GRID_END_EXCL, false) &&
                        !this.moveItemStackTo(stack, SECOND_SIDE_SLOT, THIRD_SIDE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // not allowed
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) clicked.setByPlayer(ItemStack.EMPTY);
        else clicked.setChanged();

        return result;
    }

    private static boolean isCard(ItemStack stack) {
        // Strict equality against your single Card item
        return stack.getItem() == ModItems.CARD;
        // If you don't have ModItems.CARD, replace with your reference, e.g.:
        // return stack.getItem() == com.spider.mtgcard.items.CardItem.INSTANCE;
        // or, if you prefer class-based:
        // return stack.getItem() instanceof com.spider.mtgcard.items.CardItem;
    }

    private static class CardOnlySlot extends Slot {
        public CardOnlySlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return isCard(stack); }
        @Override public int getMaxStackSize() { return 64; } // or whatever your card stacks use
    }

    private static class BundleOnlySlot extends Slot {
        public BundleOnlySlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean mayPlace(ItemStack stack) { return isBundle(stack); }
        @Override public int getMaxStackSize() { return 1; }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.inventory.stopOpen(player);

        if (this.inventory instanceof DeckboxBlockEntity dbe) {
            dbe.onViewerClose();
        }
    }
}
