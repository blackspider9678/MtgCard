package com.spider.mtgcard.dice;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DiceCustomizerIngredients {
    public static final Item MATERIAL_ITEM = Items.ECHO_SHARD;

    // Kept as one constant so the foil ingredient can be changed without touching renderer logic.
    public static final Item FOIL_ITEM = Items.GOLD_NUGGET;

    public static boolean isMaterial(ItemStack stack) {
        return stack.is(MATERIAL_ITEM);
    }

    public static boolean isFoil(ItemStack stack) {
        return stack.is(FOIL_ITEM);
    }

    public static boolean isBannerPattern(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.PROVIDES_BANNER_PATTERNS);
    }

    private DiceCustomizerIngredients() {
    }
}
