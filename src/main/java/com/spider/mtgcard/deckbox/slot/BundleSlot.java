package com.spider.mtgcard.deckbox.slot;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;

public class BundleSlot extends Slot {
    public BundleSlot(Container inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.BUNDLE); }
}
