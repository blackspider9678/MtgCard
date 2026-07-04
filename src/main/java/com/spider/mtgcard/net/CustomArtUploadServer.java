package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.ImportPerms;
import com.spider.mtgcard.util.ArtImageStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomArtUploadServer {

    private static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CHUNK_BYTES = 256 * 1024;
    private static final int MAX_ACTIVE_PER_PLAYER = 2;
    private static final long TIMEOUT_MS = 30_000;
    private static final ExecutorService ART_WRITE_EXECUTOR =
            Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "mtgcard-custom-art-save");
                t.setDaemon(true);
                return t;
            });

    private static final class Upload {
        final UUID playerId;
        final String artKey;
        final String ext;
        final int totalBytes;
        final int totalChunks;
        final byte[][] chunks;
        int received = 0;
        long lastTouchMs = System.currentTimeMillis();

        Upload(UUID playerId, String artKey, String ext, int totalBytes, int totalChunks) {
            this.playerId = playerId;
            this.artKey = artKey;
            this.ext = ext;
            this.totalBytes = totalBytes;
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
        }
    }

    private static final Map<String, Upload> ACTIVE = new ConcurrentHashMap<>();

    public static void handleBegin(CustomCardPackets.CustomArtBegin payload, ServerPlayer player) {
        if (!ImportPerms.canImport(player)) {
            player.sendSystemMessage(Component.literal("You do not have permission to import custom cards."));
            return;
        }

        cleanupOld();

        UUID playerId = player.getUUID();
        long activeForPlayer = ACTIVE.values().stream().filter(upload -> upload.playerId.equals(playerId)).count();
        if (activeForPlayer >= MAX_ACTIVE_PER_PLAYER) {
            return;
        }

        if (payload.totalBytes() <= 0 || payload.totalBytes() > MAX_TOTAL_BYTES) {
            return;
        }
        if (payload.chunkSize() <= 0 || payload.chunkSize() > MAX_CHUNK_BYTES) {
            return;
        }
        if (payload.totalChunks() <= 0 || payload.totalChunks() > 4096) {
            return;
        }

        String artKey = sanitizeKey(payload.artKey());
        if (artKey.isBlank()) {
            return;
        }

        String ext = safeExt(payload.ext());
        ACTIVE.put(payload.uploadId(), new Upload(playerId, artKey, ext, payload.totalBytes(), payload.totalChunks()));
    }

    public static void handleChunk(CustomCardPackets.CustomArtChunk payload, ServerPlayer player) {
        if (!ImportPerms.canImport(player)) {
            return;
        }

        Upload upload = ACTIVE.get(payload.uploadId());
        if (upload == null || !upload.playerId.equals(player.getUUID())) {
            return;
        }

        int index = payload.chunkIndex();
        if (index < 0 || index >= upload.totalChunks) {
            return;
        }

        byte[] bytes = payload.bytes();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES) {
            return;
        }

        if (upload.chunks[index] == null) {
            upload.received++;
        }
        upload.chunks[index] = bytes;
        upload.lastTouchMs = System.currentTimeMillis();
    }

    public static void handleFinish(CustomCardPackets.CustomArtFinish payload, ServerPlayer player, MinecraftServer server) {
        if (!ImportPerms.canImport(player)) {
            return;
        }

        Upload upload = ACTIVE.get(payload.uploadId());
        if (upload == null || !upload.playerId.equals(player.getUUID())) {
            return;
        }

        if (upload.received != upload.totalChunks) {
            return;
        }

        int total = 0;
        for (byte[] chunk : upload.chunks) {
            total += chunk == null ? 0 : chunk.length;
        }
        if (total <= 0 || total > MAX_TOTAL_BYTES) {
            ACTIVE.remove(payload.uploadId());
            return;
        }

        ACTIVE.remove(payload.uploadId());

        byte[][] chunks = upload.chunks;
        String artKey = upload.artKey;
        String ext = upload.ext;
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("mtgcard").resolve("art");
        final int totalBytes = total;

        ART_WRITE_EXECUTOR.execute(() -> {
            long started = System.nanoTime();
            try {
                byte[] all = new byte[totalBytes];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, all, offset, chunk.length);
                    offset += chunk.length;
                }

                ArtImageStorage.StorageDecision decision = ArtImageStorage.normalizeForStorage(all, ext);
                ArtImageStorage.StoredArt art = decision.art();
                if (art == null) {
                    Mtgcard.LOGGER.warn("[MTGCard] Custom art {} from player {} could not be stored: {}",
                            artKey, playerName, decision.note());
                    return;
                }

                if (decision.fellBackFromWebp()) {
                    Mtgcard.LOGGER.warn("[MTGCard] Custom art {} from player {} fell back to .{} (source .{}): {}",
                            artKey, playerName, art.ext(), decision.sourceExt(), decision.note());
                } else {
                    Mtgcard.LOGGER.info("[MTGCard] Custom art {} from player {} stored as .{}",
                            artKey, playerName, art.ext());
                }

                ArtImageStorage.write(dir, artKey, art);
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                Mtgcard.LOGGER.info("[MTGCard] Custom art {} saved for {} in {} ms", artKey, playerName, elapsedMs);

                server.execute(() -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    if (current != null) {
                        ServerPlayNetworking.send(current, new CustomCardPackets.CustomArtReady(artKey));
                    }
                });
            } catch (Throwable t) {
                Mtgcard.LOGGER.warn("[MTGCard] Failed to save custom art {} from {}: {}",
                        artKey, playerName, t.toString());
                server.execute(() -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    if (current != null) {
                        current.sendSystemMessage(Component.literal("[MTGCard] Failed to save custom art: " + artKey));
                    }
                });
            }
        });
    }

    private static void cleanupOld() {
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(entry -> now - entry.getValue().lastTouchMs > TIMEOUT_MS);
    }

    private static String safeExt(String ext) {
        if (ext == null) {
            return "png";
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "webp", "png", "jpg", "jpeg" -> ext;
            default -> "png";
        };
    }

    private static String sanitizeKey(String key) {
        if (key == null) {
            return "";
        }

        key = key.trim().toLowerCase(Locale.ROOT);
        if (key.length() > 128) {
            key = key.substring(0, 128);
        }
        key = key.replaceAll("[^a-z0-9_\\-\\.]", "_");

        if (!key.startsWith("custom_") && key.matches("[0-9a-f]{32}_f[01]")) {
            key = "custom_" + key;
        }
        return key;
    }

    private CustomArtUploadServer() {}
}
