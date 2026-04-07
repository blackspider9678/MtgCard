package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record DeleteCounterPayload(int slot, String key) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DeleteCounterPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "delete_counter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteCounterPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DeleteCounterPayload::slot,
                    ByteBufCodecs.STRING_UTF8,  DeleteCounterPayload::key,
                    DeleteCounterPayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
