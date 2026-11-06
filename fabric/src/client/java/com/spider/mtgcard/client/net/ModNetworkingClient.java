package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.UnpackHud;
import com.spider.mtgcard.client.command.DeckClientIO;
import com.spider.mtgcard.net.payload.UnbundleProgressPayload;
import com.spider.mtgcard.net.payload.DeckPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-only networking receivers. */
public final class ModNetworkingClient {

    /** Call from your ClientModInitializer. */
    public static void initClient() {

        // -------------------------
        // Pack opening HUD
        // -------------------------
        ClientPlayNetworking.registerGlobalReceiver(UnbundleProgressPayload.ID, (payload, ctx) -> {
            ctx.client().execute(() ->
                    UnpackHud.setProgressFromServer(payload.percent())
            );
        });

        // -------------------------
        // Deck export (server -> client)
        // -------------------------
        ClientPlayNetworking.registerGlobalReceiver(
                DeckPayloads.DeckExportRequestS2C.ID,
                (payload, ctx) -> {
                    ctx.client().execute(() ->
                            DeckClientIO.handleExport(payload.name())
                    );
                }
        );

        // -------------------------
        // Deck list (server -> client)
        // -------------------------
        ClientPlayNetworking.registerGlobalReceiver(
                DeckPayloads.DeckListRequestS2C.ID,
                (payload, ctx) -> {
                    ctx.client().execute(DeckClientIO::handleList);
                }
        );
    }

    private ModNetworkingClient() {}
}
