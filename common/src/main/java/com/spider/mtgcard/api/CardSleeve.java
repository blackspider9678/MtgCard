package com.spider.mtgcard.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Immutable public description of a cosmetic card sleeve. */
public record CardSleeve(ResourceLocation id, Component displayName, ResourceLocation backTexture,
                         Component artist, String category, int sortOrder) {
    public CardSleeve {
        if (id == null || displayName == null || backTexture == null) {
            throw new IllegalArgumentException("Sleeve id, name, and texture are required");
        }
        category = category == null ? "" : category;
    }

    public CardSleeve(ResourceLocation id, Component displayName, ResourceLocation backTexture) {
        this(id, displayName, backTexture, null, "", 0);
    }
}
