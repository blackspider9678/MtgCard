package com.spider.mtgcard.trade;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.dice.DiceGradientType;
import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Optional;

public final class ModTrades {
    private static final Identifier WANDERING_TRADER_DICE_POOL =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "wandering_trader/dice");
    private static final Identifier WANDERING_TRADER_PACK_POOL =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "wandering_trader/booster_pack");
    private static final DiceGradientType[] TRADER_GRADIENTS = {
            DiceGradientType.VERTICAL,
            DiceGradientType.HORIZONTAL,
            DiceGradientType.DIAGONAL,
            DiceGradientType.RADIAL
    };
    private static final int[] TRADER_TEXT_COLORS = {
            0xFFD700,
            0xFFFFFF,
            0x000000
    };
    private static final Identifier[] TRADER_BANNER_PATTERNS = {
            Identifier.withDefaultNamespace("stripe_bottom"),
            Identifier.withDefaultNamespace("stripe_top"),
            Identifier.withDefaultNamespace("stripe_center"),
            Identifier.withDefaultNamespace("stripe_middle"),
            Identifier.withDefaultNamespace("cross"),
            Identifier.withDefaultNamespace("straight_cross"),
            Identifier.withDefaultNamespace("diagonal_left"),
            Identifier.withDefaultNamespace("diagonal_right"),
            Identifier.withDefaultNamespace("rhombus"),
            Identifier.withDefaultNamespace("circle"),
            Identifier.withDefaultNamespace("gradient"),
            Identifier.withDefaultNamespace("gradient_up"),
            Identifier.withDefaultNamespace("bricks"),
            Identifier.withDefaultNamespace("border"),
            Identifier.withDefaultNamespace("curly_border"),
            Identifier.withDefaultNamespace("flower"),
            Identifier.withDefaultNamespace("creeper"),
            Identifier.withDefaultNamespace("globe"),
            Identifier.withDefaultNamespace("piglin"),
            Identifier.withDefaultNamespace("flow"),
            Identifier.withDefaultNamespace("guster")
    };

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
                ).pool(
                        WANDERING_TRADER_PACK_POOL,
                        1,
                        boosterPackOffer(3, 5, 1, 6)
                )
        );
    }

    private static VillagerTrades.ItemListing diceOffer(Item item, int emeraldCost, int itemCount, int maxUses) {
        return (level, entity, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, emeraldCost),
                randomDiceStack(item, itemCount, random),
                maxUses,
                0,
                0.0F
        );
    }

    private static ItemStack randomDiceStack(Item item, int count, RandomSource random) {
        boolean foil = random.nextInt(4) == 0;
        Optional<Identifier> bannerPattern = random.nextInt(3) == 0
                ? Optional.empty()
                : Optional.of(TRADER_BANNER_PATTERNS[random.nextInt(TRADER_BANNER_PATTERNS.length)]);

        DiceAppearance appearance = new DiceAppearance(
                randomColor(random),
                randomColor(random),
                randomColor(random),
                TRADER_TEXT_COLORS[random.nextInt(TRADER_TEXT_COLORS.length)],
                randomColor(random),
                TRADER_GRADIENTS[random.nextInt(TRADER_GRADIENTS.length)],
                false,
                foil,
                0L,
                bannerPattern
        );

        ItemStack stack = new ItemStack(item, count);
        stack.set(ModDataComponents.DICE_APPEARANCE, appearance);
        if (foil) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    private static int randomColor(RandomSource random) {
        return random.nextInt(0x1000000);
    }

    private static VillagerTrades.ItemListing boosterPackOffer(int minEmeraldCost, int maxEmeraldCost, int itemCount, int maxUses) {
        return (level, entity, random) -> {
            int emeraldCost = minEmeraldCost;
            if (maxEmeraldCost > minEmeraldCost) {
                emeraldCost += random.nextInt(maxEmeraldCost - minEmeraldCost + 1);
            }

            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, emeraldCost),
                    new ItemStack(ModItems.MTG_PACK, itemCount),
                    maxUses,
                    0,
                    0.0F
            );
        };
    }

    private ModTrades() {}
}
