package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.shared.CardArtCommon;
import com.spider.mtgcard.shared.MtgCardPaths;
import com.spider.mtgcard.util.ArtImageStorage;
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
            final String fallbackKeys = payload.fallbackKeys() == null ? "" : payload.fallbackKeys();
            final String setCode = payload.setCode() == null ? "" : payload.setCode().trim();
            final String game = MtgCardPaths.sanitizeGameFolder(payload.game());

            if (artKey.isEmpty()) return;

            // Run disk/network IO off-thread
            CompletableFuture.runAsync(() -> {
                try {
                    // 1) Serve from server disk cache first (world scoped)
                    Path cached = url.isEmpty()
                            ? resolveServerCustomCachedFile(server, game, artKey, setCode)
                            : resolveServerMainCachedFile(server, game, artKey, fallbackKeys);
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
                }
            });
        });
    }

    private static void sendChunks(MinecraftServer server, ServerPlayer player, String game, String artKey, byte[] bytes) {
        final int total = (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        final String safeGame = MtgCardPaths.sanitizeGameFolder(game);

        for (int i = 0; i < total; i++) {
            final int index = i;
            final int off = i * CHUNK_SIZE;
            final int len = Math.min(CHUNK_SIZE, bytes.length - off);

            final byte[] part = java.util.Arrays.copyOfRange(bytes, off, off + len);

            server.execute(() -> {
                if (player.connection != null) {
                    ServerPlayNetworking.send(player, new ArtPackets.ArtChunk(artKey, safeGame, index, total, part));
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
        Path mainDir = serverMainArtDir(server, game);
        Path legacyDir = MtgCardPaths.legacyArtDir(server);

        for (String candidate : artKeyCandidates(artKey, fallbackKeys)) {
            Path exact = resolveExactFile(mainDir, candidate);
            if (exact != null) return exact;
        }

        for (String candidate : artKeyCandidates(artKey, fallbackKeys)) {
            Path legacy = resolveExactFile(legacyDir, candidate);
            if (legacy != null) return migrateLegacyArt(mainDir, artKey, legacy);
        }

        return mainDir.resolve(artKey + ".webp");
    }

    private static Path resolveServerCustomCachedFile(MinecraftServer server, String game, String artKey, String setCode) {
        Path setDir = MtgCardPaths.customArtDir(server, game, setCode);
        Path exact = resolveExactFile(setDir, artKey);
        if (exact != null) return exact;

        Path customRoot = MtgCardPaths.customArtRoot(server, game);
        try (var dirs = Files.list(customRoot)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir) || dir.equals(setDir)) continue;
                exact = resolveExactFile(dir, artKey);
                if (exact != null) return exact;
            }
        } catch (Exception ignored) {
        }

        Path legacy = resolveExactFile(MtgCardPaths.legacyArtDir(server), artKey);
        if (legacy != null) return migrateLegacyArt(setDir, artKey, legacy);

        return setDir.resolve(artKey + ".webp");
    }

    private static Path migrateLegacyArt(Path targetDir, String artKey, Path legacyFile) {
        try {
            Files.createDirectories(targetDir);
            String ext = extensionOf(legacyFile);
            Path target = targetDir.resolve(artKey + "." + ext);
            if (!Files.exists(target)) {
                Files.copy(legacyFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception ignored) {
            return legacyFile;
        }
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

    private static Iterable<String> artKeyCandidates(String artKey, String fallbackKeys) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();

        addArtKeyCandidate(keys, artKey);

        for (String fallback : CardArtCommon.decodeFallbackKeys(fallbackKeys)) {
            addArtKeyCandidate(keys, fallback);
        }

        java.util.LinkedHashSet<String> expanded = new java.util.LinkedHashSet<>(keys);
        for (String key : expanded) {
            String lower = key.trim().toLowerCase(Locale.ROOT);
            addArtKeyCandidate(keys, lower);

            if (lower.startsWith("custom_")) {
                addArtKeyCandidate(keys, lower.substring("custom_".length()));
            } else {
                addArtKeyCandidate(keys, "custom_" + lower);
            }
        }

        return keys;
    }

    private static void addArtKeyCandidate(java.util.Set<String> keys, String artKey) {
        if (artKey == null) return;

        String trimmed = artKey.trim();
        if (!trimmed.isBlank()) keys.add(trimmed);
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
