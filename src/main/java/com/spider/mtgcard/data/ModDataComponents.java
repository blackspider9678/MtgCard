// com/spider/mtgcard/data/ModDataComponents.java
package com.spider.mtgcard.data;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModDataComponents {
    public static final ComponentType<String> CARD_ART_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("mtgcard", "card_art_id"),
            ComponentType.<String>builder()
                    .codec(Codec.STRING)                // data <-> NBT
                    .packetCodec(PacketCodecs.STRING)   // data <-> network
                    .build()
    );

    public static void init() { /* class load */ }
}
