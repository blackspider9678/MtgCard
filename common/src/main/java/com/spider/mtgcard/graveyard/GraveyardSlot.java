// com/spider/mtgcard/graveyard/GraveyardSlot.java
package com.spider.mtgcard.graveyard;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

public class GraveyardSlot extends Slot {

    public GraveyardSlot(GraveyardBlockEntity be, int index, int x, int y) {
        super(new GraveyardInventory(be), index, x, y);
    }

    // If you want only CARD items allowed, we can enforce here later.
}
