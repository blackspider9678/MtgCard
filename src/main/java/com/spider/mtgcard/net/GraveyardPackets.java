package com.spider.mtgcard.net;

import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.net.payload.GraveyardActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;

public final class GraveyardPackets {

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(GraveyardActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();

            context.server().execute(() -> {
                // 1) basic "is this the screen they actually have open?" validation
                AbstractContainerMenu sh = player.containerMenu;
                if (sh == null || sh.containerId != payload.syncId()) return;

                // 2) grab BE at the position
                var world = player.level();
                var be = world.getBlockEntity(payload.pos());
                if (!(be instanceof GraveyardBlockEntity gy)) return;

                // Optional: distance sanity
                if (!gy.getBlockPos().closerThan(player.blockPosition(), 8.0)) return;

                // 3) perform action
                switch (payload.action()) {
                    case EXILE_ALL -> gy.exileAll();
                    case RETURN_ALL -> gy.returnAll();
                }
            });
        });
    }

    private GraveyardPackets() {}
}
