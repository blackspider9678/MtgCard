package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.gui.DeckControlScreen;
import com.spider.mtgcard.deckcontrol.DeckControlPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public final class DeckControlClientNetworking {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DeckControlPackets.OverlayS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    var client = MinecraftClient.getInstance();
                    if (client.currentScreen instanceof DeckControlScreen screen) {
                        screen.onOverlayPayload(payload);
                    }
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(DeckControlPackets.CascadeS2C.ID, (payload, ctx) -> {
            ctx.client().execute(() -> {
                if (ctx.client().currentScreen instanceof com.spider.mtgcard.client.gui.DeckControlScreen sc) {
                    sc.onCascadePayload(payload);
                }
            });
        });
    }

    private DeckControlClientNetworking() {}
}
