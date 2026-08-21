package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.api.BoosterPackLootRegistry;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.List;

public final class PackLootRules {
    public static final float PACK_CHANCE = 0.05f;

    private static final String MINECRAFT = "minecraft";
    private static final String CHESTS = "chests/";
    private static final String TRIAL_CHAMBER_REWARD_PREFIX = "chests/trial_chambers/reward";

    private static final Identifier REGULAR_VAULT = Identifier.fromNamespaceAndPath(MINECRAFT, "chests/trial_chambers/reward");
    private static final Identifier OMINOUS_VAULT = Identifier.fromNamespaceAndPath(MINECRAFT, "chests/trial_chambers/reward_ominous");
    private static final Identifier REGULAR_TRIAL_SPAWNER = Identifier.fromNamespaceAndPath(MINECRAFT, "spawners/trial_chamber/consumables");
    private static final Identifier OMINOUS_TRIAL_SPAWNER = Identifier.fromNamespaceAndPath(MINECRAFT, "spawners/ominous/trial_chamber/consumables");
    private static final Identifier FISHING = Identifier.fromNamespaceAndPath(MINECRAFT, "gameplay/fishing");

    public static List<LootPool.Builder> poolsFor(Identifier id) {
        if (MtgcardConfig.mtgGameEnabled()) BoosterPackLootRegistry.register(ModItems.MTG_PACK, PACK_CHANCE);
        if (!MINECRAFT.equals(id.getNamespace())) {
            return List.of();
        }

        if (OMINOUS_VAULT.equals(id)) {
            return packPools(2.0F, 3.0F);
        }

        if (REGULAR_VAULT.equals(id) || REGULAR_TRIAL_SPAWNER.equals(id) || OMINOUS_TRIAL_SPAWNER.equals(id)) {
            return packPools(1.0F, 1.0F);
        }

        if (FISHING.equals(id)) {
            return MtgcardConfig.fishingPackLootEnabled() ? packPools(1.0F, 1.0F) : List.of();
        }

        String path = id.getPath();
        if (path.startsWith(TRIAL_CHAMBER_REWARD_PREFIX)) {
            return List.of();
        }

        if (path.startsWith(CHESTS)) {
            return packPools(1.0F, 1.0F);
        }

        return List.of();
    }

    private static List<LootPool.Builder> packPools(float minCount, float maxCount) {
        return BoosterPackLootRegistry.entries().stream().map(entry -> packPool(entry, minCount, maxCount)).toList();
    }

    private static LootPool.Builder packPool(BoosterPackLootRegistry.Entry entry, float minCount, float maxCount) {
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(entry.chance()));

        if (minCount == 1.0F && maxCount == 1.0F) {
            return pool.add(LootItem.lootTableItem(entry.item().get()));
        }

        return pool.add(LootItem.lootTableItem(entry.item().get())
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(minCount, maxCount))));
    }

    private PackLootRules() {}
}
