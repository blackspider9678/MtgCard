package com.spider.mtgcard.net.payload;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public record FlipHeldCardFacePayload(InteractionHand hand) implements CustomPacketPayload {

    public static final Type<FlipHeldCardFacePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "flip_held_card_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlipHeldCardFacePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeEnum(payload.hand()),
                    (buf) -> new FlipHeldCardFacePayload(buf.readEnum(InteractionHand.class))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
