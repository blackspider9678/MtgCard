package com.spider.mtgcard.loot;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.dice.DiceGradientType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import java.util.Optional;

public final class RandomizeDiceAppearanceFunction implements LootItemFunction {
    public static final RandomizeDiceAppearanceFunction INSTANCE = new RandomizeDiceAppearanceFunction();
    public static final MapCodec<RandomizeDiceAppearanceFunction> CODEC = MapCodec.unit(INSTANCE);

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

    private RandomizeDiceAppearanceFunction() {}

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        RandomSource random = context.getRandom();
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

        stack.set(ModDataComponents.DICE_APPEARANCE, appearance);
        if (foil) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        }
        return stack;
    }

    @Override
    public MapCodec<? extends LootItemFunction> codec() {
        return CODEC;
    }

    private static int randomColor(RandomSource random) {
        return random.nextInt(0x1000000);
    }
}
