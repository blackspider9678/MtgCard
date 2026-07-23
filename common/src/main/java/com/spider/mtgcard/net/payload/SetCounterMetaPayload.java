package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SetCounterMetaPayload(int hand, String key, String displayName, String iconKey) implements CustomPacketPayload {
    public static final Type<SetCounterMetaPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_counter_meta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCounterMetaPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetCounterMetaPayload::hand,
                    ByteBufCodecs.STRING_UTF8,  SetCounterMetaPayload::key,
                    ByteBufCodecs.STRING_UTF8,  SetCounterMetaPayload::displayName,
                    ByteBufCodecs.STRING_UTF8,  SetCounterMetaPayload::iconKey,
                    SetCounterMetaPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
