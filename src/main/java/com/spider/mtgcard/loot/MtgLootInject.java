package com.spider.mtgcard.loot;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.api.BoosterPackLootRegistry;
import com.spider.mtgcard.config.MtgcardConfig;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.resources.Identifier;

public final class MtgLootInject {

    private static final float PACK_CHANCE = 0.05f; // 5%

    public static void init() {
        if (MtgcardConfig.mtgGameEnabled()) BoosterPackLootRegistry.register(ModItems.MTG_PACK, PACK_CHANCE);
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Identifier id = key.identifier();

            // Includes vanilla, modded, datapack, and configured chest loot tables.
            if (!id.getPath().startsWith("chests/")) return;

            for (BoosterPackLootRegistry.Entry entry : BoosterPackLootRegistry.entries()) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .when(LootItemRandomChanceCondition.randomChance(entry.chance()))
                                .add(LootItem.lootTableItem(entry.item().get()))
                );
            }
        });
    }

    private MtgLootInject() {}
}
