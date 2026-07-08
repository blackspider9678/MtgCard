package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SetMenuSlotFacePayload(int containerId, int slotIndex, int face) implements CustomPacketPayload {
    public static final Type<SetMenuSlotFacePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_menu_slot_face"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetMenuSlotFacePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetMenuSlotFacePayload::containerId,
                    ByteBufCodecs.VAR_INT, SetMenuSlotFacePayload::slotIndex,
                    ByteBufCodecs.VAR_INT, SetMenuSlotFacePayload::face,
                    SetMenuSlotFacePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
