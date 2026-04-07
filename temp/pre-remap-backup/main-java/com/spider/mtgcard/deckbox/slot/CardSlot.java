package com.spider.mtgcard.deckbox.slot;

import com.spider.mtgcard.item.CardItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

public class CardSlot extends Slot {
    public CardSlot(Container inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof CardItem; }
}
