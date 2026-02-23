package com.spider.mtgcard.net.payload;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public record FlipHeldCardFacePayload(Hand hand) implements CustomPayload {

    public static final Id<FlipHeldCardFacePayload> ID =
            new Id<>(Identifier.of(Mtgcard.MOD_ID, "flip_held_card_face"));

    public static final PacketCodec<RegistryByteBuf, FlipHeldCardFacePayload> CODEC =
            PacketCodec.ofStatic(
                    (buf, pkt) -> buf.writeEnumConstant(pkt.hand()),
                    (buf) -> new FlipHeldCardFacePayload(buf.readEnumConstant(Hand.class))
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}