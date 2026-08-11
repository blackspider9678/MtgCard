package com.spider.mtgcard.data;

import com.mojang.serialization.Codec;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.dice.DiceAppearance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public final class ModDataComponents {
    public static final String CARD_ART_ID_PATH = "card_art_id";
    public static final Identifier CARD_ART_ID_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, CARD_ART_ID_PATH);

    public static final String DICE_APPEARANCE_PATH = "dice_appearance";
    public static final Identifier DICE_APPEARANCE_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, DICE_APPEARANCE_PATH);
    public static final DataComponentType<DiceAppearance> DICE_APPEARANCE =
            configureDiceAppearance(DataComponentType.<DiceAppearance>builder()).build();

    public static DataComponentType.Builder<String> configureCardArtId(DataComponentType.Builder<String> builder) {
        return builder
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8);
    }

    public static DataComponentType.Builder<DiceAppearance> configureDiceAppearance(DataComponentType.Builder<DiceAppearance> builder) {
        return builder
                .persistent(DiceAppearance.CODEC)
                .networkSynchronized(DiceAppearance.STREAM_CODEC);
    }

    public static void init() { /* class load */ }

    private ModDataComponents() {}
}
