package com.spider.mtgcard.net;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;

public final class ArtPackets {
    public static final ResourceLocation REQ_ID = ResourceLocation.fromNamespaceAndPath("mtgcard", "art_req");
    public static final ResourceLocation CHUNK_ID = ResourceLocation.fromNamespaceAndPath("mtgcard", "art_chunk");

    public record ArtRequest(String artKey, String url, String fallbackKeys, String setCode, String game) implements CustomPacketPayload {
        public ArtRequest(String artKey, String url) { this(artKey, url, "", "", "mtg"); }
        public static final Type<ArtRequest> ID = new Type<>(REQ_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ArtRequest> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ArtRequest::artKey,
                        ByteBufCodecs.STRING_UTF8, ArtRequest::url,
                        ByteBufCodecs.STRING_UTF8, ArtRequest::fallbackKeys,
                        ByteBufCodecs.STRING_UTF8, ArtRequest::setCode,
                        ByteBufCodecs.STRING_UTF8, ArtRequest::game,
                        ArtRequest::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Server -> Client chunk */
    public record ArtChunk(String artKey, String game, int index, int total, byte[] data) implements CustomPacketPayload {
        public ArtChunk(String artKey, int index, int total, byte[] data) { this(artKey, "mtg", index, total, data); }
        public static final Type<ArtChunk> ID = new Type<>(CHUNK_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ArtChunk> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ArtChunk::artKey,
                        ByteBufCodecs.STRING_UTF8, ArtChunk::game,
                        ByteBufCodecs.VAR_INT, ArtChunk::index,
                        ByteBufCodecs.VAR_INT, ArtChunk::total,
                        ByteBufCodecs.BYTE_ARRAY, ArtChunk::data,
                        ArtChunk::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Call during init on BOTH sides before registering receivers/sending. */
    public static void registerTypes() {}

    private ArtPackets() {}
}
