package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomArtUploadServer {

    private static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024; // 8MB cap per image
    private static final int MAX_CHUNK_BYTES = 256 * 1024;       // sanity cap
    private static final int MAX_ACTIVE_PER_PLAYER = 2;
    private static final long TIMEOUT_MS = 30_000;

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

    public static void handleBegin(CustomCardPackets.CustomArtBegin p, ServerPlayer player) {
        cleanupOld();

        UUID pid = player.getUUID();

        long activeForPlayer = ACTIVE.values().stream().filter(u -> u.playerId.equals(pid)).count();
        if (activeForPlayer >= MAX_ACTIVE_PER_PLAYER) {
            return;
        }

        if (p.totalBytes() <= 0 || p.totalBytes() > MAX_TOTAL_BYTES) {
            return;
        }
        if (p.chunkSize() <= 0 || p.chunkSize() > MAX_CHUNK_BYTES) {
            return;
        }
        if (p.totalChunks() <= 0 || p.totalChunks() > 4096) {
            return;
        }

        String artKey = sanitizeKey(p.artKey());
        if (artKey.isBlank()) {
            return;
        }

        String ext = safeExt(p.ext());

        ACTIVE.put(p.uploadId(), new Upload(pid, artKey, ext, p.totalBytes(), p.totalChunks()));
    }

    public static void handleChunk(CustomCardPackets.CustomArtChunk p, ServerPlayer player) {
        Upload u = ACTIVE.get(p.uploadId());
        if (u == null) {
            return;
        }
        if (!u.playerId.equals(player.getUUID())) {
            return;
        }

        int idx = p.chunkIndex();
        if (idx < 0 || idx >= u.totalChunks) {
            return;
        }

        byte[] bytes = p.bytes();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_CHUNK_BYTES) {
            return;
        }

        boolean first = (u.chunks[idx] == null);
        if (first) u.received++;
        u.chunks[idx] = bytes;
        u.lastTouchMs = System.currentTimeMillis();

        if (idx == 0 || idx == u.totalChunks - 1 || (u.received % 50 == 0)) {
        }
    }

    public static void handleFinish(CustomCardPackets.CustomArtFinish p, ServerPlayer player, MinecraftServer server) {
        Upload u = ACTIVE.get(p.uploadId());
        if (u == null) {
            return;
        }
        if (!u.playerId.equals(player.getUUID())) {
            return;
        }

        if (u.received != u.totalChunks) {
            return;
        }

        int total = 0;
        for (byte[] c : u.chunks) total += (c == null ? 0 : c.length);
        if (total <= 0 || total > MAX_TOTAL_BYTES) {
            ACTIVE.remove(p.uploadId());
            return;
        }

        byte[] all = new byte[total];
        int off = 0;
        for (byte[] c : u.chunks) {
            System.arraycopy(c, 0, all, off, c.length);
            off += c.length;
        }

        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("mtgcard").resolve("art");
        Path out = dir.resolve(u.artKey + "." + u.ext);

        try {
            Files.createDirectories(dir);
            Files.write(out, all, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // ✅ tell this player “that key now exists”
            ServerPlayNetworking.send(player, new CustomCardPackets.CustomArtReady(u.artKey));

        } catch (Exception ignored) {
        } finally {
            ACTIVE.remove(p.uploadId());
        }

    }

    private static void cleanupOld() {
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(e -> now - e.getValue().lastTouchMs > TIMEOUT_MS);
    }

    private static String safeExt(String ext) {
        if (ext == null) return "png";
        ext = ext.toLowerCase(Locale.ROOT);
        return (ext.equals("webp") || ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg")) ? ext : "png";
    }

    // allow only safe filename-ish keys
    private static String sanitizeKey(String k) {
        if (k == null) return "";
        k = k.trim().toLowerCase(Locale.ROOT);
        if (k.length() > 128) k = k.substring(0, 128);
        k = k.replaceAll("[^a-z0-9_\\-\\.]", "_");

        // ✅ If it looks like an id face key and lacks prefix, add it
        if (!k.startsWith("custom_") && k.matches("[0-9a-f]{32}_f[01]")) {
            k = "custom_" + k;
        }
        return k;
    }

    private CustomArtUploadServer() {}
}
