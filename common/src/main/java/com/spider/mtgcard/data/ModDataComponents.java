// com/spider/mtgcard/data/ModDataComponents.java
package com.spider.mtgcard.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public final class ModDataComponents {
    public static final DataComponentType<String> CARD_ART_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("mtgcard", "card_art_id"),
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)                // data <-> NBT
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)   // data <-> network
                    .build()
    );

    public static final DataComponentType<com.spider.mtgcard.dice.DiceAppearance> DICE_APPEARANCE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath("mtgcard", "dice_appearance"),
            DataComponentType.<com.spider.mtgcard.dice.DiceAppearance>builder()
                    .persistent(com.spider.mtgcard.dice.DiceAppearance.CODEC)
                    .networkSynchronized(com.spider.mtgcard.dice.DiceAppearance.STREAM_CODEC).build());

    public static void init() { /* class load */ }
}
