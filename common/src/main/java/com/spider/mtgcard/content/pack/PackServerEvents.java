package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.MtgcardConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class PackServerEvents {
    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            if (MtgcardConfig.packDebugEnabled()) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Player disconnect observed for pack flow player={} inventory={}",
                        player.getName().getString(),
                        PackInventoryUtil.describeInventoryState(player, player.getInventory().getSelectedSlot())
                );
            }
            PackOpenManager.cancelAndRefund(server, player.getUUID());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;

            var refunds = PackRefundState.get(server).drain(player.getUUID());
            boolean packDebug = MtgcardConfig.packDebugEnabled();
            if (packDebug) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Player join refund check player={} queuedRefundCount={} inventoryBefore={}",
                        player.getName().getString(),
                        refunds.size(),
                        PackInventoryUtil.describeInventoryState(player, player.getInventory().getSelectedSlot())
                );
            }
            if (!refunds.isEmpty()) {
                for (var st : refunds) {
                    PackInventoryUtil.DeliveryResult result = PackInventoryUtil.giveOrDrop(
                            player,
                            st,
                            -1,
                            "queued_refund_rejoin uid=" + PackInventoryUtil.readPackUid(st)
                    );
                    if (packDebug) {
                        Mtgcard.LOGGER.info(
                                "[MTGCard][PackDebug] Rejoin refund processed player={} uid={} mode={} refund={} inventoryAfter={}",
                                player.getName().getString(),
                                PackInventoryUtil.readPackUid(st),
                                result.mode(),
                                PackInventoryUtil.describeStack(st),
                                PackInventoryUtil.describeInventoryState(player, player.getInventory().getSelectedSlot())
                        );
                    }
                }
            }
        });
    }

    private PackServerEvents() {}
}
