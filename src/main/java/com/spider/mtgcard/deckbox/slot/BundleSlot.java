package com.spider.mtgcard.deckbox.slot;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

public class BundleSlot extends Slot {
    public BundleSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean canInsert(ItemStack stack) { return stack.isOf(Items.BUNDLE); }
}
