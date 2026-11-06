package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public record XmlArtUploadPayload(String fileName, String sourceUrl, String setCode, byte[] imgBytes) implements CustomPacketPayload {

    public XmlArtUploadPayload {
        setCode = com.spider.mtgcard.shared.MtgCardPaths.sanitizeSetFolder(setCode);
    }

    public static final Type<XmlArtUploadPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mtgcard", "xml_art_upload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, XmlArtUploadPayload> CODEC =
            StreamCodec.<RegistryFriendlyByteBuf, XmlArtUploadPayload>ofMember(
                    // Value-first encoder: (value, buf)
                    (XmlArtUploadPayload v, RegistryFriendlyByteBuf b) -> write(b, v),
                    // Decoder: (buf) -> value
                    (RegistryFriendlyByteBuf b) -> read(b)
            );

    private static XmlArtUploadPayload read(RegistryFriendlyByteBuf buf) {
        String fn = buf.readUtf();
        String su = buf.readUtf();
        String sc = buf.readUtf();
        byte[] data = buf.readByteArray();
        return new XmlArtUploadPayload(fn, su, sc, data);
    }

    private static void write(RegistryFriendlyByteBuf buf, XmlArtUploadPayload p) {
        buf.writeUtf(p.fileName());
        buf.writeUtf(p.sourceUrl());
        buf.writeUtf(p.setCode());
        buf.writeByteArray(p.imgBytes());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
