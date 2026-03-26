package com.spider.mtgcard.content.pack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PackOpenManager {

    public static final class Active {
        public final UUID playerId;
        public final ItemStack refundPackOne; // exact pack (count=1) to refund if cancelled
        public volatile boolean cancelled = false;

        Active(UUID playerId, ItemStack refundPackOne) {
            this.playerId = playerId;
            this.refundPackOne = refundPackOne;
        }
    }

    private static final ConcurrentHashMap<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    /** Returns true if started, false if player already has an active opening. */
    public static boolean tryStart(ServerPlayer player, ItemStack refundPackOne) {
        UUID id = player.getUUID();
        Active a = new Active(id, refundPackOne);
        return ACTIVE.putIfAbsent(id, a) == null;
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static Active get(ServerPlayer player) {
        return ACTIVE.get(player.getUUID());
    }

    /** Mark finished successfully. */
    public static void finish(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    /**
     * Cancel (if active) and record a refund for this world/server.
     * Call this on disconnect.
     */
    public static void cancelAndRefund(MinecraftServer server, UUID playerId) {
        Active a = ACTIVE.remove(playerId);
        if (a == null) return;

        a.cancelled = true;

        // Save refund into persistent world state so it returns on rejoin of SAME world.
        PackRefundState.get(server).addRefund(playerId, a.refundPackOne);
    }

    private PackOpenManager() {}
}
