package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SetHiddenPayload(int slot, boolean hidden) implements CustomPayload {
    public static final Id<SetHiddenPayload> ID =
            new Id<>(Identifier.of("mtgcard", "card_set_hidden"));

    public static final PacketCodec<RegistryByteBuf, SetHiddenPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SetHiddenPayload::slot,
                    PacketCodecs.BOOLEAN,    SetHiddenPayload::hidden,
                    SetHiddenPayload::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
