// src/main/java/com/spider/mtgcard/cardstore/CardStoreScreenHandler.java
package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class CardStoreScreenHandler extends ScreenHandler {

    public final BlockPos blockPos;

    // Layout constants (must match what your Screen expects visually)
    public static final int MARGIN = 10;
    public static final int INV_BLOCK_H = (3 * 18) + 4 + 18; // 3 rows + gap + hotbar

    public CardStoreScreenHandler(int syncId, PlayerInventory playerInv, BlockPos blockPos) {
        super(ModScreenHandlers.CARD_STORE, syncId);
        this.blockPos = blockPos;

        // ✅ Put inventory at the bottom of a "virtual" fullscreen screen.
        // We can't know the client's exact pixel height server-side,
        // so choose a large virtual height and make the client screen match it.
        //
        // EASIEST: pick a fixed GUI height that matches 16:9-ish.
        // If you want true dynamic per-resolution later, we can sync height from client.
        int guiW = 426; // "virtual" width
        int guiH = 240; // "virtual" height (you can tweak)

        int invTopY = guiH - MARGIN - INV_BLOCK_H;
        int invX = MARGIN + 8;

        addPlayerInventory(playerInv, invX, invTopY);
        addHotbar(playerInv, invX, invTopY + (3 * 18) + 4);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private void addPlayerInventory(PlayerInventory inv, int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
    }

    private void addHotbar(PlayerInventory inv, int x, int y) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, x + col * 18, y));
        }
    }
}
