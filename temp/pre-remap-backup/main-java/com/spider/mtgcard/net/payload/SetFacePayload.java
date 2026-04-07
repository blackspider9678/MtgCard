// src/main/java/com/spider/mtgcard/net/payload/SetFacePayload.java
package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public record SetFacePayload(int slot, int face) implements CustomPacketPayload {

    public static final Type<SetFacePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFacePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetFacePayload::slot,
                    ByteBufCodecs.VAR_INT, SetFacePayload::face,
                    SetFacePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
