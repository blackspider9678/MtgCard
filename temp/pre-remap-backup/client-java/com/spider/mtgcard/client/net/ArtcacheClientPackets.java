package com.spider.mtgcard.client.net;

import com.spider.mtgcard.net.ArtcachePackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import static net.minecraft.world.level.storage.LevelResource.*;

public final class ArtcacheClientPackets {

    /** Call from your ClientModInitializer. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ArtcachePackets.ArtcacheRequest.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    // Avoid hard-linking if your CardArtManager package is in flux:
                    try {
                        Class<?> mgr = Class.forName("com.spider.mtgcard.client.java.CardArtManager");
                        switch (payload.action()) {
                            case STATS -> mgr.getMethod("printStatsToChat").invoke(null);
                            case PURGE -> mgr.getMethod("purgeCache").invoke(null);
                            case REBUILD -> mgr.getMethod("rebuildCache").invoke(null);
                        }
                    } catch (Throwable t) {
                        // If methods/class names differ, you can swap to your actual manager class/methods.
                        // Intentionally swallow so the client doesn't crash from a command.
                    }
                })
        );
    }

    private ArtcacheClientPackets() {}
}
