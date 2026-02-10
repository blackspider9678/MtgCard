package com.spider.mtgcard.client.cardstore;

import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.client.gui.CardStoreScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class CardStoreClientPackets {

    public static void registerClient() {

        ClientPlayNetworking.registerGlobalReceiver(CardStorePackets.SearchPrintsStartS2C.ID, (payload, context) -> {
            context.client().execute(() -> {
                var screen = context.client().currentScreen;
                if (screen instanceof CardStoreScreen cs) cs.onPrintsStart(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CardStorePackets.SearchPrintsAddS2C.ID, (payload, context) -> {
            context.client().execute(() -> {
                var screen = context.client().currentScreen;
                if (screen instanceof CardStoreScreen cs) cs.onPrintsAdd(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(CardStorePackets.SearchPrintsDoneS2C.ID, (payload, context) -> {
            context.client().execute(() -> {
                var screen = context.client().currentScreen;
                if (screen instanceof CardStoreScreen cs) cs.onPrintsDone(payload);
            });
        });

        // (If you have a confirm/checkout result packet, register it here too.)
    }

    private CardStoreClientPackets() {}
}
