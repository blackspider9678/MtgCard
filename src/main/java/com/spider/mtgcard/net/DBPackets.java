package com.spider.mtgcard.net;

import com.spider.mtgcard.net.payload.SearchPayload;
import com.spider.mtgcard.net.payload.CardDbGridActionPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class DBPackets {

    private DBPackets() {}

    /** Call once during common init. */
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(SearchPayload.ID, SearchPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CardDbGridActionPayload.ID, CardDbGridActionPayload.CODEC);
    }

    /** Call on server init. */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SearchPayload.ID, (payload, context) -> {
            var server = context.server();
            var player = context.player();

            server.execute(() -> {
                if (player.containerMenu instanceof com.spider.mtgcard.db.CardDatabaseScreenHandler h) {
                    String q = payload.q() == null ? "" : payload.q();
                    if (payload.rememberSort()) {
                        h.rememberSortPreference(payload.order(), payload.dir(), true);
                    }
                    h.setActiveQuery(q);
                    h.applySearch(q, payload.order(), payload.dir());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDbGridActionPayload.ID, (payload, context) -> {
            var server = context.server();
            var player = context.player();

            server.execute(() -> {
                if (player.containerMenu instanceof com.spider.mtgcard.db.CardDatabaseScreenHandler h) {
                    h.handleClientGridAction(payload.containerId(), payload.slot(), payload.action(), player);
                }
            });
        });
    }
}
