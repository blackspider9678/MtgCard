package com.spider.mtgcard.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Immutable public description of a cosmetic card sleeve. */
public record CardSleeve(Identifier id, Component displayName, Identifier backTexture,
                         Component artist, String category, int sortOrder) {
    public CardSleeve {
        if (id == null || displayName == null || backTexture == null) {
            throw new IllegalArgumentException("Sleeve id, name, and texture are required");
        }
        category = category == null ? "" : category;
    }

    public CardSleeve(Identifier id, Component displayName, Identifier backTexture) {
        this(id, displayName, backTexture, null, "", 0);
    }
}
