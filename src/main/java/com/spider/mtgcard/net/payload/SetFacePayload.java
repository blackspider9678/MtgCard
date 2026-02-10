// src/main/java/com/spider/mtgcard/net/payload/SetFacePayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SetFacePayload(int slot, int face) implements CustomPayload {

    public static final Id<SetFacePayload> ID =
            new Id<>(Identifier.of("mtgcard", "set_face"));

    public static final PacketCodec<RegistryByteBuf, SetFacePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, SetFacePayload::slot,
                    PacketCodecs.VAR_INT, SetFacePayload::face,
                    SetFacePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
