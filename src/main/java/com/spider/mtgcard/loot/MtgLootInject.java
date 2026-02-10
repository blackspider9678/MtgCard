package com.spider.mtgcard.loot;

import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.util.Identifier;

public final class MtgLootInject {

    private static final float PACK_CHANCE = 0.01f; // 1%

    public static void init() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.getValue();

            // Only vanilla chest loot tables
            if (!"minecraft".equals(id.getNamespace())) return;
            if (!id.getPath().startsWith("chests/")) return;

            LootPool pool = LootPool.builder()
                    .conditionally(RandomChanceLootCondition.builder(PACK_CHANCE))
                    .with(ItemEntry.builder(ModItems.MTG_PACK))
                    .build();

            tableBuilder.pool(pool);
        });
    }

    private MtgLootInject() {}
}
