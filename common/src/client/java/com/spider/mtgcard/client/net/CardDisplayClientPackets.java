package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.CardLargeViewScreen;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class CardDisplayClientPackets {

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
                com.spider.mtgcard.net.payload.CardDisplayPayloads.OpenDisplayViewS2C.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc == null) return;

                    // handSlot is irrelevant for display mode; pass -1
                    mc.gui.setScreen(new com.spider.mtgcard.client.CardLargeViewScreen(
                            payload.stack(),
                            -1,
                            payload.entityId()
                    ));
                })
        );
    }

    private CardDisplayClientPackets() {}
}
