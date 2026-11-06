package com.spider.mtgcard.dice;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.Optional;

public final class DicePatternResolver {
    public static Optional<ResourceLocation> resolveBannerPatternAsset(HolderLookup.Provider registries, ItemStack stack) {
        if (registries == null || !DiceCustomizerIngredients.isBannerPattern(stack)) {
            return Optional.empty();
        }

        TagKey<BannerPattern> tag = ((net.minecraft.world.item.BannerPatternItem) stack.getItem()).getBannerPattern();
        if (tag == null) {
            return Optional.empty();
        }

        return registries.lookupOrThrow(Registries.BANNER_PATTERN)
                .get(tag)
                .flatMap(patterns -> patterns.stream().findFirst())
                .map(holder -> holder.value().assetId());
    }

    private DicePatternResolver() {}
}
