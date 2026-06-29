package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.net.ModPayloads;
import net.minecraft.server.level.ServerPlayer;

public final class NeoForgePackServerEvents {
    public static void onPlayerLoggedIn(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return;

        var refunds = PackRefundState.get(server).drain(player.getUUID());
        if (refunds.isEmpty()) return;

        for (var stack : refunds) {
            PackInventoryUtil.giveOrDrop(
                    player,
                    stack,
                    -1,
                    "queued_refund_rejoin uid=" + PackInventoryUtil.readPackUid(stack)
            );
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) return;

        ModPayloads.clearUnpackProgress(player);
        PackOpenManager.cancelAndRefund(server, player.getUUID());
    }

    private NeoForgePackServerEvents() {}
}
