// com/spider/mtgcard/net/payload/GraveyardActionPayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

public record GraveyardActionPayload(BlockPos pos, int syncId, Action action) implements CustomPacketPayload {

    public static final Type<GraveyardActionPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "graveyard_action"));

    public enum Action {
        EXILE_ALL,
        RETURN_ALL
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, GraveyardActionPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buf) -> {
                        buf.writeBlockPos(payload.pos());
                        buf.writeVarInt(payload.syncId());
                        buf.writeVarInt(payload.action().ordinal());
                    },
                    buf -> new GraveyardActionPayload(
                            buf.readBlockPos(),
                            buf.readVarInt(),
                            Action.values()[buf.readVarInt()]
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
