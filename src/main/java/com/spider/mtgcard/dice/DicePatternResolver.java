package com.spider.mtgcard.dice;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.Optional;

public final class DicePatternResolver {
    public static Optional<Identifier> resolveBannerPatternAsset(HolderLookup.Provider registries, ItemStack stack) {
        if (registries == null || !DiceCustomizerIngredients.isBannerPattern(stack)) {
            return Optional.empty();
        }

        HolderSet<BannerPattern> patterns = stack.get(DataComponents.PROVIDES_BANNER_PATTERNS);
        if (patterns == null) {
            return Optional.empty();
        }

        return patterns.stream()
                .findFirst()
                .map(holder -> holder.value().assetId());
    }

    private DicePatternResolver() {}
}
