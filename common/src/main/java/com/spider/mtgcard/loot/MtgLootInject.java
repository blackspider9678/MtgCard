package com.spider.mtgcard.loot;

import com.spider.mtgcard.content.pack.PackLootRules;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;

public final class MtgLootInject {

    public static void init() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            for (LootPool.Builder pool : PackLootRules.poolsFor(key.identifier())) {
                tableBuilder.withPool(pool);
            }
        });
    }

    private MtgLootInject() {}
}
