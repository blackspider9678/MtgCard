package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> TCG_CARD = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "tcg_card")
    );

    private ModItemTags() {
    }
}
