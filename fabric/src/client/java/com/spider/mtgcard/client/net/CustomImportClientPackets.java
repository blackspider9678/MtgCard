package com.spider.mtgcard.client.net;

import com.spider.mtgcard.net.CustomImportPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CustomImportClientPackets {
    private CustomImportClientPackets() {}

    /** Call during client init. */
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CustomImportPackets.OpenImportGui.ID, (payload, ctx) ->
                ctx.client().execute(() ->
                        com.spider.mtgcard.client.gui.CustomImportScreen.open()
                )
        );
    }
}
