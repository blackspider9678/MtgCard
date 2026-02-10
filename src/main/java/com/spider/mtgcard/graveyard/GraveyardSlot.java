// com/spider/mtgcard/graveyard/GraveyardSlot.java
package com.spider.mtgcard.graveyard;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class GraveyardSlot extends Slot {

    public GraveyardSlot(GraveyardBlockEntity be, int index, int x, int y) {
        super(new GraveyardInventory(be), index, x, y);
    }

    // If you want only CARD items allowed, we can enforce here later.
}
