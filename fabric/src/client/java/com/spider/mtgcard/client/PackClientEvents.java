package com.spider.mtgcard.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class PackClientEvents {
    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            UnpackHud.reset();
        });
    }
    private PackClientEvents() {}
}
