package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.ShulkerBoxSlot;
import net.minecraft.screen.slot.Slot;

import static com.spider.mtgcard.deckbox.DeckboxBlockEntity.*;

public class DeckboxScreenHandler extends ScreenHandler {
    private static final int INVENTORY_SIZE = DeckboxBlockEntity.INVENTORY_SIZE; // 102
    private final Inventory inventory;

    // ---- slot index helpers (container side) ----
    private static final int GRID_START = 0;
    private static final int GRID_END_EXCL =
            DeckboxBlockEntity.DECKBOX_ROWS * DeckboxBlockEntity.DECKBOX_COLS; // 99

    // the top side slot (bundle-only)
    private static final int BUNDLE_SLOT = DeckboxBlockEntity.FIRST_SIDE_SLOT; // 99

    // the two card-only side slots as a half-open range [100, 102)
    private static final int SIDE_CARD_START = DeckboxBlockEntity.SECOND_SIDE_SLOT;      // 100
    private static final int SIDE_CARD_END_EXCL = DeckboxBlockEntity.THIRD_SIDE_SLOT+1;  // 102

    private final PropertyDelegate props;

    public int getRgbTint() {
        return this.props.get(0);
    }


    public DeckboxScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(INVENTORY_SIZE), new ArrayPropertyDelegate(1));
    }

    public DeckboxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        this(syncId, playerInventory, inventory, new ArrayPropertyDelegate(1));
    }

    public DeckboxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate props) {
        super(ModScreenHandlers.DECKBOX, syncId);
        checkSize(inventory, INVENTORY_SIZE);
        this.inventory = inventory;

        this.props = props;
        this.addProperties(props);

        inventory.onOpen(playerInventory.player);

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

    private void addPlayerSlots(PlayerInventory playerInv, int left, int top) {
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
        return stack.getItem() == net.minecraft.item.Items.BUNDLE;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot clicked = this.slots.get(slotIndex);
        if (clicked == null || !clicked.hasStack()) return ItemStack.EMPTY;

        ItemStack stack = clicked.getStack();
        result = stack.copy();

        int containerSlots = this.inventory.size();         // 102
        int playerStart = containerSlots;                    // first player slot
        int playerEndExcl = this.slots.size();               // after hotbar

        boolean isFromContainer = slotIndex < containerSlots;

        if (isFromContainer) {
            // container -> player
            if (!this.insertItem(stack, playerStart, playerEndExcl, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player -> container
            if (isBundle(stack)) {
                if (!this.insertItem(stack, BUNDLE_SLOT, BUNDLE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (isCard(stack)) {
                // grid then the two card-only side slots
                if (!this.insertItem(stack, GRID_START, GRID_END_EXCL, false) &&
                        !this.insertItem(stack, SECOND_SIDE_SLOT, THIRD_SIDE_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // not allowed
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) clicked.setStack(ItemStack.EMPTY);
        else clicked.markDirty();

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
        public CardOnlySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean canInsert(ItemStack stack) { return isCard(stack); }
        @Override public int getMaxItemCount() { return 64; } // or whatever your card stacks use
    }

    private static class BundleOnlySlot extends Slot {
        public BundleOnlySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean canInsert(ItemStack stack) { return isBundle(stack); }
        @Override public int getMaxItemCount() { return 1; }
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);

        if (this.inventory instanceof DeckboxBlockEntity dbe) {
            dbe.onViewerClose();
        }
    }
}
