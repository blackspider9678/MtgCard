package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SetCounterMetaPayload(int hand, String key, String displayName, String iconKey) implements CustomPayload {
    public static final Id<SetCounterMetaPayload> ID =
            new Id<>(Identifier.of("mtgcard", "set_counter_meta"));

    public static final PacketCodec<RegistryByteBuf, SetCounterMetaPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SetCounterMetaPayload::hand,
                    PacketCodecs.STRING,  SetCounterMetaPayload::key,
                    PacketCodecs.STRING,  SetCounterMetaPayload::displayName,
                    PacketCodecs.STRING,  SetCounterMetaPayload::iconKey,
                    SetCounterMetaPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
