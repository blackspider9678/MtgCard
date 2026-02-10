// com/spider/mtgcard/net/payload/GraveyardActionPayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record GraveyardActionPayload(BlockPos pos, int syncId, Action action) implements CustomPayload {

    public static final Id<GraveyardActionPayload> ID =
            new Id<>(Identifier.of("mtgcard", "graveyard_action"));

    public enum Action {
        EXILE_ALL,
        RETURN_ALL
    }

    public static final PacketCodec<RegistryByteBuf, GraveyardActionPayload> CODEC =
            PacketCodec.of(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
