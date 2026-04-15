package com.spider.mtgcard.content.pack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class PackServerEvents {
    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            PackOpenManager.cancelAndRefund(server, player.getUUID());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;

            var refunds = PackRefundState.get(server).drain(player.getUUID());
            if (!refunds.isEmpty()) {
                for (var st : refunds) {
                    PackInventoryUtil.giveOrDrop(player, st);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your pack opening was cancelled and refunded."), true);
            }
        });
    }

    private PackServerEvents() {}
}
