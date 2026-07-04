package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.ImportPerms;
import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomCardServer {
    private static final int MAX_PENDING_ENTRIES_PER_PLAYER = 50_000;
    private static final int MAX_ENTRIES_PER_PLAYER_TICK = 64;
    private static final int MAX_ENTRIES_TOTAL_TICK = 256;
    private static final int COMPLETE_AFTER_IDLE_TICKS = 20;
    private static final long FEEDBACK_INTERVAL_MS = 2_000L;

    private static final Map<UUID, ImportJob> JOBS = new ConcurrentHashMap<>();

    private static final class ImportJob {
        final MinecraftServer server;
        final CustomCardStore store;
        final UUID playerId;
        final String playerName;
        final ArrayDeque<CustomCardPackets.BatchEntry> pending = new ArrayDeque<>();
        final long startedNanos = System.nanoTime();

        int accepted;
        int processed;
        int added;
        int failed;
        int idleTicks;
        long lastFeedbackMs;
        boolean completing;

        ImportJob(MinecraftServer server, CustomCardStore store, ServerPlayer player) {
            this.server = server;
            this.store = store;
            this.playerId = player.getUUID();
            this.playerName = player.getName().getString();
        }
    }

    /** Handles the C2S batch create payload. */
    public static void handleBatch(CustomCardPackets.CustomBatchCreate payload, ServerPlayer who) {
        if (payload == null || who == null) return;
        if (!ImportPerms.canImport(who)) {
            who.sendSystemMessage(Component.literal("You do not have permission to import custom cards."));
            return;
        }

        MinecraftServer server = who.level().getServer();
        if (server == null) return;

        var state = WorldState.get(server);
        if (state == null) return;

        CustomCardStore store = state.customCards();
        if (store == null) return;

        var entries = payload.entries();
        if (entries == null || entries.isEmpty()) return;

        UUID playerId = who.getUUID();
        ImportJob job = JOBS.get(playerId);
        if (job == null || job.server != server || job.completing) {
            job = new ImportJob(server, store, who);
            JOBS.put(playerId, job);
            Mtgcard.LOGGER.info("[MTGCard] Custom card import started for {} ({})", job.playerName, playerId);
            who.sendSystemMessage(Component.literal("[MTGCard] Custom card import queued. Processing in the background."));
        }

        int accepted = 0;
        int dropped = 0;
        for (var entry : payload.entries()) {
            if (entry == null) continue;
            if (job.pending.size() >= MAX_PENDING_ENTRIES_PER_PLAYER) {
                dropped++;
                continue;
            }
            job.pending.addLast(entry);
            job.accepted++;
            accepted++;
        }

        job.idleTicks = 0;
        if (accepted > 0) {
            sendProgressMaybe(job, false);
        }
        if (dropped > 0) {
            who.sendSystemMessage(Component.literal("[MTGCard] Import queue is full; dropped " + dropped + " custom cards."));
            Mtgcard.LOGGER.warn("[MTGCard] Custom card import for {} dropped {} entries because the queue is full",
                    job.playerName, dropped);
        }
    }

    public static void tick(MinecraftServer server) {
        int totalBudget = MAX_ENTRIES_TOTAL_TICK;
        for (ImportJob job : JOBS.values()) {
            if (totalBudget <= 0) break;
            if (job.server != server || job.completing) continue;

            int playerBudget = Math.min(MAX_ENTRIES_PER_PLAYER_TICK, totalBudget);
            int processedThisTick = 0;

            while (playerBudget > 0 && !job.pending.isEmpty()) {
                CustomCardPackets.BatchEntry entry = job.pending.pollFirst();
                job.processed++;
                processedThisTick++;
                playerBudget--;
                totalBudget--;

                String id = job.store.addFromClient(entry, null);
                if (id == null) {
                    job.failed++;
                } else {
                    job.added++;
                }
            }

            if (processedThisTick > 0) {
                job.idleTicks = 0;
                sendProgressMaybe(job, false);
                continue;
            }

            if (!job.pending.isEmpty()) {
                job.idleTicks = 0;
                continue;
            }

            job.idleTicks++;
            if (job.idleTicks >= COMPLETE_AFTER_IDLE_TICKS) {
                completeJob(job);
            }
        }
    }

    private static void completeJob(ImportJob job) {
        if (job.completing) return;
        job.completing = true;
        JOBS.remove(job.playerId, job);

        long processMs = (System.nanoTime() - job.startedNanos) / 1_000_000L;
        Mtgcard.LOGGER.info("[MTGCard] Custom card import finished processing for {}: accepted={}, added={}, failed={}, processMs={}",
                job.playerName, job.accepted, job.added, job.failed, processMs);

        job.store.saveIfDirtyAsync().whenComplete((saved, error) -> job.server.execute(() -> {
            ServerPlayer player = job.server.getPlayerList().getPlayer(job.playerId);
            if (error != null) {
                Mtgcard.LOGGER.error("[MTGCard] Custom card import save failed for {} after {} ms",
                        job.playerName, processMs, error);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("[MTGCard] Custom card import failed while saving. Check server logs."));
                }
                return;
            }

            long totalMs = (System.nanoTime() - job.startedNanos) / 1_000_000L;
            Mtgcard.LOGGER.info("[MTGCard] Custom card import complete for {}: accepted={}, added={}, failed={}, saved={}, totalMs={}",
                    job.playerName, job.accepted, job.added, job.failed, saved, totalMs);
            if (player != null) {
                player.sendSystemMessage(Component.literal("[MTGCard] Custom card import complete: "
                        + job.added + " added, " + job.failed + " skipped."));
            }
        }));
    }

    private static void sendProgressMaybe(ImportJob job, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - job.lastFeedbackMs < FEEDBACK_INTERVAL_MS) return;
        job.lastFeedbackMs = now;

        ServerPlayer player = job.server.getPlayerList().getPlayer(job.playerId);
        if (player == null) return;

        player.sendSystemMessage(Component.literal("[MTGCard] Importing custom cards: "
                + job.processed + "/" + job.accepted + " processed, " + job.pending.size() + " queued."));
    }

    private static Path resolveArtPath(Path artDir, String key) {
        if (key == null || key.isEmpty()) return null;

        // try known possibilities
        Path p = artDir.resolve(key + ".webp");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".png");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".jpg");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".jpeg");
        if (Files.exists(p)) return p;

        // last resort: raw key if you ever stored full filename
        p = artDir.resolve(key);
        if (Files.exists(p)) return p;

        return null;
    }


    private CustomCardServer() {}
}
