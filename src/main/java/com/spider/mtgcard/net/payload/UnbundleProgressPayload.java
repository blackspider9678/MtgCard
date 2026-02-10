package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C: progress % (0..100) while unbundling packs */
public record UnbundleProgressPayload(int percent) implements CustomPayload {
    public static final Id<UnbundleProgressPayload> ID =
            new Id<>(Identifier.of("mtgcard", "unbundle_progress"));

    public static final PacketCodec<RegistryByteBuf, UnbundleProgressPayload> CODEC =
            PacketCodec.of(UnbundleProgressPayload::write, UnbundleProgressPayload::read);

    private static UnbundleProgressPayload read(RegistryByteBuf buf) {
        return new UnbundleProgressPayload(buf.readVarInt());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(this.percent);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
