package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ArtPackets {
    public static final Identifier REQ_ID = Identifier.of("mtgcard", "art_req");
    public static final Identifier CHUNK_ID = Identifier.of("mtgcard", "art_chunk");

    public record ArtRequest(String artKey, String url) implements CustomPayload {
        public static final Id<ArtRequest> ID = new Id<>(REQ_ID);

        public static final PacketCodec<RegistryByteBuf, ArtRequest> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ArtRequest::artKey,
                        PacketCodecs.STRING, ArtRequest::url,
                        ArtRequest::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server -> Client chunk */
    public record ArtChunk(String artKey, int index, int total, byte[] data) implements CustomPayload {
        public static final Id<ArtChunk> ID = new Id<>(CHUNK_ID);

        public static final PacketCodec<RegistryByteBuf, ArtChunk> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, ArtChunk::artKey,
                        PacketCodecs.VAR_INT, ArtChunk::index,
                        PacketCodecs.VAR_INT, ArtChunk::total,
                        PacketCodecs.BYTE_ARRAY, ArtChunk::data,
                        ArtChunk::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Call during init on BOTH sides before registering receivers/sending. */
    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(ArtRequest.ID, ArtRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(ArtChunk.ID, ArtChunk.CODEC);
    }

    private ArtPackets() {}
}
