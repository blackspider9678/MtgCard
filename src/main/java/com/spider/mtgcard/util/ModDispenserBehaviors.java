// src/main/java/com/spider/mtgcard/util/ModDispenserBehaviors.java
package com.spider.mtgcard.util;

import com.spider.mtgcard.deckbox.DeckboxDispenserBehavior;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.core.dispenser.ShulkerBoxDispenseBehavior;

public final class ModDispenserBehaviors {
    private ModDispenserBehaviors() {}

    public static void init() {
        // Places the deckbox block instead of dropping the item
        for (var deckboxItem : ModBlocks.getDeckboxItems()) {
            DispenserBlock.registerBehavior(deckboxItem, new DeckboxDispenserBehavior());
        }
    }
}
