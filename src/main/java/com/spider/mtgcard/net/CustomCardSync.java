// com/spider/mtgcard/net/CustomCardSync.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta;
import com.spider.mtgcard.net.CustomCardPackets.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomCardSync {
    private static final int FULL_SYNC_CHUNK_SIZE = 128;
    private static final int FULL_SYNC_CHUNKS_PER_TICK = 2;

    private static final Map<UUID, FullSyncJob> FULL_SYNC_JOBS = new ConcurrentHashMap<>();

    private static final class FullSyncJob {
        final UUID playerId;
        final UUID syncId = UUID.randomUUID();
        final List<WireMeta> entries;
        final int totalChunks;
        int nextChunk = 0;

        FullSyncJob(ServerPlayer player, List<WireMeta> entries) {
            this.playerId = player.getUUID();
            this.entries = entries;
            this.totalChunks = Math.max(1, (int) Math.ceil(entries.size() / (double) FULL_SYNC_CHUNK_SIZE));
        }
    }

    public static void initServerHooks(java.util.function.Function<MinecraftServer, CustomCardStore> storeGetter) {
        // 1) Send FULL snapshot on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var store = storeGetter.apply(server);
            if (store == null) return;

            enqueueFullSync(handler.player, store.all());
        });
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

                ServerPlayNetworking.send(player, new CustomSyncFullChunk(job.syncId, chunkIndex, job.totalChunks, chunk));
                sent++;
            }

            if (job.nextChunk >= job.totalChunks) {
                FULL_SYNC_JOBS.remove(job.playerId, job);
            }
        }
    }

    private static void enqueueFullSync(ServerPlayer player, java.util.Collection<CardMeta> metas) {
        List<WireMeta> list = toWire(metas);
        if (list.size() <= FULL_SYNC_CHUNK_SIZE) {
            ServerPlayNetworking.send(player, new CustomSyncFull(list));
            return;
        }

        FULL_SYNC_JOBS.put(player.getUUID(), new FullSyncJob(player, list));
    }

    public static void broadcastDeltaAdd(MinecraftServer server, CardMeta m) {
        List<WireMeta> one = new ArrayList<>(1);
        one.add(toWire(m));
        var payload = new CustomSyncDelta(one.get(0));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void broadcastFull(MinecraftServer server, java.util.Collection<CardMeta> metas) {
        if (server == null || metas == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enqueueFullSync(player, metas);
        }
    }

    private static List<WireMeta> toWire(java.util.Collection<CardMeta> metas) {
        var out = new ArrayList<WireMeta>(metas.size());
        for (var m : metas) out.add(toWire(m));
        return out;
    }
    private static WireMeta toWire(CardMeta m) {
        return new WireMeta(
                nz(m.id), nz(m.name), nz(m.manaCost), nz(m.typeLine), nz(m.rarity), nz(m.set),
                nz(m.oracleText), nz(m.power), nz(m.toughness), nz(m.loyalty),
                m.doubleFaced,
                nz(m.backName), nz(m.backTypeLine), nz(m.backOracleText), nz(m.backPower), nz(m.backToughness), nz(m.backLoyalty)
        );
    }

    public static CustomCardStore getStore(MinecraftServer server) {
        return WorldState.get(server).customCards();
    }

    // NEW overload: keep your existing initServerHooks(Function<...>) as-is.
// Add this no-arg version so Mtgcard.java can call initServerHooks() with no args.
    public static void initServerHooks() {
        initServerHooks(CustomCardSync::getStore);
    }

    private static String nz(String s) { return (s == null) ? "" : s; }

    private CustomCardSync() {}
}
