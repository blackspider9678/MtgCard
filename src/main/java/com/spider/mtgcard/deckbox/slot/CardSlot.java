package com.spider.mtgcard.deckbox.slot;

import com.spider.mtgcard.item.CardItem;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class CardSlot extends Slot {
    public CardSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean canInsert(ItemStack stack) { return stack.getItem() instanceof CardItem; }
}
