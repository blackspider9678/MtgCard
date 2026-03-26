package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ArtServerPackets {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Keep chunks comfortably under the ~2MiB cap.
    // 240KB is very safe (headers + other overhead still far below limit).
    private static final int CHUNK_SIZE = 240 * 1024;

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ArtPackets.ArtRequest.ID, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = ctx.server();

            final String artKey = safeKey(payload.artKey());
            final String url = payload.url() == null ? "" : payload.url().trim();

            if (artKey.isEmpty()) return;

            // Run disk/network IO off-thread
            CompletableFuture.runAsync(() -> {
                try {
                    // 1) Serve from server disk cache first (world scoped)
                    Path cached = resolveServerCachedFile(server, artKey);
                    if (Files.exists(cached)) {
                        byte[] bytes = Files.readAllBytes(cached);
                        if (bytes.length > 0) {
                            sendChunks(server, player, artKey, bytes);
                        }
                        return;
                    }

                    // 2) If URL is empty, this is a "world-art" request: do NOT download
                    if (url.isEmpty()) {
                        System.out.println("[MTGCard] world-art missing on server for key=" + artKey);
                        return;
                    }

                    // 3) Download, size-check, write to disk cache, then send
                    byte[] png = download(url);
                    if (png == null || png.length == 0) return;

                    // write to world cache as .png (IMPORTANT: these are PNG bytes)
                    Path out = serverCacheDir(server).resolve(artKey + ".png");
                    Files.createDirectories(out.getParent());
                    Files.write(out, png, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                    sendChunks(server, player, artKey, png);

                } catch (Throwable ignored) {
                }
            });
        });
    }

    private static void sendChunks(MinecraftServer server, ServerPlayer player, String artKey, byte[] bytes) {
        final int total = (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;

        for (int i = 0; i < total; i++) {
            final int index = i;
            final int off = i * CHUNK_SIZE;
            final int len = Math.min(CHUNK_SIZE, bytes.length - off);

            final byte[] part = java.util.Arrays.copyOfRange(bytes, off, off + len);

            server.execute(() -> {
                if (player.connection != null) {
                    ServerPlayNetworking.send(player, new ArtPackets.ArtChunk(artKey, index, total, part));
                }
            });
        }
    }

    /** <world>/mtgcard/art */
    private static Path serverCacheDir(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path dir = root.resolve("mtgcard").resolve("art");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    /** Prefer .png; keep .webp only if you truly generate webp bytes later. */
    private static Path resolveServerCachedFile(MinecraftServer server, String artKey) {
        Path dir = serverCacheDir(server);

        Path webp = dir.resolve(artKey + ".webp");
        if (Files.exists(webp)) return webp;

        Path png = dir.resolve(artKey + ".png");
        if (Files.exists(png)) return png;

        Path jpg = dir.resolve(artKey + ".jpg");
        if (Files.exists(jpg)) return jpg;

        Path jpeg = dir.resolve(artKey + ".jpeg");
        if (Files.exists(jpeg)) return jpeg;

        return png;
    }

    private static byte[] download(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .header("User-Agent", "mtgcard-art/1.0")
                .build();

        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) return null;

        byte[] bytes = resp.body();
        if (bytes == null) return null;

        // hard cap: 10MB (download protection)
        if (bytes.length > 10 * 1024 * 1024) return null;

        return bytes;
    }

    /** Avoid path traversal / weird filenames. */
    private static String safeKey(String k) {
        if (k == null) return "";
        String s = k.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9_\\-\\.]", "");
        return s;
    }

    private ArtServerPackets() {}
}
