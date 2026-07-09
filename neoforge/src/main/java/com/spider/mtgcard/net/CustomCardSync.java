package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta;
import com.spider.mtgcard.net.CustomCardPackets.CustomSyncDelta;
import com.spider.mtgcard.net.CustomCardPackets.CustomSyncFull;
import com.spider.mtgcard.net.CustomCardPackets.CustomSyncFullChunk;
import com.spider.mtgcard.net.CustomCardPackets.WireMeta;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class CustomCardSync {
    private static final int FULL_SYNC_CHUNK_SIZE = 128;
    private static final int FULL_SYNC_CHUNKS_PER_TICK = 2;

    private static final Map<UUID, FullSyncJob> FULL_SYNC_JOBS = new ConcurrentHashMap<>();
    private static volatile Function<MinecraftServer, CustomCardStore> storeGetter = CustomCardSync::getStore;

    private static final class FullSyncJob {
        final UUID playerId;
        final UUID syncId = UUID.randomUUID();
        final List<WireMeta> entries;
        final int totalChunks;
        int nextChunk;

        FullSyncJob(ServerPlayer player, List<WireMeta> entries) {
            this.playerId = player.getUUID();
            this.entries = entries;
            this.totalChunks = Math.max(1, (int) Math.ceil(entries.size() / (double) FULL_SYNC_CHUNK_SIZE));
        }
    }

    public static void initServerHooks(Function<MinecraftServer, CustomCardStore> storeGetter) {
        CustomCardSync.storeGetter = storeGetter == null ? CustomCardSync::getStore : storeGetter;
    }

    public static CustomCardStore getStore(MinecraftServer server) {
        return WorldState.get(server).customCards();
    }

    public static void sendFullTo(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        CustomCardStore store = storeGetter.apply(server);
        if (store == null) return;

        enqueueFullSync(player, store.all());
    }

    public static void tick(MinecraftServer server) {
        for (FullSyncJob job : FULL_SYNC_JOBS.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
            if (player == null) {
                FULL_SYNC_JOBS.remove(job.playerId, job);
                continue;
            }

            int sent = 0;
            while (sent < FULL_SYNC_CHUNKS_PER_TICK && job.nextChunk < job.totalChunks) {
                int chunkIndex = job.nextChunk++;
                int from = chunkIndex * FULL_SYNC_CHUNK_SIZE;
                int to = Math.min(job.entries.size(), from + FULL_SYNC_CHUNK_SIZE);
                List<WireMeta> chunk = from >= to ? List.of() : job.entries.subList(from, to);

                PacketDistributor.sendToPlayer(
                        player,
                        new CustomSyncFullChunk(job.syncId, chunkIndex, job.totalChunks, chunk)
                );
                sent++;
            }

            if (job.nextChunk >= job.totalChunks) {
                FULL_SYNC_JOBS.remove(job.playerId, job);
            }
        }
    }

    public static void broadcastDeltaAdd(MinecraftServer server, CardMeta meta) {
        if (server == null || meta == null) return;

        CustomSyncDelta payload = new CustomSyncDelta(toWire(meta));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void broadcastFull(MinecraftServer server, Collection<CardMeta> metas) {
        if (server == null) return;

        Collection<CardMeta> snapshot = metas == null ? List.of() : metas;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enqueueFullSync(player, snapshot);
        }
    }

    private static void enqueueFullSync(ServerPlayer player, java.util.Collection<CardMeta> metas) {
        List<WireMeta> list = toWire(metas);
        if (list.size() <= FULL_SYNC_CHUNK_SIZE) {
            PacketDistributor.sendToPlayer(player, new CustomSyncFull(list));
            return;
        }

        FULL_SYNC_JOBS.put(player.getUUID(), new FullSyncJob(player, list));
    }

    private static List<WireMeta> toWire(java.util.Collection<CardMeta> metas) {
        if (metas == null || metas.isEmpty()) return List.of();

        ArrayList<WireMeta> out = new ArrayList<>(metas.size());
        for (CardMeta meta : metas) {
            if (meta != null) {
                out.add(toWire(meta));
            }
        }
        return out;
    }

    private static WireMeta toWire(CardMeta meta) {
        return new WireMeta(
                nz(meta.id), nz(meta.name), nz(meta.manaCost), nz(meta.typeLine), nz(meta.rarity), nz(meta.set),
                nz(meta.oracleText), nz(meta.power), nz(meta.toughness), nz(meta.loyalty),
                meta.doubleFaced,
                nz(meta.backName), nz(meta.backTypeLine), nz(meta.backOracleText),
                nz(meta.backPower), nz(meta.backToughness), nz(meta.backLoyalty)
        );
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private CustomCardSync() {}
}
