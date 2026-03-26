package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomCardServer {

    /** Handles the C2S batch create payload. */
    public static void handleBatch(CustomCardPackets.CustomBatchCreate payload, ServerPlayer who) {
        if (payload == null || who == null) return;

        MinecraftServer server = who.level().getServer();
        if (server == null) return;

        var state = WorldState.get(server);
        if (state == null) return;

        CustomCardStore store = state.customCards();
        if (store == null) return;

        for (var entry : payload.entries()) {
            if (entry == null) continue;
            store.addFromClient(entry, who);
        }

        store.saveIfDirty(); // ✅ ONE write instead of 2146 writes
    }

    private static Path resolveArtPath(Path artDir, String key) {
        if (key == null || key.isEmpty()) return null;

        // try known possibilities
        Path p = artDir.resolve(key + ".webp");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".png");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".jpg");
        if (Files.exists(p)) return p;

        p = artDir.resolve(key + ".jpeg");
        if (Files.exists(p)) return p;

        // last resort: raw key if you ever stored full filename
        p = artDir.resolve(key);
        if (Files.exists(p)) return p;

        return null;
    }


    private CustomCardServer() {}
}