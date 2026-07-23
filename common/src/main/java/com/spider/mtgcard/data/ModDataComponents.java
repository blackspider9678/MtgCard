// com/spider/mtgcard/data/ModDataComponents.java
package com.spider.mtgcard.data;

import com.spider.mtgcard.Mtgcard;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public final class ModDataComponents {
    public static final String CARD_ART_ID_PATH = "card_art_id";
    public static final Identifier CARD_ART_ID_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, CARD_ART_ID_PATH);

    public static DataComponentType.Builder<String> configureCardArtId(DataComponentType.Builder<String> builder) {
        return builder
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8);
    }

    public static void init() { /* class load */ }

    private ModDataComponents() {}
}
