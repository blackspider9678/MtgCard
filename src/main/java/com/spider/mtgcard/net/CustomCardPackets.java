package com.spider.mtgcard.net;

import com.spider.mtgcard.net.payload.XmlArtUploadPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class CustomCardPackets {

    // =========================
    // IDs
    // =========================
    public static final Identifier BATCH_CREATE_ID = Identifier.fromNamespaceAndPath("mtgcard","custom_batch_create");
    public static final Identifier SYNC_FULL_ID    = Identifier.fromNamespaceAndPath("mtgcard","custom_sync_full");
    public static final Identifier SYNC_DELTA_ID   = Identifier.fromNamespaceAndPath("mtgcard","custom_sync_delta");

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

    public static final Identifier ART_BEGIN_ID  = Identifier.fromNamespaceAndPath("mtgcard","custom_art_begin");
    public static final Identifier ART_CHUNK_ID  = Identifier.fromNamespaceAndPath("mtgcard","custom_art_chunk");
    public static final Identifier ART_FINISH_ID = Identifier.fromNamespaceAndPath("mtgcard","custom_art_finish");

    // Manual codec for BatchEntry (explicit order)
    // Manual codec for BatchEntry (explicit order)
    private static StreamCodec<RegistryFriendlyByteBuf, BatchEntry> batchEntryCodec() {
        return StreamCodec.ofMember(
                (BatchEntry e, RegistryFriendlyByteBuf buf) -> {
                    // ✅ NEW: id first
                    buf.writeUtf(e.id());

                    buf.writeUtf(e.name());
                    buf.writeUtf(e.manaCost());
                    buf.writeUtf(e.typeLine());
                    buf.writeUtf(e.rarity());
                    buf.writeUtf(e.set());
                    buf.writeUtf(e.oracleText());
                    buf.writeUtf(e.power());
                    buf.writeUtf(e.toughness());
                    buf.writeUtf(e.loyalty());
                    buf.writeBoolean(e.doubleFaced());
                    buf.writeUtf(e.backName());
                    buf.writeUtf(e.backTypeLine());
                    buf.writeUtf(e.backOracleText());
                    buf.writeUtf(e.backPower());
                    buf.writeUtf(e.backToughness());
                    buf.writeUtf(e.backLoyalty());
                    buf.writeUtf(e.artKeyFront());
                    buf.writeUtf(e.artKeyBack());
                },
                (RegistryFriendlyByteBuf buf) -> {
                    // ✅ NEW: id first
                    String id = buf.readUtf();

                    String name = buf.readUtf();
                    String manaCost = buf.readUtf();
                    String typeLine = buf.readUtf();
                    String rarity = buf.readUtf();
                    String set = buf.readUtf();
                    String oracleText = buf.readUtf();
                    String power = buf.readUtf();
                    String toughness = buf.readUtf();
                    String loyalty = buf.readUtf();
                    boolean doubleFaced = buf.readBoolean();
                    String backName = buf.readUtf();
                    String backTypeLine = buf.readUtf();
                    String backOracleText = buf.readUtf();
                    String backPower = buf.readUtf();
                    String backToughness = buf.readUtf();
                    String backLoyalty = buf.readUtf();
                    String artKeyFront = buf.readUtf();
                    String artKeyBack  = buf.readUtf();

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


    public record CustomArtUpload(String key, byte[] data) implements CustomPacketPayload {
        public static final Type<CustomArtUpload> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "custom_art_upload"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtUpload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, CustomArtUpload::key,
                        ByteBufCodecs.BYTE_ARRAY, CustomArtUpload::data,
                        CustomArtUpload::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<BatchEntry>> BATCH_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, batchEntryCodec());

    public record CustomBatchCreate(List<BatchEntry> entries) implements CustomPacketPayload {
        public static final Type<CustomBatchCreate> ID = new Type<>(BATCH_CREATE_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomBatchCreate> CODEC =
                StreamCodec.composite(
                        BATCH_LIST_CODEC,
                        CustomBatchCreate::entries,
                        CustomBatchCreate::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
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

    private static StreamCodec<RegistryFriendlyByteBuf, WireMeta> wireMetaCodec() {
        return StreamCodec.ofMember(
                (WireMeta m, RegistryFriendlyByteBuf buf) -> {
                    buf.writeUtf(m.id());
                    buf.writeUtf(m.name());
                    buf.writeUtf(m.manaCost());
                    buf.writeUtf(m.typeLine());
                    buf.writeUtf(m.rarity());
                    buf.writeUtf(m.set());
                    buf.writeUtf(m.oracleText());
                    buf.writeUtf(m.power());
                    buf.writeUtf(m.toughness());
                    buf.writeUtf(m.loyalty());
                    buf.writeBoolean(m.doubleFaced());
                    buf.writeUtf(m.backName());
                    buf.writeUtf(m.backTypeLine());
                    buf.writeUtf(m.backOracleText());
                    buf.writeUtf(m.backPower());
                    buf.writeUtf(m.backToughness());
                    buf.writeUtf(m.backLoyalty());
                },
                (RegistryFriendlyByteBuf buf) -> new WireMeta(
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readBoolean(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
                )
        );
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<WireMeta>> WIREMETA_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, wireMetaCodec());

    public record CustomSyncFull(List<WireMeta> entries) implements CustomPacketPayload {
        public static final Type<CustomSyncFull> ID = new Type<>(SYNC_FULL_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomSyncFull> CODEC =
                StreamCodec.composite(
                        WIREMETA_LIST_CODEC,
                        CustomSyncFull::entries,
                        CustomSyncFull::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CustomSyncDelta(WireMeta entry) implements CustomPacketPayload {
        public static final Type<CustomSyncDelta> ID = new Type<>(SYNC_DELTA_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomSyncDelta> CODEC =
                StreamCodec.composite(
                        wireMetaCodec(),
                        CustomSyncDelta::entry,
                        CustomSyncDelta::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // =========================
    // Registration helpers
    // =========================
    public static void registerTypes() {
        // C2S
        PayloadTypeRegistry.serverboundPlay().register(CustomBatchCreate.ID, CustomBatchCreate.CODEC);

        // S2C
        PayloadTypeRegistry.clientboundPlay().register(CustomSyncFull.ID,  CustomSyncFull.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CustomSyncDelta.ID, CustomSyncDelta.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(CustomArtReady.ID,  CustomArtReady.CODEC);

        // C2S art upload
        PayloadTypeRegistry.serverboundPlay().register(CustomArtBegin.ID,  CustomArtBegin.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CustomArtChunk.ID,  CustomArtChunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CustomArtFinish.ID, CustomArtFinish.CODEC);
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
    ) implements CustomPacketPayload {
        public static final Type<CustomArtBegin> ID = new Type<>(ART_BEGIN_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtBegin> CODEC =
                StreamCodec.ofMember(
                        (p, buf) -> {
                            buf.writeUtf(p.uploadId());
                            buf.writeUtf(p.artKey());
                            buf.writeUtf(p.ext());
                            buf.writeVarInt(p.totalBytes());
                            buf.writeVarInt(p.chunkSize());
                            buf.writeVarInt(p.totalChunks());
                        },
                        (buf) -> new CustomArtBegin(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt()
                        )
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CustomArtChunk(
            String uploadId,
            int chunkIndex,
            byte[] bytes
    ) implements CustomPacketPayload {
        public static final Type<CustomArtChunk> ID = new Type<>(ART_CHUNK_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtChunk> CODEC =
                StreamCodec.ofMember(
                        (p, buf) -> {
                            buf.writeUtf(p.uploadId());
                            buf.writeVarInt(p.chunkIndex());
                            buf.writeByteArray(p.bytes());
                        },
                        (buf) -> new CustomArtChunk(
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readByteArray()
                        )
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CustomArtFinish(String uploadId) implements CustomPacketPayload {
        public static final Type<CustomArtFinish> ID = new Type<>(ART_FINISH_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtFinish> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8,
                        CustomArtFinish::uploadId,
                        CustomArtFinish::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static final Identifier ART_READY_ID = Identifier.fromNamespaceAndPath("mtgcard","custom_art_ready");

    public record CustomArtReady(String artKey) implements CustomPacketPayload {
        public static final Type<CustomArtReady> ID = new Type<>(ART_READY_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtReady> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, CustomArtReady::artKey,
                        CustomArtReady::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private CustomCardPackets() {}
}
