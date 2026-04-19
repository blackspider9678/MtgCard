// com/spider/mtgcard/net/payload/SearchPayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SearchPayload(String q, String order, String dir, boolean rememberSort) implements CustomPacketPayload {
    public static final Type<SearchPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_db_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SearchPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SearchPayload::q,
                    ByteBufCodecs.STRING_UTF8, SearchPayload::order,
                    ByteBufCodecs.STRING_UTF8, SearchPayload::dir,
                    ByteBufCodecs.BOOL, SearchPayload::rememberSort,
                    SearchPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
