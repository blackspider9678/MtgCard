package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SetHiddenPayload(int slot, boolean hidden) implements CustomPacketPayload {
    public static final Type<SetHiddenPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_set_hidden"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetHiddenPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetHiddenPayload::slot,
                    ByteBufCodecs.BOOL,    SetHiddenPayload::hidden,
                    SetHiddenPayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
