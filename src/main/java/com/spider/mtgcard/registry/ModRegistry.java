package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class ModRegistry {
    public static Identifier id(String p) {
        return Identifier.of(Mtgcard.MOD_ID, p);
    }

    public static final TagKey<Item> CARD_TAG = TagKey.of(RegistryKeys.ITEM, id("cards"));

    private ModRegistry() {}
}
