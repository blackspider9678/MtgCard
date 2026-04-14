// src/main/java/com/spider/mtgcard/content/pack/cache/ServerArtStore.java
package com.spider.mtgcard.content.pack.cache;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.util.ArtImageStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerArtStore {
    private final Path dir;
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public ServerArtStore(MinecraftServer server) {
        this.dir = server.getWorldPath(LevelResource.ROOT).resolve("mtgcard").resolve("art");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
    }

    public Path file(String artKey) {
        // new
        Path webp = dir.resolve(artKey + ".webp");
        if (Files.exists(webp)) return webp;

        // optional legacy fallback
        Path png = dir.resolve(artKey + ".png");
        if (Files.exists(png)) return png;

        // default target
        return webp;
    }

    /** Ensure file exists; download once if missing. Returns path or null on failure. */
    public Path ensure(String artKey, String url) {
        Path f = file(artKey);
        if (Files.exists(f)) return f;
        if (url == null || url.isEmpty()) return null;

        if (inFlight.putIfAbsent(artKey, Boolean.TRUE) != null) {
            for (int i=0;i<40;i++) { try { Thread.sleep(50);} catch (InterruptedException ignored) {} if (Files.exists(f)) break; }
            inFlight.remove(artKey);
            return Files.exists(f) ? f : null;
        }
        try { return downloadAndStore(f, url); }
        catch (Exception e1) {
            int q = url.indexOf('?'); // retry without query
            if (q > 0) try { return downloadAndStore(f, url.substring(0,q)); } catch (Exception ignored) {}
            return null;
        } finally { inFlight.remove(artKey); }
    }

    private static Path downloadAndStore(Path dst, String url) throws Exception {
        Files.createDirectories(dst.getParent());

        var conn = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "mtgcard/1.0 (+server)");

        Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }

        if (Files.size(tmp) <= 32) {
            Files.deleteIfExists(tmp);
            throw new RuntimeException("tiny");
        }

        byte[] bytes = Files.readAllBytes(tmp);
        Files.deleteIfExists(tmp);

        String baseName = stripImageExt(dst.getFileName().toString());
        ArtImageStorage.StorageDecision decision = ArtImageStorage.normalizeForStorage(bytes, url);
        ArtImageStorage.StoredArt art = decision.art();
        if (art == null) {
            Mtgcard.LOGGER.warn("[MTGCard] ServerArtStore could not store {} from {}: {}", baseName, url, decision.note());
            throw new RuntimeException("unsupported");
        }

        if (decision.fellBackFromWebp()) {
            Mtgcard.LOGGER.warn("[MTGCard] ServerArtStore {} from {} fell back to .{} (source .{}): {}",
                    baseName, url, art.ext(), decision.sourceExt(), decision.note());
        } else {
            Mtgcard.LOGGER.info("[MTGCard] ServerArtStore {} from {} stored as .{}",
                    baseName, url, art.ext());
        }

        return ArtImageStorage.write(dst.getParent(), baseName, art);
    }

    private static String stripImageExt(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webp") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
        return fileName;
    }
}
