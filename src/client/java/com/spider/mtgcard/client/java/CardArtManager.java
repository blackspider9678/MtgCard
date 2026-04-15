package com.spider.mtgcard.client.java;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spider.mtgcard.util.StackData;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CardArtManager — unchanged public API.
 * - Supports custom world art (mtg_meta.world_art_front/world_art_back) for custom cards.
 * - Still supports Scryfall URLs (image_png/card_faces[i].image_png).
 * - Writes art-index.json in integrated server; in remote MP we rely on server pushes.
 *
 * NEW:
 * - Requests are QUEUED and sent gradually (one-by-one / rate-limited) to avoid spamming 80 at once.
 */
public final class CardArtManager {

    public record TextureRef(Identifier id, int texW, int texH, DynamicTexture tex) {}

    private static final Map<String, TextureRef> TEX = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> DISK_INDEX = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mtg-card-textures");
        t.setDaemon(true);
        return t;
    });
    private static final Gson GSON = new Gson();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    // artKey -> last request time (ms)
    private static final ConcurrentMap<String, Long> IN_FLIGHT = new ConcurrentHashMap<>();

    // -----------------------
    // NEW: request queue
    // -----------------------
    private record PendingReq(String artKey, String url, String fileName) {}

    private static final ConcurrentLinkedQueue<PendingReq> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    // rate limits (tune these)
    private static final int MAX_SENDS_PER_TICK = 8;
    private static final int MAX_INFLIGHT = 24;
    private static final long REQUEST_COOLDOWN_MS = 1500;

    private static boolean shouldRequestNow(String artKey) {
        long now = System.currentTimeMillis();
        Long last = IN_FLIGHT.putIfAbsent(artKey, now);

        if (last == null) return true; // first time

        if (now - last < REQUEST_COOLDOWN_MS) return false;

        IN_FLIGHT.put(artKey, now);
        return true;
    }

    /** Enqueue a request (deduped). The actual packet send happens in pumpQueue(). */
    private static void enqueueRequest(String artKey, String url, String fileName) {
        if (artKey == null || artKey.isBlank()) return;

        // If it's already queued, do nothing
        if (!QUEUED.add(artKey)) return;

        // Store filename mapping early (so resolveCachedFile() has something)
        if (fileName != null && !fileName.isBlank()) {
            DISK_INDEX.putIfAbsent(artKey, fileName);
            saveIndexAsync();
        }

        PENDING.add(new PendingReq(artKey, url == null ? "" : url, fileName == null ? "" : fileName));
    }

    /**
     * Pump a few queued requests per tick.
     * Call this from ClientTickEvents.END_CLIENT_TICK.
     */
    public static void pumpQueue() {
        var mc = Minecraft.getInstance();
        if (mc == null) return;

        // Only pump when we’re in-world & networking is live
        if (mc.player == null || mc.getConnection() == null) return;

        int started = 0;

        while (started < MAX_SENDS_PER_TICK && IN_FLIGHT.size() < MAX_INFLIGHT) {
            PendingReq req = PENDING.poll();
            if (req == null) break;

            // Allow it to be queued again in the future if needed
            QUEUED.remove(req.artKey());

            // cooldown gate (prevents hammering one bad key)
            if (!shouldRequestNow(req.artKey())) {
                continue;
            }

            // send request packet (server will fetch & respond)
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.spider.mtgcard.net.ArtPackets.ArtRequest(req.artKey(), req.url())
            );

            started++;
        }
    }

    /** Clears queued/inflight state (call on disconnect/world switch if you want). */
    public static void clearRequestState() {
        PENDING.clear();
        QUEUED.clear();
        IN_FLIGHT.clear();
        LOADING.clear();
    }

    // ---- lifecycle ----
    public static void init() { loadIndex(); }

    /**
     * Cache dir:
     * - Integrated server (SP/LAN): <world>/mtgcard/art
     * - Remote MP client: <runDir>/mtgcard/art/<server-address or level-name>
     */
    public static Path cacheDir() {
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path dir = root.resolve("mtgcard").resolve("art");
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            return dir;
        }
        var game = mc.gameDirectory.toPath();
        String scope = "singleplayer";
        if (mc.getCurrentServer() != null) {
            scope = mc.getCurrentServer().ip.replace(':','_');
        } else if (mc.getSingleplayerServer() != null) {
            scope = mc.getSingleplayerServer().getWorldData().getLevelName().replace(' ','_');
        }
        var dir = game.resolve("mtgcard").resolve("art").resolve(scope);
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    /** Index file path: world-scoped in integrated server; null in remote MP (server owns it). */
    private static Path indexFile() {
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path file = root.resolve("mtgcard").resolve("art-index.json");
            try { Files.createDirectories(file.getParent()); } catch (Exception ignored) {}
            return file;
        }
        return null; // remote MP -> don't write an index on the client
    }

    private static boolean isIntegrated(Minecraft mc) {
        return mc.getSingleplayerServer() != null && mc.getCurrentServer() == null;
    }

    private static void loadIndex() {
        var file = indexFile();
        if (file == null || !Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> m = GSON.fromJson(r, INDEX_TYPE);
            if (m != null) DISK_INDEX.putAll(m);
        } catch (Exception ignored) {}
    }

    public static void saveIndexAsync() {
        var file = indexFile();
        if (file == null) return;
        IO.submit(() -> {
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(DISK_INDEX, w);
            } catch (Exception ignored) {}
        });
    }

    /** Purge all cached art files and index (scoped to current cacheDir). */
    public static int purgeAll() {
        int deleted = 0;
        try (var files = Files.list(cacheDir())) {
            for (Path p : files.toList()) {
                if (Files.isRegularFile(p) && (
                        p.toString().endsWith(".png")
                                || p.toString().endsWith(".webp")
                                || p.toString().endsWith(".jpg")
                                || p.toString().endsWith(".jpeg")
                )) {
                    Files.deleteIfExists(p);
                    deleted++;
                }
            }
        } catch (Exception ignored) {}
        DISK_INDEX.clear();
        saveIndexAsync();
        return deleted;
    }

    /** Rebuild the JSON index from files on disk (scoped to current cacheDir). */
    public static int rebuildIndex() {
        DISK_INDEX.clear();
        try (var files = Files.list(cacheDir())) {
            files.filter(p -> {
                String s = p.toString().toLowerCase(Locale.ROOT);
                return s.endsWith(".png") || s.endsWith(".webp") || s.endsWith(".jpg") || s.endsWith(".jpeg");
            }).forEach(p -> {
                String name = p.getFileName().toString();
                String key = name
                        .replace(".png", "")
                        .replace(".webp", "")
                        .replace(".jpg", "")
                        .replace(".jpeg", "");
                DISK_INDEX.put(key, name);
            });
        } catch (Exception ignored) {}
        saveIndexAsync();
        return DISK_INDEX.size();
    }

    public static String stats() {
        try {
            long count = Files.list(cacheDir())
                    .filter(p -> {
                        String s = p.toString().toLowerCase(Locale.ROOT);
                        return s.endsWith(".png") || s.endsWith(".webp") || s.endsWith(".jpg") || s.endsWith(".jpeg");
                    })
                    .count();
            long bytes = Files.walk(cacheDir())
                    .filter(Files::isRegularFile)
                    .mapToLong(f -> {
                        try { return Files.size(f); } catch (Exception e) { return 0; }
                    }).sum();
            double mb = bytes / 1024.0 / 1024.0;
            return count + " textures (" + String.format(Locale.ROOT, "%.1f", mb) + " MB)";
        } catch (Exception e) {
            return "error reading cache: " + e.getMessage();
        }
    }

    // ---- Texture logic ----
    /** Returns a TextureRef if already cached or loaded; otherwise requests and returns null. */
    public static TextureRef getOrRequestFace(ItemStack stack, int faceIndex) {
        CompoundTag root = StackData.readCustom(stack);
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        // world art path
        String worldKey = "";
        if (faceIndex <= 0) {
            worldKey = meta.getString("world_art_front").orElse("");
            if (worldKey.isBlank()) worldKey = meta.getString("world_art").orElse("");
        } else {
            worldKey = meta.getString("world_art_back").orElse("");
            if (worldKey.isBlank()) {
                worldKey = meta.getString("world_art_front").orElse("");
                if (worldKey.isBlank()) worldKey = meta.getString("world_art").orElse("");
            }
        }

        if (worldKey != null && !worldKey.isBlank()) {
            return getOrRequestWorld(worldKey.trim());
        }

        // Scryfall path
        String artKey = computeArtKey(meta, faceIndex);

        TextureRef cached = getLiveCached(artKey);
        if (cached != null) return cached;

        String url = extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return null;

        Path file = resolveCachedFile(artKey);

        if (Files.exists(file)) {
            queueDiskLoad(artKey, file);
            return null;
        }

        // IMPORTANT: queue instead of sending immediately
        String fileName = DISK_INDEX.getOrDefault(artKey, artKey + ".webp");
        enqueueRequest(artKey, url, fileName);
        return null;
    }

    /** Resolve world-art by key: bind from disk if present, else request from server. */
    private static TextureRef getOrRequestWorld(String artKey) {
        TextureRef cached = getLiveCached(artKey);
        if (cached != null) {
            return cached;
        }

        Path file = resolveCachedFile(artKey);

        if (Files.exists(file)) {
            DISK_INDEX.put(artKey, file.getFileName().toString());
            saveIndexAsync();
            queueDiskLoad(artKey, file);
            return null;
        }

        DISK_INDEX.putIfAbsent(artKey, artKey + ".webp");
        saveIndexAsync();

        // IMPORTANT: queue instead of sending immediately
        enqueueRequest(artKey, "", artKey + ".webp");
        return null;
    }

    public static void clearMemoryTextures() {
        for (TextureRef ref : TEX.values()) {
            try { ref.tex().close(); } catch (Throwable ignored) {}
        }
        TEX.clear();
    }

    private static String computeArtKey(CompoundTag meta, int faceIndex) {
        String scryId = meta.getString("scryfall_id").orElse("");
        if (scryId.isEmpty()) scryId = meta.getString("id").orElse("");
        if (!scryId.isEmpty()) return scryId + "_f" + faceIndex;

        String url = extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return "missingmeta_f" + faceIndex;

        String norm = stripQuery(url);
        String hash = sha1(norm);
        return hash + "_f" + faceIndex;
    }

    private static String extractImageUrl(CompoundTag meta, int faceIndex) {
        Optional<ListTag> facesOpt = meta.getList("card_faces");
        if (facesOpt.isPresent() && !facesOpt.get().isEmpty()) {
            int idx = Math.max(0, Math.min(faceIndex, facesOpt.get().size() - 1));
            Optional<CompoundTag> face0 = facesOpt.get().getCompound(idx);
            if (face0.isPresent()) {
                String direct = face0.get().getString("image_png").orElse("");
                if (!direct.isEmpty()) return direct;

                Optional<CompoundTag> uris = face0.get().getCompound("image_uris");
                if (uris.isPresent()) {
                    String u = choiceImageUrl(uris.get());
                    if (!u.isEmpty()) return u;
                }
            }
        }

        String directRoot = meta.getString("image_png").orElse("");
        if (!directRoot.isEmpty()) return directRoot;

        Optional<CompoundTag> urisRoot = meta.getCompound("image_uris");
        if (urisRoot.isPresent()) {
            String u = choiceImageUrl(urisRoot.get());
            if (!u.isEmpty()) return u;
        }
        return "";
    }

    private static String choiceImageUrl(CompoundTag uris) {
        String png   = uris.getString("png").orElse("");
        if (!png.isEmpty()) return png;
        String large = uris.getString("large").orElse("");
        if (!large.isEmpty()) return large;
        String normal = uris.getString("normal").orElse("");
        if (!normal.isEmpty()) return normal;
        String art = uris.getString("art_crop").orElse("");
        if (!art.isEmpty()) return art;
        String border = uris.getString("border_crop").orElse("");
        return border == null ? "" : border;
    }

    // Called by the client networking receiver when the server sends image bytes.
    public static void onArtResponse(String artKey, byte[] imgBytes) {
        IN_FLIGHT.remove(artKey);

        try {
            String ext = detectExt(imgBytes);
            String fileName = artKey + "." + ext;
            Path file = cacheDir().resolve(fileName);

            Files.createDirectories(file.getParent());
            Files.write(file, imgBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            DISK_INDEX.put(artKey, fileName);
            saveIndexAsync();

            queueDiskLoad(artKey, file);
        } catch (Exception ignored) {}
    }

    private static Path resolveCachedFile(String artKey) {
        Path dir = cacheDir();

        for (String candidate : artKeyCandidates(artKey)) {
            Path indexed = resolveIndexedFile(dir, candidate);
            if (indexed != null) return indexed;

            Path exact = resolveExactFile(dir, candidate);
            if (exact != null) return exact;
        }

        return dir.resolve(artKey + ".webp");
    }

    private static Path resolveIndexedFile(Path dir, String artKey) {
        String mapped = DISK_INDEX.get(artKey);
        if (mapped == null || mapped.isBlank()) return null;

        Path path = dir.resolve(mapped);
        return Files.exists(path) ? path : null;
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

    private static Iterable<String> artKeyCandidates(String artKey) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        if (artKey == null || artKey.isBlank()) return keys;

        addArtKeyCandidate(keys, artKey);

        String trimmed = artKey.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        addArtKeyCandidate(keys, lower);

        if (lower.startsWith("custom_")) {
            addArtKeyCandidate(keys, lower.substring("custom_".length()));
        } else {
            addArtKeyCandidate(keys, "custom_" + lower);
        }

        return keys;
    }

    private static void addArtKeyCandidate(Set<String> keys, String artKey) {
        if (artKey == null) return;

        String trimmed = artKey.trim();
        if (!trimmed.isBlank()) keys.add(trimmed);
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return (q >= 0) ? url.substring(0, q) : url;
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static BufferedImage readAnyImage(InputStream in) throws Exception {
        com.spider.mtgcard.util.ArtImageStorage.ensureWebpCodecsRegistered();
        BufferedImage img = ImageIO.read(in);
        if (img == null) throw new IllegalArgumentException("Unsupported image format (ImageIO returned null)");
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
            img = out;
        }
        return img;
    }

    private static NativeImage bufferedToNative(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, true);

        int[] argb = src.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = argb[row + x];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int g = (c >>> 8)  & 0xFF;
                int b =  c         & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                ni.setPixelABGR(x, y, abgr);
            }
        }
        return ni;
    }

    private static BufferedImage readAnyImage(byte[] bytes) throws Exception {
        try (var bais = new ByteArrayInputStream(bytes)) {
            return readAnyImage(bais);
        }
    }

    private static void queueDiskLoad(String artKey, Path file) {
        if (artKey == null || artKey.isBlank() || file == null || !Files.exists(file)) {
            return;
        }

        if (!LOADING.add(artKey)) {
            return;
        }

        IO.submit(() -> {
            NativeImage img = null;
            try (var in = Files.newInputStream(file)) {
                BufferedImage bi = readAnyImage(in);
                img = bufferedToNative(bi);
                NativeImage finalImg = img;
                img = null;

                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    try {
                        bindTexture(artKey, finalImg);
                    } finally {
                        LOADING.remove(artKey);
                    }
                });
            } catch (Throwable t) {
                if (img != null) {
                    try { img.close(); } catch (Throwable ignored) {}
                }
                System.out.println("[MTGCard] Failed to load art key=" + artKey + " from " + file + ": " + t);
                LOADING.remove(artKey);
            }
        });
    }

    private static void bindTexture(String artKey, NativeImage img) {
        try {
            Identifier texId = Identifier.fromNamespaceAndPath("mtgcard", "card/" + sha1(artKey));

            TextureRef old = TEX.remove(artKey);
            if (old != null) {
                try { old.tex().close(); } catch (Throwable ignored) {}
            }

            DynamicTexture tex = new DynamicTexture(() -> "mtgcard/" + artKey, img);
            Minecraft.getInstance().getTextureManager().register(texId, tex);

            TEX.put(artKey, new TextureRef(texId, img.getWidth(), img.getHeight(), tex));
        } catch (Throwable t) {
            try { img.close(); } catch (Throwable ignored) {}
        }
    }

    private static String detectExt(byte[] b) {
        if (b == null || b.length < 12) return "png";

        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' &&
                b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";

        return "png";
    }

    public static void invalidateArtKey(String artKey) {
        if (artKey == null || artKey.isBlank()) return;

        // Stop any “cooldown/inflight” suppression
        IN_FLIGHT.remove(artKey);
        QUEUED.remove(artKey);

        // Remove pending queue entries for this key (optional but nice)
        PENDING.removeIf(r -> r.artKey().equals(artKey));

        // Remove any cached texture so next request actually reloads
        TextureRef old = TEX.remove(artKey);
        if (old != null) {
            try { old.tex().close(); } catch (Throwable ignored) {}
        }

        // If DISK_INDEX had a stale mapping, drop it so resolveCachedFile re-discovers
        DISK_INDEX.remove(artKey);
        saveIndexAsync();
    }

    public static void tryLoadNow(String artKey) {
        if (artKey == null || artKey.isBlank()) return;

        Path file = resolveCachedFile(artKey);
        if (Files.exists(file)) {
            DISK_INDEX.put(artKey, file.getFileName().toString());
            saveIndexAsync();
            queueDiskLoad(artKey, file);
        }
    }

    private static TextureRef getLiveCached(String artKey) {
        TextureRef ref = TEX.get(artKey);
        if (ref == null) return null;

        var tm = Minecraft.getInstance().getTextureManager();
        var tex = tm.getTexture(ref.id()); // if not registered, this tends to be null or MissingTexture

        if (tex == null) {
            // stale entry
            TEX.remove(artKey);
            try { ref.tex().close(); } catch (Throwable ignored) {}
            return null;
        }
        return ref;
    }

    public static void requestNow(String key) {
        // If your system has “ensureLoaded/queueDownload/queueLoad”, call it here.
        // Otherwise just invalidating is enough—next render tick will attempt again.
    }

    private CardArtManager() {}
}
