package com.spider.mtgcard.net;

import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.net.payload.GraveyardActionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GraveyardPackets {

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(GraveyardActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            context.server().execute(() -> {
                // 1) basic "is this the screen they actually have open?" validation
                ScreenHandler sh = player.currentScreenHandler;
                if (sh == null || sh.syncId != payload.syncId()) return;

                // 2) grab BE at the position
                var world = player.getEntityWorld();
                var be = world.getBlockEntity(payload.pos());
                if (!(be instanceof GraveyardBlockEntity gy)) return;

                // Optional: distance sanity
                if (!gy.getPos().isWithinDistance(player.getBlockPos(), 8.0)) return;

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
