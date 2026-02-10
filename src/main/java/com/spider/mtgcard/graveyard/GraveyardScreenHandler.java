// com/spider/mtgcard/graveyard/GraveyardScreenHandler.java
package com.spider.mtgcard.graveyard;

import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class GraveyardScreenHandler extends ScreenHandler {

    public static final int SIDE_SIZE = 100;   // 10x10
    public static final int TOTAL = 200;       // graveyard(100) + exile(100)

    public final BlockPos pos;
    private final ScreenHandlerContext context;
    private final Inventory blockInv;

    // ---- layout constants (match your GraveyardScreen background math) ----
    public static final Identifier TEX = Identifier.of("mtgcard", "textures/gui/graveyard.png");
    private static final int GRID = 10;
    private static final int SLOT = 18;

    private static final int TOTAL_W = 8 + (10 * 18) + 18 + (10 * 18) + 8; // 394

    private static final int LEFT_PAD = 12;
    private static final int TOP_PAD  = 38;

    private static final int GRAVE_X = LEFT_PAD;
    private static final int EXILE_X = 204;
    private static final int GRID_Y  = TOP_PAD;

    private static final int PLAYER_INV_W = 9 * 18; // 162
    private static final int PLAYER_INV_X = 1 + (TOTAL_W - PLAYER_INV_W) / 2; // centered
    private static final int PLAYER_INV_Y = GRID_Y + (10 * 18) + 8;      // under grids

    /** Client-side constructor (pos comes from opening data). */
    public GraveyardScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos) {
        this(syncId, playerInv, pos, ScreenHandlerContext.EMPTY);
    }

    /** Server-side constructor. */
    public GraveyardScreenHandler(int syncId, PlayerInventory playerInv, BlockPos pos, ScreenHandlerContext context) {
        super(ModScreenHandlers.GRAVEYARD, syncId);
        this.pos = pos;
        this.context = context;

        // Resolve the real BE inventory on the server; on the client this falls back
        this.blockInv = context.get((world, bp) -> {
            var be = world.getBlockEntity(bp);
            if (be instanceof GraveyardBlockEntity gbe) return new BEInventory(gbe);
            return new SimpleInventory(TOTAL);
        }, new SimpleInventory(TOTAL));

        checkSize(this.blockInv, TOTAL);
        this.blockInv.onOpen(playerInv.player);

        // ✅ Open animation while GUI is open (server only)
        if (!playerInv.player.getEntityWorld().isClient()) {
            context.run((world, bp) -> {
                var be = world.getBlockEntity(bp);
                if (be instanceof GraveyardBlockEntity gbe) gbe.onViewerOpen();
            });
        }

        // ---- Graveyard 10x10 (0..99) ----
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int slot = row * GRID + col; // 0..99
                int x = GRAVE_X + col * SLOT;
                int y = GRID_Y + row * SLOT;
                this.addSlot(new CardOnlySlot(blockInv, slot, x, y));
            }
        }

        // ---- Exile 10x10 (100..199) ----
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                int slot = SIDE_SIZE + (row * GRID + col); // 100..199
                int x = EXILE_X + col * SLOT;
                int y = GRID_Y + row * SLOT;
                this.addSlot(new CardOnlySlot(blockInv, slot, x, y));
            }
        }

        // ---- Player inventory (3 rows) ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = col + row * 9 + 9;
                int x = PLAYER_INV_X + col * 18;
                int y = PLAYER_INV_Y + row * 18;
                this.addSlot(new Slot(playerInv, idx, x, y));
            }
        }

        // ---- Hotbar ----
        int hotbarY = PLAYER_INV_Y + 58; // (3*18)+4
        for (int col = 0; col < 9; col++) {
            int x = PLAYER_INV_X + col * 18;
            this.addSlot(new Slot(playerInv, col, x, hotbarY));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return context.get((world, bp) -> {
            if (!world.getBlockState(bp).isOf(ModBlocks.GRAVEYARD)) return false;

            double cx = bp.getX() + 0.5;
            double cy = bp.getY() + 0.5;
            double cz = bp.getZ() + 0.5;

            double dx = player.getX() - cx;
            double dy = player.getY() - cy;
            double dz = player.getZ() - cz;

            return (dx * dx + dy * dy + dz * dz) <= 64.0; // 8 blocks
        }, true);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack original = slot.getStack();
        ItemStack copy = original.copy();

        int blockSlots = TOTAL;                 // 200
        int playerStart = blockSlots;
        int playerEnd = playerStart + 36;

        if (slotIndex < blockSlots) {
            if (!this.insertItem(original, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!this.insertItem(original, 0, SIDE_SIZE, false)) {
                if (!this.insertItem(original, SIDE_SIZE, TOTAL, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();

        return copy;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.blockInv.onClose(player);

        // ✅ Close animation when last viewer closes (server only)
        if (!player.getEntityWorld().isClient()) {
            context.run((world, bp) -> {
                var be = world.getBlockEntity(bp);
                if (be instanceof GraveyardBlockEntity gbe) gbe.onViewerClose();
            });
        }
    }

    /** Only allow your card item in these grids (prevents junk filling grave/exile). */
    private static final class CardOnlySlot extends Slot {
        public CardOnlySlot(Inventory inv, int index, int x, int y) {
            super(inv, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack != null && stack.isOf(com.spider.mtgcard.item.ModItems.CARD);
        }
    }

    /** Inventory view backed by the GraveyardBlockEntity list. */
    private static final class BEInventory implements Inventory {
        private final GraveyardBlockEntity be;
        private BEInventory(GraveyardBlockEntity be) { this.be = be; }

        @Override public int size() { return TOTAL; }

        @Override public boolean isEmpty() {
            for (int i = 0; i < TOTAL; i++) if (!getStack(i).isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getStack(int slot) { return be.getStack(slot); }

        @Override public ItemStack removeStack(int slot, int amount) {
            ItemStack cur = getStack(slot);
            if (cur.isEmpty()) return ItemStack.EMPTY;

            ItemStack taken = cur.split(amount);
            if (cur.isEmpty()) be.setStack(slot, ItemStack.EMPTY);
            else be.setStack(slot, cur);
            return taken;
        }

        @Override public ItemStack removeStack(int slot) { return be.removeStack(slot); }

        @Override public void setStack(int slot, ItemStack stack) { be.setStack(slot, stack); }

        @Override public void markDirty() { be.markDirty(); }

        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }

        @Override public void clear() {
            for (int i = 0; i < TOTAL; i++) setStack(i, ItemStack.EMPTY);
        }
    }
}
