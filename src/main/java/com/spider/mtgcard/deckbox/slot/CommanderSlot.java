package com.spider.mtgcard.deckbox.slot;

import com.spider.mtgcard.cards.CardNbt;
import com.spider.mtgcard.item.CardItem;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

// slot/CommanderSlot.java
public class CommanderSlot extends Slot {
    public CommanderSlot(Inventory inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean canInsert(ItemStack stack) {
        if (!(stack.getItem() instanceof CardItem card)) return false;
        return CardNbt.isCommanderLegal(stack); // your rule (legendary creature or has “Commander” tag)
    }
}
