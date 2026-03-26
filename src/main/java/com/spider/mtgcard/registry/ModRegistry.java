package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;

public final class ModRegistry {
    public static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, p);
    }

    public static final TagKey<Item> CARD_TAG = TagKey.create(Registries.ITEM, id("cards"));

    private ModRegistry() {}
}
