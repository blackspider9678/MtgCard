package com.spider.mtgcard.trade;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

public final class ModTrades {
    private static final Identifier WANDERING_TRADER_DICE_POOL =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "wandering_trader/dice");

    public static void init() {
        TradeOfferHelper.registerWanderingTraderOffers(builder ->
                builder.pool(
                        WANDERING_TRADER_DICE_POOL,
                        2,
                        diceOffer(ModItems.D4_DICE, 3, 1, 12),
                        diceOffer(ModItems.D6_DICE, 3, 1, 12),
                        diceOffer(ModItems.D8_DICE, 4, 1, 12),
                        diceOffer(ModItems.D10_DICE, 4, 1, 12),
                        diceOffer(ModItems.D12_DICE, 5, 1, 12),
                        diceOffer(ModItems.D20_DICE, 8, 1, 12),
                        diceOffer(ModItems.D100_DICE, 16, 1, 6)
                )
        );
    }

    private static VillagerTrades.ItemListing diceOffer(Item item, int emeraldCost, int itemCount, int maxUses) {
        return (level, entity, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, emeraldCost),
                new ItemStack(item, itemCount),
                maxUses,
                0,
                0.0F
        );
    }

    private ModTrades() {}
}
