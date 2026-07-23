package com.spider.mtgcard.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CustomCardPackets {
    public static final Identifier BATCH_CREATE_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_batch_create");
    public static final Identifier SYNC_FULL_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_sync_full");
    public static final Identifier SYNC_FULL_CHUNK_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_sync_full_chunk");
    public static final Identifier SYNC_DELTA_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_sync_delta");
    public static final Identifier ART_BEGIN_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_art_begin");
    public static final Identifier ART_CHUNK_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_art_chunk");
    public static final Identifier ART_FINISH_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_art_finish");
    public static final Identifier ART_READY_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_art_ready");
    public static final Identifier ART_INVALIDATE_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "custom_art_invalidate");

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

    public record WireMeta(
            String id, String name, String manaCost, String typeLine, String rarity, String set,
            String oracleText, String power, String toughness, String loyalty,
            boolean doubleFaced,
            String backName, String backTypeLine, String backOracleText,
            String backPower, String backToughness, String backLoyalty
    ) {}

    private static StreamCodec<RegistryFriendlyByteBuf, BatchEntry> batchEntryCodec() {
        return StreamCodec.ofMember(
                (entry, buf) -> {
                    buf.writeUtf(entry.id());
                    buf.writeUtf(entry.name());
                    buf.writeUtf(entry.manaCost());
                    buf.writeUtf(entry.typeLine());
                    buf.writeUtf(entry.rarity());
                    buf.writeUtf(entry.set());
                    buf.writeUtf(entry.oracleText());
                    buf.writeUtf(entry.power());
                    buf.writeUtf(entry.toughness());
                    buf.writeUtf(entry.loyalty());
                    buf.writeBoolean(entry.doubleFaced());
                    buf.writeUtf(entry.backName());
                    buf.writeUtf(entry.backTypeLine());
                    buf.writeUtf(entry.backOracleText());
                    buf.writeUtf(entry.backPower());
                    buf.writeUtf(entry.backToughness());
                    buf.writeUtf(entry.backLoyalty());
                    buf.writeUtf(entry.artKeyFront());
                    buf.writeUtf(entry.artKeyBack());
                },
                buf -> new BatchEntry(
                        buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readBoolean(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf()
                )
        );
    }

    private static StreamCodec<RegistryFriendlyByteBuf, WireMeta> wireMetaCodec() {
        return StreamCodec.ofMember(
                (meta, buf) -> {
                    buf.writeUtf(meta.id());
                    buf.writeUtf(meta.name());
                    buf.writeUtf(meta.manaCost());
                    buf.writeUtf(meta.typeLine());
                    buf.writeUtf(meta.rarity());
                    buf.writeUtf(meta.set());
                    buf.writeUtf(meta.oracleText());
                    buf.writeUtf(meta.power());
                    buf.writeUtf(meta.toughness());
                    buf.writeUtf(meta.loyalty());
                    buf.writeBoolean(meta.doubleFaced());
                    buf.writeUtf(meta.backName());
                    buf.writeUtf(meta.backTypeLine());
                    buf.writeUtf(meta.backOracleText());
                    buf.writeUtf(meta.backPower());
                    buf.writeUtf(meta.backToughness());
                    buf.writeUtf(meta.backLoyalty());
                },
                buf -> new WireMeta(
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        buf.readBoolean(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()
                )
        );
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<BatchEntry>> BATCH_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, batchEntryCodec());

    private static final StreamCodec<RegistryFriendlyByteBuf, List<WireMeta>> WIRE_META_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, wireMetaCodec());

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);

    public record CustomBatchCreate(List<BatchEntry> entries) implements CustomPacketPayload {
        public static final Type<CustomBatchCreate> ID = new Type<>(BATCH_CREATE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomBatchCreate> CODEC =
                StreamCodec.composite(BATCH_LIST_CODEC, CustomBatchCreate::entries, CustomBatchCreate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomSyncFull(List<WireMeta> entries) implements CustomPacketPayload {
        public static final Type<CustomSyncFull> ID = new Type<>(SYNC_FULL_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomSyncFull> CODEC =
                StreamCodec.composite(WIRE_META_LIST_CODEC, CustomSyncFull::entries, CustomSyncFull::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomSyncFullChunk(UUID syncId, int index, int total, List<WireMeta> entries)
            implements CustomPacketPayload {
        public static final Type<CustomSyncFullChunk> ID = new Type<>(SYNC_FULL_CHUNK_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomSyncFullChunk> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUUID(payload.syncId());
                            buf.writeVarInt(payload.index());
                            buf.writeVarInt(payload.total());
                            WIRE_META_LIST_CODEC.encode(buf, payload.entries());
                        },
                        buf -> new CustomSyncFullChunk(
                                buf.readUUID(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                WIRE_META_LIST_CODEC.decode(buf)
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomSyncDelta(WireMeta entry) implements CustomPacketPayload {
        public static final Type<CustomSyncDelta> ID = new Type<>(SYNC_DELTA_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomSyncDelta> CODEC =
                StreamCodec.composite(wireMetaCodec(), CustomSyncDelta::entry, CustomSyncDelta::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomArtBegin(
            String uploadId,
            String artKey,
            String setCode,
            String ext,
            int totalBytes,
            int chunkSize,
            int totalChunks
    ) implements CustomPacketPayload {
        public static final Type<CustomArtBegin> ID = new Type<>(ART_BEGIN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtBegin> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUtf(payload.uploadId());
                            buf.writeUtf(payload.artKey());
                            buf.writeUtf(payload.setCode());
                            buf.writeUtf(payload.ext());
                            buf.writeVarInt(payload.totalBytes());
                            buf.writeVarInt(payload.chunkSize());
                            buf.writeVarInt(payload.totalChunks());
                        },
                        buf -> new CustomArtBegin(
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt()
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomArtChunk(String uploadId, int chunkIndex, byte[] bytes) implements CustomPacketPayload {
        public static final Type<CustomArtChunk> ID = new Type<>(ART_CHUNK_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtChunk> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUtf(payload.uploadId());
                            buf.writeVarInt(payload.chunkIndex());
                            buf.writeByteArray(payload.bytes());
                        },
                        buf -> new CustomArtChunk(buf.readUtf(), buf.readVarInt(), buf.readByteArray())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomArtFinish(String uploadId) implements CustomPacketPayload {
        public static final Type<CustomArtFinish> ID = new Type<>(ART_FINISH_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtFinish> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CustomArtFinish::uploadId, CustomArtFinish::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomArtReady(String artKey, String setCode) implements CustomPacketPayload {
        public static final Type<CustomArtReady> ID = new Type<>(ART_READY_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtReady> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeUtf(payload.artKey());
                            buf.writeUtf(payload.setCode());
                        },
                        buf -> new CustomArtReady(buf.readUtf(), buf.readUtf())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CustomArtInvalidate(List<String> artKeys) implements CustomPacketPayload {
        public static final Type<CustomArtInvalidate> ID = new Type<>(ART_INVALIDATE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomArtInvalidate> CODEC =
                StreamCodec.composite(STRING_LIST_CODEC, CustomArtInvalidate::artKeys, CustomArtInvalidate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(CustomBatchCreate.ID, CustomBatchCreate.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CustomArtBegin.ID, CustomArtBegin.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CustomArtChunk.ID, CustomArtChunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CustomArtFinish.ID, CustomArtFinish.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(CustomSyncFull.ID, CustomSyncFull.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CustomSyncFullChunk.ID, CustomSyncFullChunk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CustomSyncDelta.ID, CustomSyncDelta.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CustomArtReady.ID, CustomArtReady.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CustomArtInvalidate.ID, CustomArtInvalidate.CODEC);
    }

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(CustomBatchCreate.ID, (payload, ctx) ->
                ctx.server().execute(() -> CustomCardServer.handleBatch(payload, ctx.player()))
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtBegin.ID, (payload, ctx) ->
                ctx.server().execute(() -> CustomArtUploadServer.handleBegin(payload, ctx.player()))
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtChunk.ID, (payload, ctx) ->
                ctx.server().execute(() -> CustomArtUploadServer.handleChunk(payload, ctx.player()))
        );

        ServerPlayNetworking.registerGlobalReceiver(CustomArtFinish.ID, (payload, ctx) ->
                ctx.server().execute(() -> CustomArtUploadServer.handleFinish(payload, ctx.player(), ctx.server()))
        );
    }

    private CustomCardPackets() {}
}
