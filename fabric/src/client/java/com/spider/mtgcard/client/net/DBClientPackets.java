package com.spider.mtgcard.client.net;

import com.spider.mtgcard.net.payload.SearchPayload;
import com.spider.mtgcard.net.payload.CardDbGridActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;

public final class DBClientPackets {
    private DBClientPackets() {}

    public static void sendSearch(String q, String order, String dir, boolean rememberSort) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;

        ClientPlayNetworking.send(new SearchPayload(
                q == null ? "" : q,
                order == null ? "name" : order,
                dir == null ? "asc" : dir,
                rememberSort
        ));
    }

    public static boolean sendGridAction(int containerId, int slot, int action) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return false;
        if (!ClientPlayNetworking.canSend(CardDbGridActionPayload.ID)) return false;

        ClientPlayNetworking.send(new CardDbGridActionPayload(containerId, slot, action));
        return true;
    }

    public static void registerClientReceivers() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                com.spider.mtgcard.net.payload.DeckboxTabNamesPayload.ID,
                (payload, ctx) -> {
                    ctx.client().execute(() -> {
                        var screen = ctx.client().gui.screen();
                        if (screen instanceof com.spider.mtgcard.client.gui.CardDatabaseScreen db) {
                            db.applyDeckboxTabNames(payload.syncId(), payload.tabNames());
                        }
                    });
                }
        );
    }
}
