package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

/** S2C: progress % (0..100) while unbundling packs */
public record UnbundleProgressPayload(int percent) implements CustomPacketPayload {
    public static final Type<UnbundleProgressPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "unbundle_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnbundleProgressPayload> CODEC =
            StreamCodec.ofMember(UnbundleProgressPayload::write, UnbundleProgressPayload::read);

    private static UnbundleProgressPayload read(RegistryFriendlyByteBuf buf) {
        return new UnbundleProgressPayload(buf.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.percent);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
