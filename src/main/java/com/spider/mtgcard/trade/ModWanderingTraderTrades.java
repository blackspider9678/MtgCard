// src/main/java/com/spider/mtgcard/trade/ModWanderingTraderTrades.java
package com.spider.mtgcard.trade;

import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;

import java.util.Optional;

public final class ModWanderingTraderTrades {

    // Vanilla pool id
    private static final Identifier REGULAR_POOL = Identifier.ofVanilla("wandering_trader");

    public static void init() {
        TradeOfferHelper.registerWanderingTraderOffers(builder -> {
            builder.addAll(
                    REGULAR_POOL,

                    // Dice offers (emeralds -> dice)
                    sellForEmeralds(ModItems.D4_DICE,   3, 1, 12),
                    sellForEmeralds(ModItems.D6_DICE,   3, 1, 12),
                    sellForEmeralds(ModItems.D8_DICE,   4, 1, 12),
                    sellForEmeralds(ModItems.D10_DICE,  4, 1, 12),
                    sellForEmeralds(ModItems.D12_DICE,  5, 1, 12),
                    sellForEmeralds(ModItems.D20_DICE,  8, 1, 12),
                    sellForEmeralds(ModItems.D100_DICE, 16, 1, 6)

                    // If you want an additional “themed” cost, swap to the 2-cost helper below.
                    // Example: emeralds + bundle -> d20
                    // sellForEmeraldsAndItem(ModItems.D20_DICE, 6, Items.BUNDLE, 1, 1, 8)
            );
        });
    }

    /**
     * Sells `sellItem` for `emeraldCost` emeralds.
     * @param sellCount how many dice per trade (usually 1)
     * @param maxUses how many times the offer can be used
     */
    private static TradeOffers.Factory sellForEmeralds(net.minecraft.item.Item sellItem,
                                                       int emeraldCost,
                                                       int sellCount,
                                                       int maxUses) {
        return (ServerWorld world, Entity entity, Random random) -> {
            TradedItem buy = new TradedItem(Items.EMERALD, emeraldCost);
            ItemStack sell = new ItemStack(sellItem, sellCount);
            return new TradeOffer(buy, sell, maxUses, 0, 0.0f);
        };
    }

    /**
     * Optional: Sells `sellItem` for emeralds + another item (e.g., bundle, amethyst shard).
     * Use this if you want dice to feel more “premium” without crafting.
     */
    @SuppressWarnings("unused")
    private static TradeOffers.Factory sellForEmeraldsAndItem(net.minecraft.item.Item sellItem,
                                                              int emeraldCost,
                                                              net.minecraft.item.Item extraCostItem,
                                                              int extraCostCount,
                                                              int sellCount,
                                                              int maxUses) {
        return (ServerWorld world, Entity entity, Random random) -> {
            TradedItem buy1 = new TradedItem(Items.EMERALD, emeraldCost);
            TradedItem buy2 = new TradedItem(extraCostItem, extraCostCount);
            ItemStack sell = new ItemStack(sellItem, sellCount);

            return new TradeOffer(
                    buy1,
                    Optional.of(buy2), // ✅ REQUIRED in 1.21+
                    sell,
                    maxUses,
                    0,
                    0.0f
            );
        };
    }


    private ModWanderingTraderTrades() {}
}