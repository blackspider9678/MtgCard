package com.spider.mtgcard.content.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PackProgressBars {
    private static final int COMPLETE_CLEAR_DELAY_TICKS = 20;
    private static final Map<UUID, Entry> ACTIVE = new ConcurrentHashMap<>();

    public static void update(ServerPlayer player, int percent) {
        if (player == null) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        int clamped = Math.max(0, Math.min(100, percent));
        Runnable task = () -> updateOnServer(player, clamped, server.getTickCount());
        if (server.isSameThread()) task.run();
        else server.execute(task);
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        Runnable task = () -> remove(player.getUUID(), player);
        if (server.isSameThread()) task.run();
        else server.execute(task);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        int now = server.getTickCount();
        var toRemove = new ArrayList<UUID>();
        ACTIVE.forEach((playerId, entry) -> {
            if (entry.clearAtTick >= 0 && now >= entry.clearAtTick) {
                toRemove.add(playerId);
            }
        });

        for (UUID playerId : toRemove) {
            remove(playerId, server.getPlayerList().getPlayer(playerId));
        }
    }

    private static void updateOnServer(ServerPlayer player, int percent, int currentTick) {
        Entry entry = ACTIVE.computeIfAbsent(player.getUUID(), id -> createEntry(player));
        entry.bar.addPlayer(player);
        entry.bar.setVisible(true);
        entry.bar.setName(title(percent));
        entry.bar.setProgress(percent / 100.0f);
        entry.clearAtTick = percent >= 100 ? currentTick + COMPLETE_CLEAR_DELAY_TICKS : -1;
    }

    private static Entry createEntry(ServerPlayer player) {
        ServerBossEvent bar = new ServerBossEvent(
                title(0),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setVisible(true);
        bar.addPlayer(player);
        return new Entry(bar);
    }

    private static void remove(UUID playerId, ServerPlayer player) {
        Entry entry = ACTIVE.remove(playerId);
        if (entry == null) return;

        if (player != null) {
            entry.bar.removePlayer(player);
        } else {
            entry.bar.removeAllPlayers();
        }
        entry.bar.setVisible(false);
    }

    private static Component title(int percent) {
        return Component.literal("Unwrapping pack: " + percent + "%");
    }

    private static final class Entry {
        private final ServerBossEvent bar;
        private int clearAtTick = -1;

        private Entry(ServerBossEvent bar) {
            this.bar = bar;
        }
    }

    private PackProgressBars() {}
}
