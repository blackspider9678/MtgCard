package com.spider.mtgcard.net;

import com.spider.mtgcard.net.payload.XmlArtUploadPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class CustomCardPackets {

    // =========================
    // IDs
    // =========================
    public static final Identifier BATCH_CREATE_ID = Identifier.of("mtgcard","custom_batch_create");
    public static final Identifier SYNC_FULL_ID    = Identifier.of("mtgcard","custom_sync_full");
    public static final Identifier SYNC_DELTA_ID   = Identifier.of("mtgcard","custom_sync_delta");

    // =========================
    // C2S: batch create
    // =========================
    public record BatchEntry(
            String id,

            String name, String manaCost, String typeLine, String rarity, String set, String oracleText,
            String power, String toughness, String loyalty,
            boolean doubleFaced,
            String backName, String backTypeLine, String backOracleText,
            String backPower, String backToughness, String backLoyalty,

            String artKeyFront,
            String artKeyBack
    ) {}

    public static final Identifier ART_BEGIN_ID  = Identifier.of("mtgcard","custom_art_begin");
    public static final Identifier ART_CHUNK_ID  = Identifier.of("mtgcard","custom_art_chunk");
    public static final Identifier ART_FINISH_ID = Identifier.of("mtgcard","custom_art_finish");

    // Manual codec for BatchEntry (explicit order)
    // Manual codec for BatchEntry (explicit order)
    private static PacketCodec<RegistryByteBuf, BatchEntry> batchEntryCodec() {
        return PacketCodec.of(
                (BatchEntry e, RegistryByteBuf buf) -> {
                    // ✅ NEW: id first
                    buf.writeString(e.id());

                    buf.writeString(e.name());
                    buf.writeString(e.manaCost());
                    buf.writeString(e.typeLine());
                    buf.writeString(e.rarity());
                    buf.writeString(e.set());
                    buf.writeString(e.oracleText());
                    buf.writeString(e.power());
                    buf.writeString(e.toughness());
                    buf.writeString(e.loyalty());
                    buf.writeBoolean(e.doubleFaced());
                    buf.writeString(e.backName());
                    buf.writeString(e.backTypeLine());
                    buf.writeString(e.backOracleText());
                    buf.writeString(e.backPower());
                    buf.writeString(e.backToughness());
                    buf.writeString(e.backLoyalty());
                    buf.writeString(e.artKeyFront());
                    buf.writeString(e.artKeyBack());
                },
                (RegistryByteBuf buf) -> {
                    // ✅ NEW: id first
                    String id = buf.readString();

                    String name = buf.readString();
                    String manaCost = buf.readString();
                    String typeLine = buf.readString();
                    String rarity = buf.readString();
                    String set = buf.readString();
                    String oracleText = buf.readString();
                    String power = buf.readString();
                    String toughness = buf.readString();
                    String loyalty = buf.readString();
                    boolean doubleFaced = buf.readBoolean();
                    String backName = buf.readString();
                    String backTypeLine = buf.readString();
                    String backOracleText = buf.readString();
                    String backPower = buf.readString();
                    String backToughness = buf.readString();
                    String backLoyalty = buf.readString();
                    String artKeyFront = buf.readString();
                    String artKeyBack  = buf.readString();

                    // ✅ constructor args must match record field order
                    return new BatchEntry(
                            id,
                            name, manaCost, typeLine, rarity, set, oracleText,
                            power, toughness, loyalty,
                            doubleFaced,
                            backName, backTypeLine, backOracleText, backPower, backToughness, backLoyalty,
                            artKeyFront, artKeyBack
                    );
                }
        );
    }


    public record CustomArtUpload(String key, byte[] data) implements CustomPayload {
        public static final Id<CustomArtUpload> ID = new Id<>(Identifier.of("mtgcard", "custom_art_upload"));

        public static final PacketCodec<RegistryByteBuf, CustomArtUpload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, CustomArtUpload::key,
                        PacketCodecs.BYTE_ARRAY, CustomArtUpload::data,
                        CustomArtUpload::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private static final PacketCodec<RegistryByteBuf, List<BatchEntry>> BATCH_LIST_CODEC =
            PacketCodecs.collection(ArrayList::new, batchEntryCodec());

    public record CustomBatchCreate(List<BatchEntry> entries) implements CustomPayload {
        public static final Id<CustomBatchCreate> ID = new Id<>(BATCH_CREATE_ID);

        public static final PacketCodec<RegistryByteBuf, CustomBatchCreate> CODEC =
                PacketCodec.tuple(
                        BATCH_LIST_CODEC,
                        CustomBatchCreate::entries,
                        CustomBatchCreate::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================
    // S2C: sync (full/delta)
    // =========================
    /** Minimal wire meta (no images) */
    public record WireMeta(
            String id, String name, String manaCost, String typeLine, String rarity, String set,
            String oracleText, String power, String toughness, String loyalty,
            boolean doubleFaced,
            String backName, String backTypeLine, String backOracleText, String backPower, String backToughness, String backLoyalty
    ) {}

    private static PacketCodec<RegistryByteBuf, WireMeta> wireMetaCodec() {
        return PacketCodec.of(
                (WireMeta m, RegistryByteBuf buf) -> {
                    buf.writeString(m.id());
                    buf.writeString(m.name());
                    buf.writeString(m.manaCost());
                    buf.writeString(m.typeLine());
                    buf.writeString(m.rarity());
                    buf.writeString(m.set());
                    buf.writeString(m.oracleText());
                    buf.writeString(m.power());
                    buf.writeString(m.toughness());
                    buf.writeString(m.loyalty());
                    buf.writeBoolean(m.doubleFaced());
                    buf.writeString(m.backName());
                    buf.writeString(m.backTypeLine());
                    buf.writeString(m.backOracleText());
                    buf.writeString(m.backPower());
                    buf.writeString(m.backToughness());
                    buf.writeString(m.backLoyalty());
                },
                (RegistryByteBuf buf) -> new WireMeta(
                        buf.readString(), buf.readString(), buf.readString(), buf.readString(), buf.readString(), buf.readString(),
                        buf.readString(), buf.readString(), buf.readString(), buf.readString(),
                        buf.readBoolean(),
                        buf.readString(), buf.readString(), buf.readString(), buf.readString(), buf.readString(), buf.readString()
                )
        );
    }

    private static final PacketCodec<RegistryByteBuf, List<WireMeta>> WIREMETA_LIST_CODEC =
            PacketCodecs.collection(ArrayList::new, wireMetaCodec());

    public record CustomSyncFull(List<WireMeta> entries) implements CustomPayload {
        public static final Id<CustomSyncFull> ID = new Id<>(SYNC_FULL_ID);

        public static final PacketCodec<RegistryByteBuf, CustomSyncFull> CODEC =
                PacketCodec.tuple(
                        WIREMETA_LIST_CODEC,
                        CustomSyncFull::entries,
                        CustomSyncFull::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CustomSyncDelta(WireMeta entry) implements CustomPayload {
        public static final Id<CustomSyncDelta> ID = new Id<>(SYNC_DELTA_ID);

        public static final PacketCodec<RegistryByteBuf, CustomSyncDelta> CODEC =
                PacketCodec.tuple(
                        wireMetaCodec(),
                        CustomSyncDelta::entry,
                        CustomSyncDelta::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================
    // Registration helpers
    // =========================
    public static void registerTypes() {
        // C2S
        PayloadTypeRegistry.playC2S().register(CustomBatchCreate.ID, CustomBatchCreate.CODEC);

        // S2C
        PayloadTypeRegistry.playS2C().register(CustomSyncFull.ID,  CustomSyncFull.CODEC);
        PayloadTypeRegistry.playS2C().register(CustomSyncDelta.ID, CustomSyncDelta.CODEC);

        PayloadTypeRegistry.playS2C().register(CustomArtReady.ID,  CustomArtReady.CODEC);

        // C2S art upload
        PayloadTypeRegistry.playC2S().register(CustomArtBegin.ID,  CustomArtBegin.CODEC);
        PayloadTypeRegistry.playC2S().register(CustomArtChunk.ID,  CustomArtChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(CustomArtFinish.ID, CustomArtFinish.CODEC);
    }


    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(CustomBatchCreate.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    CustomCardServer.handleBatch(payload, ctx.player());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtBegin.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    CustomArtUploadServer.handleBegin(payload, ctx.player());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtChunk.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    int n = (payload.bytes() == null) ? -1 : payload.bytes().length;
                    CustomArtUploadServer.handleChunk(payload, ctx.player());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtFinish.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    CustomArtUploadServer.handleFinish(payload, ctx.player(), ctx.server());
                })
        );
    }

    public record CustomArtBegin(
            String uploadId,   // UUID string
            String artKey,     // e.g. custom_<hash>_front
            String ext,        // "png" or "webp" (optional but nice)
            int totalBytes,    // total expected
            int chunkSize,     // client chosen
            int totalChunks    // client chosen
    ) implements CustomPayload {
        public static final Id<CustomArtBegin> ID = new Id<>(ART_BEGIN_ID);

        public static final PacketCodec<RegistryByteBuf, CustomArtBegin> CODEC =
                PacketCodec.of(
                        (p, buf) -> {
                            buf.writeString(p.uploadId());
                            buf.writeString(p.artKey());
                            buf.writeString(p.ext());
                            buf.writeVarInt(p.totalBytes());
                            buf.writeVarInt(p.chunkSize());
                            buf.writeVarInt(p.totalChunks());
                        },
                        (buf) -> new CustomArtBegin(
                                buf.readString(),
                                buf.readString(),
                                buf.readString(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt()
                        )
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CustomArtChunk(
            String uploadId,
            int chunkIndex,
            byte[] bytes
    ) implements CustomPayload {
        public static final Id<CustomArtChunk> ID = new Id<>(ART_CHUNK_ID);

        public static final PacketCodec<RegistryByteBuf, CustomArtChunk> CODEC =
                PacketCodec.of(
                        (p, buf) -> {
                            buf.writeString(p.uploadId());
                            buf.writeVarInt(p.chunkIndex());
                            buf.writeByteArray(p.bytes());
                        },
                        (buf) -> new CustomArtChunk(
                                buf.readString(),
                                buf.readVarInt(),
                                buf.readByteArray()
                        )
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CustomArtFinish(String uploadId) implements CustomPayload {
        public static final Id<CustomArtFinish> ID = new Id<>(ART_FINISH_ID);

        public static final PacketCodec<RegistryByteBuf, CustomArtFinish> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING,
                        CustomArtFinish::uploadId,
                        CustomArtFinish::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static final Identifier ART_READY_ID = Identifier.of("mtgcard","custom_art_ready");

    public record CustomArtReady(String artKey) implements CustomPayload {
        public static final Id<CustomArtReady> ID = new Id<>(ART_READY_ID);

        public static final PacketCodec<RegistryByteBuf, CustomArtReady> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, CustomArtReady::artKey,
                        CustomArtReady::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private CustomCardPackets() {}
}
