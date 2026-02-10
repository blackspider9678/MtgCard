package com.spider.mtgcard.client.guidebook;

import com.spider.mtgcard.net.GuideBookPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class GuideBookClientNet {

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GuideBookPackets.OpenPayload.ID, (payload, context) -> {
            context.client().execute(() -> GuideBookClient.open(null));
        });
    }

    private GuideBookClientNet() {}
}
