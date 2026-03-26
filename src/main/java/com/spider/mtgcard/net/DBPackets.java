package com.spider.mtgcard.net;

import com.spider.mtgcard.net.payload.SearchPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class DBPackets {

    private DBPackets() {}

    /** Call once during common init. */
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(SearchPayload.ID, SearchPayload.CODEC);
    }

    /** Call on server init. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SearchPayload.ID, (payload, context) -> {
            var server = context.server();
            var player = context.player();

            server.execute(() -> {
                if (player.containerMenu instanceof com.spider.mtgcard.db.CardDatabaseScreenHandler h) {
                    String q = payload.q() == null ? "" : payload.q();
                    h.setActiveQuery(q);
                    h.applySearch(q, payload.order(), payload.dir());
                }
            });
        });
    }
}
