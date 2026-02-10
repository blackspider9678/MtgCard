// com/spider/mtgcard/net/payload/SearchPayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;

public record SearchPayload(String q, String order, String dir) implements CustomPayload {
    public static final Id<SearchPayload> ID =
            new Id<>(Identifier.of("mtgcard", "card_db_search"));

    public static final PacketCodec<RegistryByteBuf, SearchPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, SearchPayload::q,
                    PacketCodecs.STRING, SearchPayload::order,
                    PacketCodecs.STRING, SearchPayload::dir,
                    SearchPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
