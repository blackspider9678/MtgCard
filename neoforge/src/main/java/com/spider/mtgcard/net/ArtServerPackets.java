package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.shared.CardArtCommon;
import com.spider.mtgcard.shared.MtgCardPaths;
import com.spider.mtgcard.util.ArtImageStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

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

    public static void handleRequest(ArtPackets.ArtRequest payload, ServerPlayer player, MinecraftServer server) {
        final String artKey = safeKey(payload.artKey());
        final String url = payload.url() == null ? "" : payload.url().trim();
            final String game = MtgCardPaths.sanitizeGameFolder(payload.game());

        if (artKey.isEmpty()) return;

        // Run disk/network IO off-thread
        CompletableFuture.runAsync(() -> {
            try {
                // 1) Serve from server disk cache first (world scoped)
                Path cached = (url.isEmpty() ? resolveServerCustomCachedFile(server, game, artKey, payload.setCode()) : resolveServerMainCachedFile(server, game, artKey, payload.fallbackKeys()));
                if (Files.exists(cached)) {
                    byte[] bytes = Files.readAllBytes(cached);
                    if (bytes.length > 0) {
                        String cachedExt = extensionOf(cached);
                        if ("webp".equals(cachedExt)) {
                            Mtgcard.LOGGER.info("[MTGCard] Art {} served from cache as .webp", artKey);
                        } else {
                            Mtgcard.LOGGER.info("[MTGCard] Art {} served from cache as .{}", artKey, cachedExt);
                        }
                        sendChunks(server, player, game, artKey, bytes);
                    }
                    return;
                }

                // 2) If URL is empty, this is a "world-art" request: do NOT download
                if (url.isEmpty()) {
                    System.out.println("[MTGCard] world-art missing on server for key=" + artKey);
                    return;
                }

                // 3) Download, prefer webp on disk, then send the stored bytes
                byte[] downloaded = download(url);
                ArtImageStorage.StorageDecision decision = ArtImageStorage.normalizeForStorage(downloaded, url);
                ArtImageStorage.StoredArt art = decision.art();
                if (art == null) {
                    Mtgcard.LOGGER.warn("[MTGCard] Art {} from {} could not be stored: {}", artKey, url, decision.note());
                        sendChunks(server, player, game, artKey, new byte[0]);
                    return;
                }

                if (decision.fellBackFromWebp()) {
                    Mtgcard.LOGGER.warn("[MTGCard] Art {} from {} fell back to .{} (source .{}): {}",
                            artKey, url, art.ext(), decision.sourceExt(), decision.note());
                } else {
                    Mtgcard.LOGGER.info("[MTGCard] Art {} from {} stored as .{}", artKey, url, art.ext());
                }

                ArtImageStorage.write(serverMainArtDir(server, game), artKey, art);
                sendChunks(server, player, game, artKey, art.bytes());

            } catch (Throwable t) {
                Mtgcard.LOGGER.warn("[MTGCard] Art request failed for {} from {}: {}", artKey, url, t.toString());
                    sendChunks(server, player, game, artKey, new byte[0]);
            }
        });
    }

    private static void sendChunks(MinecraftServer server, ServerPlayer player, String game, String artKey, byte[] bytes) {
        final int total = Math.max(1, (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final int off = i * CHUNK_SIZE;
            final int len = Math.min(CHUNK_SIZE, bytes.length - off);

            final byte[] part = java.util.Arrays.copyOfRange(bytes, off, off + len);

            server.execute(() -> {
                if (player.connection != null) {
                    PacketDistributor.sendToPlayer(player, new ArtPackets.ArtChunk(artKey, game, index, total, part));
                }
            });
        }
    }

    /** <world>/mtgcard/<game>/main_art */
    private static Path serverMainArtDir(MinecraftServer server, String game) {
        Path dir = MtgCardPaths.mainArtDir(server, game);
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    private static Path resolveServerMainCachedFile(MinecraftServer server, String game, String artKey, String fallbackKeys) {
        Path dir = serverMainArtDir(server, game);
        Path exact = resolveExactFile(dir, artKey);
        return exact != null ? exact : dir.resolve(artKey + ".webp");
    }

    private static Path resolveServerCustomCachedFile(MinecraftServer server, String game, String artKey, String setCode) {
        Path dir = MtgCardPaths.customArtDir(server, game, setCode);
        Path exact = resolveExactFile(dir, artKey);
        return exact != null ? exact : dir.resolve(artKey + ".webp");
    }

    private static Path resolveExactFile(Path dir, String artKey) {
        Path webp = dir.resolve(artKey + ".webp");
        if (Files.exists(webp)) return webp;

        Path png = dir.resolve(artKey + ".png");
        if (Files.exists(png)) return png;

        Path jpg = dir.resolve(artKey + ".jpg");
        if (Files.exists(jpg)) return jpg;

        Path jpeg = dir.resolve(artKey + ".jpeg");
        if (Files.exists(jpeg)) return jpeg;

        return null;
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
        return CardArtCommon.sanitizeArtKey(k);
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "unknown";
    }

    private ArtServerPackets() {}
}
