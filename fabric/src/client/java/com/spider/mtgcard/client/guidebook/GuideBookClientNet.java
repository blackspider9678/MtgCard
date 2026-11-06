package com.spider.mtgcard.client.guidebook;

import com.spider.mtgcard.net.GuideBookPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GuideBookClientNet {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GuideBookPackets.OpenPayload.ID, (payload, context) -> {
            context.client().execute(() -> GuideBookClient.open(null));
        });
        ClientPlayNetworking.registerGlobalReceiver(GuideBookPackets.ConfigSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof GuideBookScreen screen) {
                        screen.receiveServerConfig(payload.json(), payload.canEdit(), payload.message());
                    }
                }));
    }

    private GuideBookClientNet() {}
}
