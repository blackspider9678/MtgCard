package com.spider.mtgcard.loot;

import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.resources.Identifier;

public final class MtgLootInject {

    private static final float PACK_CHANCE = 0.01f; // 1%

    public static void init() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.identifier();

            // Only vanilla chest loot tables
            if (!"minecraft".equals(id.getNamespace())) return;
            if (!id.getPath().startsWith("chests/")) return;

            LootPool pool = LootPool.lootPool()
                    .when(LootItemRandomChanceCondition.randomChance(PACK_CHANCE))
                    .add(LootItem.lootTableItem(ModItems.MTG_PACK))
                    .build();

            tableBuilder.pool(pool);
        });
    }

    private MtgLootInject() {}
}
