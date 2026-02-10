package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record XmlArtUploadPayload(String fileName, String sourceUrl, byte[] imgBytes) implements CustomPayload {

    public static final Id<XmlArtUploadPayload> ID =
            new Id<>(Identifier.of("mtgcard", "xml_art_upload"));

    public static final PacketCodec<RegistryByteBuf, XmlArtUploadPayload> CODEC =
            PacketCodec.<RegistryByteBuf, XmlArtUploadPayload>of(
                    // Value-first encoder: (value, buf)
                    (XmlArtUploadPayload v, RegistryByteBuf b) -> write(b, v),
                    // Decoder: (buf) -> value
                    (RegistryByteBuf b) -> read(b)
            );

    private static XmlArtUploadPayload read(RegistryByteBuf buf) {
        String fn = buf.readString();
        String su = buf.readString();
        byte[] data = buf.readByteArray();
        return new XmlArtUploadPayload(fn, su, data);
    }

    private static void write(RegistryByteBuf buf, XmlArtUploadPayload p) {
        buf.writeString(p.fileName());
        buf.writeString(p.sourceUrl());
        buf.writeByteArray(p.imgBytes());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
