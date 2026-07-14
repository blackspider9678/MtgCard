package com.spider.mtgcard.net;

import com.spider.mtgcard.db.CardDatabaseDebug;
import com.spider.mtgcard.net.payload.SearchPayload;
import com.spider.mtgcard.net.payload.CardDbGridActionPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class DBPackets {

    private DBPackets() {}

    /** Call once during common init. */
    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(SearchPayload.ID, SearchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDbGridActionPayload.ID, CardDbGridActionPayload.CODEC);
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

            CardDatabaseDebug.log("[CardDBDebug] server received grid packet player={} container={} slot={} action={} openMenu={}",
                    player == null ? "null" : player.getName().getString(),
                    payload.containerId(),
                    payload.slot(),
                    payload.action(),
                    player == null || player.containerMenu == null
                            ? "null"
                            : player.containerMenu.getClass().getName() + "#" + player.containerMenu.containerId);

            if (server == null || player == null) {
                CardDatabaseDebug.log("[CardDBDebug] server ignored grid packet because server/player was null");
                return;
            }
            server.execute(() -> {
                if (player.containerMenu instanceof com.spider.mtgcard.db.CardDatabaseScreenHandler h) {
                    CardDatabaseDebug.log("[CardDBDebug] server dispatching grid packet to CardDatabaseScreenHandler container={} slot={} action={}",
                            payload.containerId(), payload.slot(), payload.action());
                    h.handleClientGridAction(payload.containerId(), payload.slot(), payload.action(), player);
                } else {
                    CardDatabaseDebug.log("[CardDBDebug] server ignored grid packet because open menu was {}",
                            player.containerMenu == null ? "null" : player.containerMenu.getClass().getName());
                }
            });
        });
    }
}
