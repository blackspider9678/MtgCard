package com.spider.mtgcard.content.pack;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public final class PackServerEvents {
    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            PackOpenManager.cancelAndRefund(server, player.getUuid());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;

            var refunds = PackRefundState.get(server).drain(player.getUuid());
            if (!refunds.isEmpty()) {
                for (var st : refunds) {
                    if (!player.getInventory().insertStack(st)) {
                        player.dropItem(st, false);
                    }
                }
                player.sendMessage(net.minecraft.text.Text.literal("Your pack opening was cancelled and refunded."), true);
            }
        });
    }

    private PackServerEvents() {}
}
