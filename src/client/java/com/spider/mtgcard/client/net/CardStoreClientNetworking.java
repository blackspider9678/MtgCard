package com.spider.mtgcard.client.net;

import com.spider.mtgcard.cardstore.CardStorePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CardStoreClientNetworking {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                CardStorePackets.ImportDeckS2C.ID,
                (payload, ctx) -> {
                    ctx.client().execute(() -> {
                        if (ctx.client().currentScreen instanceof com.spider.mtgcard.client.gui.CardStoreScreen screen) {
                            screen.onImportDeckResult(payload);
                        }
                    });
                }
        );
    }
}
