package com.spider.mtgcard.deckbox.slot;

import com.spider.mtgcard.cards.CardNbt;
import com.spider.mtgcard.item.ModItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

// slot/CommanderSlot.java
public class CommanderSlot extends Slot {
    public CommanderSlot(Container inv, int index, int x, int y) { super(inv, index, x, y); }
    @Override public boolean mayPlace(ItemStack stack) {
        if (!stack.is(ModItemTags.TCG_CARD)) return false;
        return CardNbt.isCommanderLegal(stack); // your rule (legendary creature or has “Commander” tag)
    }
}
