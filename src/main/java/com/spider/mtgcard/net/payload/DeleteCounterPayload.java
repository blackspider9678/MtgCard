package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DeleteCounterPayload(int slot, String key) implements CustomPayload {
    public static final CustomPayload.Id<DeleteCounterPayload> ID =
            new CustomPayload.Id<>(Identifier.of("mtgcard", "delete_counter"));

    public static final PacketCodec<RegistryByteBuf, DeleteCounterPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, DeleteCounterPayload::slot,
                    PacketCodecs.STRING,  DeleteCounterPayload::key,
                    DeleteCounterPayload::new
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
