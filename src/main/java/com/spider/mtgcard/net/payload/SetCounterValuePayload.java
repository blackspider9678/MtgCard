package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SetCounterValuePayload(int hand, String key, int value) implements CustomPacketPayload {
    public static final Type<SetCounterValuePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_counter_value"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCounterValuePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetCounterValuePayload::hand,
                    ByteBufCodecs.STRING_UTF8,  SetCounterValuePayload::key,
                    ByteBufCodecs.VAR_INT, SetCounterValuePayload::value,
                    SetCounterValuePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
