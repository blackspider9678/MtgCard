package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record CardDbGridActionPayload(int containerId, int slot, int action) implements CustomPacketPayload {
    public static final Type<CardDbGridActionPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_db_grid_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CardDbGridActionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.CONTAINER_ID, CardDbGridActionPayload::containerId,
                    ByteBufCodecs.VAR_INT, CardDbGridActionPayload::slot,
                    ByteBufCodecs.VAR_INT, CardDbGridActionPayload::action,
                    CardDbGridActionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
