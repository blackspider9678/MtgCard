// src/main/java/com/spider/mtgcard/util/ModDispenserBehaviors.java
package com.spider.mtgcard.util;

import com.spider.mtgcard.deckbox.DeckboxDispenserBehavior;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.BlockPlacementDispenserBehavior;

public final class ModDispenserBehaviors {
    private ModDispenserBehaviors() {}

    public static void init() {
        // Places the deckbox block instead of dropping the item
        DispenserBlock.registerBehavior(
                ModBlocks.DECKBOX_ITEM,
                new DeckboxDispenserBehavior()
        );
    }
}
