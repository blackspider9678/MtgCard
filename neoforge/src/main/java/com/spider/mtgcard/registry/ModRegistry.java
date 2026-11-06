package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModRegistry {
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    public static final TagKey<Item> CARD_TAG = TagKey.create(Registries.ITEM, id("cards"));

    private ModRegistry() {}
}
