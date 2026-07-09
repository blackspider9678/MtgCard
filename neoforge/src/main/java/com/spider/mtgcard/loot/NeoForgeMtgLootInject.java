package com.spider.mtgcard.loot;

import com.spider.mtgcard.content.pack.PackLootRules;
import net.minecraft.world.level.storage.loot.LootPool;
import net.neoforged.neoforge.event.LootTableLoadEvent;

public final class NeoForgeMtgLootInject {
    public static void onLootTableLoad(LootTableLoadEvent event) {
        for (LootPool.Builder pool : PackLootRules.poolsFor(event.getName())) {
            event.getTable().addPool(pool.build());
        }
    }

    private NeoForgeMtgLootInject() {}
}
