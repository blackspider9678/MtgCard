package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SetCounterValuePayload(int hand, String key, int value) implements CustomPayload {
    public static final Id<SetCounterValuePayload> ID =
            new Id<>(Identifier.of("mtgcard", "set_counter_value"));

    public static final PacketCodec<RegistryByteBuf, SetCounterValuePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SetCounterValuePayload::hand,
                    PacketCodecs.STRING,  SetCounterValuePayload::key,
                    PacketCodecs.VAR_INT, SetCounterValuePayload::value,
                    SetCounterValuePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
