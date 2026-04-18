package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PackOpenManager {

    public static final class Active {
        public final UUID playerId;
        public final String packUid;
        public final ItemStack refundPackOne; // exact pack (count=1) to refund if cancelled
        public final int preferredReturnSlot;
        public volatile boolean cancelled = false;

        Active(UUID playerId, String packUid, ItemStack refundPackOne, int preferredReturnSlot) {
            this.playerId = playerId;
            this.packUid = packUid == null || packUid.isBlank() ? "missing" : packUid;
            this.refundPackOne = refundPackOne;
            this.preferredReturnSlot = preferredReturnSlot;
        }
    }

    private static final ConcurrentHashMap<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    /** Returns true if started, false if player already has an active opening. */
    public static boolean tryStart(ServerPlayer player, String packUid, ItemStack refundPackOne, int preferredReturnSlot) {
        UUID id = player.getUUID();
        Active a = new Active(id, packUid, refundPackOne, preferredReturnSlot);
        Active existing = ACTIVE.putIfAbsent(id, a);
        if (existing != null) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Active open already exists for player={} existingUid={} requestedUid={} preferredSlot={} activeCount={}",
                    player.getName().getString(),
                    existing.packUid,
                    a.packUid,
                    preferredReturnSlot,
                    ACTIVE.size()
            );
            return false;
        }

        Mtgcard.LOGGER.info(
                "[MTGCard][PackDebug] Registered active pack open player={} uid={} preferredSlot={} refund={} activeCount={}",
                player.getName().getString(),
                a.packUid,
                preferredReturnSlot,
                PackInventoryUtil.describeStack(refundPackOne),
                ACTIVE.size()
        );
        return true;
    }

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static Active get(ServerPlayer player) {
        return ACTIVE.get(player.getUUID());
    }

    /** Mark finished successfully. */
    public static void finish(ServerPlayer player) {
        Active removed = ACTIVE.remove(player.getUUID());
        Mtgcard.LOGGER.info(
                "[MTGCard][PackDebug] Finished active pack open player={} uid={} cancelled={} activeCount={}",
                player.getName().getString(),
                removed == null ? "missing" : removed.packUid,
                removed != null && removed.cancelled,
                ACTIVE.size()
        );
    }

    /**
     * Cancel (if active) and record a refund for this world/server.
     * Call this on disconnect.
     */
    public static void cancelAndRefund(MinecraftServer server, UUID playerId) {
        Active a = ACTIVE.remove(playerId);
        if (a == null) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Disconnect refund skipped for playerId={} because no active pack was registered",
                    playerId
            );
            return;
        }

        a.cancelled = true;

        // Save refund into persistent world state so it returns on rejoin of SAME world.
        PackRefundState.get(server).addRefund(playerId, a.refundPackOne);
        Mtgcard.LOGGER.info(
                "[MTGCard][PackDebug] Queued disconnect refund playerId={} uid={} preferredSlot={} refund={} activeCount={}",
                playerId,
                a.packUid,
                a.preferredReturnSlot,
                PackInventoryUtil.describeStack(a.refundPackOne),
                ACTIVE.size()
        );
    }

    private PackOpenManager() {}
}
