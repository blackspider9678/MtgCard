package com.spider.mtgcard.client.java;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spider.mtgcard.shared.CardArtCommon;
import com.spider.mtgcard.shared.MtgCardPaths;
import com.spider.mtgcard.util.StackData;
import com.spider.mtgcard.util.TcgCardMeta;
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
import java.util.List;
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
 * - Writes art_index.json in integrated server; in remote MP we rely on server pushes.
 *
 * NEW:
 * - Requests are QUEUED and sent gradually (one-by-one / rate-limited) to avoid spamming 80 at once.
 */
public final class CardArtManager {

    public record TextureRef(Identifier id, int texW, int texH, DynamicTexture tex) {}

    private static final Map<String, TextureRef> TEX = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> DISK_INDEX = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> ART_SET_INDEX = new ConcurrentHashMap<>();
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
    private record PendingReq(String requestKey, String game, String artKey, String url, String fileName, String fallbackKeys, String setCode) {}

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
        enqueueRequest(MtgCardPaths.GAME_MTG, artKey, url, fileName, "", "");
    }

    /** Enqueue a request (deduped). The actual packet send happens in pumpQueue(). */
    private static void enqueueRequest(String artKey, String url, String fileName, String fallbackKeys) {
        enqueueRequest(MtgCardPaths.GAME_MTG, artKey, url, fileName, fallbackKeys, "");
    }

    /** Enqueue a request (deduped). The actual packet send happens in pumpQueue(). */
    private static void enqueueRequest(String artKey, String url, String fileName, String fallbackKeys, String setCode) {
        enqueueRequest(MtgCardPaths.GAME_MTG, artKey, url, fileName, fallbackKeys, setCode);
    }

    /** Enqueue a request (deduped). The actual packet send happens in pumpQueue(). */
    private static void enqueueRequest(String game, String artKey, String url, String fileName, String fallbackKeys, String setCode) {
        if (artKey == null || artKey.isBlank()) return;
        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String requestKey = scopedArtKey(safeGame, artKey);

        // If it's already queued, do nothing
        if (!QUEUED.add(requestKey)) return;

        // Store filename mapping early (so resolveCachedFile() has something)
        if (fileName != null && !fileName.isBlank()) {
            DISK_INDEX.putIfAbsent(artKey, fileName);
            saveIndexAsync(safeGame);
        }
        if (setCode != null && !setCode.isBlank()) {
            ART_SET_INDEX.put(requestKey, MtgCardPaths.sanitizeSetFolder(setCode));
        }

        PENDING.add(new PendingReq(
                requestKey,
                safeGame,
                artKey,
                url == null ? "" : url,
                fileName == null ? "" : fileName,
                fallbackKeys == null ? "" : fallbackKeys,
                setCode == null ? "" : setCode
        ));
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
            QUEUED.remove(req.requestKey());

            // cooldown gate (prevents hammering one bad key)
            if (!shouldRequestNow(req.requestKey())) {
                continue;
            }

            // send request packet (server will fetch & respond)
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.spider.mtgcard.net.ArtPackets.ArtRequest(req.artKey(), req.url(), req.fallbackKeys(), req.setCode(), req.game())
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
     * - Integrated server (SP/LAN): <world>/mtgcard/mtg/main_art
     * - Remote MP client: <runDir>/mtgcard/mtg/main_art/<server-address or level-name>
     */
    public static Path cacheDir() {
        return mainArtCacheDir(MtgCardPaths.GAME_MTG);
    }

    private static Path mainArtCacheDir() {
        return mainArtCacheDir(MtgCardPaths.GAME_MTG);
    }

    private static Path mainArtCacheDir(String game) {
        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path dir = root.resolve("mtgcard").resolve(safeGame).resolve("main_art");
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            return dir;
        }
        var gameDir = mc.gameDirectory.toPath();
        var dir = gameDir.resolve("mtgcard").resolve(safeGame).resolve("main_art").resolve(cacheScope(mc));
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    private static Path customArtRoot() {
        return customArtRoot(MtgCardPaths.GAME_MTG);
    }

    private static Path customArtRoot(String game) {
        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path dir = root.resolve("mtgcard").resolve(safeGame).resolve("custom_art");
            try { Files.createDirectories(dir); } catch (Exception ignored) {}
            return dir;
        }
        var gameDir = mc.gameDirectory.toPath();
        Path dir = gameDir.resolve("mtgcard").resolve(safeGame).resolve("custom_art").resolve(cacheScope(mc));
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    private static Path customArtDir(String setCode) {
        return customArtDir(MtgCardPaths.GAME_MTG, setCode);
    }

    private static Path customArtDir(String game, String setCode) {
        Path dir = customArtRoot(game).resolve(MtgCardPaths.sanitizeSetFolder(setCode));
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    private static Path legacyArtCacheDir() {
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return root.resolve("mtgcard").resolve("art");
        }
        return mc.gameDirectory.toPath().resolve("mtgcard").resolve("art").resolve(cacheScope(mc));
    }

    private static String cacheScope(Minecraft mc) {
        String scope = "singleplayer";
        if (mc.getCurrentServer() != null) {
            scope = mc.getCurrentServer().ip.replace(':','_');
        } else if (mc.getSingleplayerServer() != null) {
            scope = mc.getSingleplayerServer().getWorldData().getLevelName().replace(' ','_');
        }
        return scope;
    }

    private static String gameForStack(ItemStack stack) {
        String game = TcgCardMeta.read(stack).game();
        return MtgCardPaths.sanitizeGameFolder(game);
    }

    private static String scopedArtKey(String game, String artKey) {
        return MtgCardPaths.sanitizeGameFolder(game) + ":" + (artKey == null ? "" : artKey.trim());
    }

    /** Index file path: world-scoped in integrated server; null in remote MP (server owns it). */
    private static Path indexFile() {
        return indexFile(MtgCardPaths.GAME_MTG);
    }

    private static Path indexFile(String game) {
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path file = root.resolve("mtgcard").resolve(MtgCardPaths.sanitizeGameFolder(game)).resolve("art_index.json");
            try { Files.createDirectories(file.getParent()); } catch (Exception ignored) {}
            return file;
        }
        return null; // remote MP -> don't write an index on the client
    }

    private static Path legacyIndexFile() {
        var mc = Minecraft.getInstance();
        if (isIntegrated(mc)) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            return root.resolve("mtgcard").resolve("art-index.json");
        }
        return null;
    }

    private static boolean isIntegrated(Minecraft mc) {
        return mc.getSingleplayerServer() != null && mc.getCurrentServer() == null;
    }

    private static void loadIndex() {
        var file = indexFile();
        if (file == null) return;
        if (!Files.exists(file)) {
            Path legacy = legacyIndexFile();
            if (legacy != null && Files.exists(legacy)) {
                file = legacy;
            } else {
                return;
            }
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> m = GSON.fromJson(r, INDEX_TYPE);
            if (m != null) DISK_INDEX.putAll(m);
        } catch (Exception ignored) {}
    }

    public static void saveIndexAsync() {
        saveIndexAsync(MtgCardPaths.GAME_MTG);
    }

    private static void saveIndexAsync(String game) {
        var file = indexFile(game);
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
        deleted += deleteImagesUnder(mainArtCacheDir(), false);
        deleted += deleteImagesUnder(customArtRoot(), true);
        DISK_INDEX.clear();
        saveIndexAsync();
        return deleted;
    }

    /** Rebuild the JSON index from files on disk (scoped to current cacheDir). */
    public static int rebuildIndex() {
        DISK_INDEX.clear();
        indexImagesUnder(mainArtCacheDir(), false);
        indexImagesUnder(customArtRoot(), true);
        saveIndexAsync();
        return DISK_INDEX.size();
    }

    public static String stats() {
        try {
            long count = countImagesUnder(mainArtCacheDir(), false) + countImagesUnder(customArtRoot(), true);
            long bytes = bytesUnder(mainArtCacheDir(), false) + bytesUnder(customArtRoot(), true);
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
        String game = gameForStack(stack);

        // world art path
        String worldKey = extractWorldArtKey(meta, faceIndex);

        if (worldKey != null && !worldKey.isBlank()) {
            String setCode = meta.getString("set").orElse("");
            return getOrRequestWorld(game, worldKey.trim(), setCode);
        }

        // Scryfall path
        String artKey = CardArtCommon.computeArtKey(meta, faceIndex);
        List<String> fallbackKeys = CardArtCommon.legacyFallbackArtKeys(meta, faceIndex);
        String textureKey = scopedArtKey(game, artKey);

        TextureRef cached = getLiveCached(textureKey);
        if (cached != null) return cached;

        for (String fallbackKey : fallbackKeys) {
            cached = getLiveCached(scopedArtKey(game, fallbackKey));
            if (cached != null) return cached;
        }

        Path file = resolveMainCachedFile(game, artKey, fallbackKeys);

        if (Files.exists(file)) {
            DISK_INDEX.put(artKey, file.getFileName().toString());
            saveIndexAsync(game);
            queueDiskLoad(textureKey, file);
            return null;
        }

        String url = CardArtCommon.extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return null;

        // IMPORTANT: queue instead of sending immediately
        String fileName = DISK_INDEX.getOrDefault(artKey, artKey + ".webp");
        enqueueRequest(game, artKey, url, fileName, CardArtCommon.encodeFallbackKeys(fallbackKeys), "");
        return null;
    }

    /** Resolve world-art by key: bind from disk if present, else request from server. */
    private static TextureRef getOrRequestWorld(String artKey) {
        return getOrRequestWorld(MtgCardPaths.GAME_MTG, artKey, ART_SET_INDEX.getOrDefault(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), ""));
    }

    /** Resolve world-art by key: bind from disk if present, else request from server. */
    private static TextureRef getOrRequestWorld(String artKey, String setCode) {
        return getOrRequestWorld(MtgCardPaths.GAME_MTG, artKey, setCode);
    }

    /** Resolve world-art by key: bind from disk if present, else request from server. */
    private static TextureRef getOrRequestWorld(String game, String artKey, String setCode) {
        String textureKey = scopedArtKey(game, artKey);
        TextureRef cached = getLiveCached(textureKey);
        if (cached != null) {
            return cached;
        }

        if (setCode != null && !setCode.isBlank()) {
            ART_SET_INDEX.put(textureKey, MtgCardPaths.sanitizeSetFolder(setCode));
        }

        Path file = resolveCustomCachedFile(game, artKey, setCode);

        if (Files.exists(file)) {
            DISK_INDEX.put(artKey, file.getFileName().toString());
            saveIndexAsync(game);
            queueDiskLoad(textureKey, file);
            return null;
        }

        DISK_INDEX.putIfAbsent(artKey, artKey + ".webp");
        saveIndexAsync(game);

        // IMPORTANT: queue instead of sending immediately
        enqueueRequest(game, artKey, "", artKey + ".webp", "", setCode);
        return null;
    }

    private static String extractWorldArtKey(CompoundTag meta, int faceIndex) {
        String worldKey = "";
        if (faceIndex <= 0) {
            worldKey = meta.getString("world_art_front").orElse("");
            if (worldKey.isBlank()) worldKey = meta.getString("world_art").orElse("");
            if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, 0);
            return worldKey;
        }

        worldKey = meta.getString("world_art_back").orElse("");
        if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, faceIndex);
        if (worldKey.isBlank()) {
            worldKey = meta.getString("world_art_front").orElse("");
            if (worldKey.isBlank()) worldKey = meta.getString("world_art").orElse("");
            if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, 0);
        }
        return worldKey;
    }

    private static String extractFaceWorldArt(CompoundTag meta, int faceIndex) {
        Optional<ListTag> facesOpt = meta.getList("card_faces");
        if (facesOpt.isEmpty() || facesOpt.get().isEmpty()) return "";

        int idx = Math.max(0, Math.min(faceIndex, facesOpt.get().size() - 1));
        Optional<CompoundTag> face = facesOpt.get().getCompound(idx);
        return face.flatMap(f -> f.getString("world_art")).orElse("");
    }

    public static void refreshWorldArt(String artKey) {
        refreshWorldArt(artKey, ART_SET_INDEX.getOrDefault(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), ""));
    }

    public static void refreshWorldArt(String artKey, String setCode) {
        if (artKey == null || artKey.isBlank()) return;
        if (setCode != null && !setCode.isBlank()) {
            ART_SET_INDEX.put(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), MtgCardPaths.sanitizeSetFolder(setCode));
        }

        invalidateArtKey(artKey);

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && isIntegrated(mc)) {
            tryLoadNow(artKey);
            return;
        }

        deleteCachedFiles(artKey);
        enqueueRequest(MtgCardPaths.GAME_MTG, artKey, "", artKey + ".webp", "", ART_SET_INDEX.getOrDefault(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), ""));
    }

    public static void forgetWorldArt(String artKey) {
        if (artKey == null || artKey.isBlank()) return;

        invalidateArtKey(artKey);
        deleteCachedFiles(artKey);
    }

    public static void clearMemoryTextures() {
        for (TextureRef ref : TEX.values()) {
            try { ref.tex().close(); } catch (Throwable ignored) {}
        }
        TEX.clear();
    }

    // Called by the client networking receiver when the server sends image bytes.
    public static void onArtResponse(String artKey, byte[] imgBytes) {
        onArtResponse(MtgCardPaths.GAME_MTG, artKey, imgBytes);
    }

    // Called by the client networking receiver when the server sends image bytes.
    public static void onArtResponse(String game, String artKey, byte[] imgBytes) {
        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String textureKey = scopedArtKey(safeGame, artKey);
        IN_FLIGHT.remove(textureKey);

        try {
            String ext = detectExt(imgBytes);
            String fileName = artKey + "." + ext;
            String setCode = ART_SET_INDEX.get(textureKey);
            Path dir = (setCode == null || setCode.isBlank())
                    ? mainArtCacheDir(safeGame)
                    : customArtDir(safeGame, setCode);
            Path file = dir.resolve(fileName);

            Files.createDirectories(file.getParent());
            Files.write(file, imgBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            com.spider.mtgcard.util.ArtImageStorage.deleteSiblingFormats(dir, artKey, ext);

            DISK_INDEX.put(artKey, fileName);
            saveIndexAsync(safeGame);

            queueDiskLoad(textureKey, file);
        } catch (Exception ignored) {}
    }

    private static Path resolveAnyCachedFile(String artKey) {
        Path main = resolveMainCachedFile(MtgCardPaths.GAME_MTG, artKey, List.of());
        if (Files.exists(main)) return main;

        String setCode = ART_SET_INDEX.getOrDefault(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), "");
        Path custom = resolveCustomCachedFile(MtgCardPaths.GAME_MTG, artKey, setCode);
        if (Files.exists(custom)) return custom;

        return main;
    }

    private static Path resolveMainCachedFile(String game, String artKey, List<String> fallbackKeys) {
        Path dir = mainArtCacheDir(game);

        for (String candidate : artKeyCandidates(artKey, fallbackKeys)) {
            Path indexed = resolveIndexedFile(dir, candidate);
            if (indexed != null) return indexed;

            Path exact = resolveExactFile(dir, candidate);
            if (exact != null) return exact;
        }

        Path legacyDir = legacyArtCacheDir();
        for (String candidate : artKeyCandidates(artKey, fallbackKeys)) {
            Path legacy = resolveExactFile(legacyDir, candidate);
            if (legacy != null) return migrateLegacyArt(dir, artKey, legacy);
        }

        return dir.resolve(artKey + ".webp");
    }

    private static Path resolveCustomCachedFile(String game, String artKey, String setCode) {
        Path setDir = customArtDir(game, setCode);
        Path exact = resolveExactFile(setDir, artKey);
        if (exact != null) return exact;

        Path root = customArtRoot(game);
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir) || dir.equals(setDir)) continue;
                exact = resolveExactFile(dir, artKey);
                if (exact != null) return exact;
            }
        } catch (Exception ignored) {
        }

        Path legacy = resolveExactFile(legacyArtCacheDir(), artKey);
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

    private static Path resolveIndexedFile(Path dir, String artKey) {
        String mapped = DISK_INDEX.get(artKey);
        if (mapped == null || mapped.isBlank()) return null;

        Path path = dir.resolve(mapped);
        return Files.exists(path) ? path : null;
    }

    private static Path resolveExactFile(Path dir, String artKey) {
        if (dir == null || artKey == null || artKey.isBlank()) return null;

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

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "webp";
    }

    private static boolean isImagePath(Path path) {
        if (path == null) return false;
        String s = path.toString().toLowerCase(Locale.ROOT);
        return s.endsWith(".png") || s.endsWith(".webp") || s.endsWith(".jpg") || s.endsWith(".jpeg");
    }

    private static int deleteImagesUnder(Path dir, boolean recursive) {
        if (dir == null || !Files.exists(dir)) return 0;
        int deleted = 0;
        try (var stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p) && isImagePath(p) && Files.deleteIfExists(p)) {
                    deleted++;
                }
            }
        } catch (Exception ignored) {
        }
        return deleted;
    }

    private static void indexImagesUnder(Path dir, boolean recursive) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(CardArtManager::isImagePath)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String key = name
                                .replace(".png", "")
                                .replace(".webp", "")
                                .replace(".jpg", "")
                                .replace(".jpeg", "");
                        DISK_INDEX.put(key, name);
                    });
        } catch (Exception ignored) {
        }
    }

    private static long countImagesUnder(Path dir, boolean recursive) throws IOException {
        if (dir == null || !Files.exists(dir)) return 0;
        try (var stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(CardArtManager::isImagePath)
                    .count();
        }
    }

    private static long bytesUnder(Path dir, boolean recursive) throws IOException {
        if (dir == null || !Files.exists(dir)) return 0;
        try (var stream = recursive ? Files.walk(dir) : Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(CardArtManager::isImagePath)
                    .mapToLong(f -> {
                        try { return Files.size(f); } catch (Exception e) { return 0; }
                    }).sum();
        }
    }

    private static Iterable<String> artKeyCandidates(String artKey) {
        return artKeyCandidates(artKey, List.of());
    }

    private static Iterable<String> artKeyCandidates(String artKey, List<String> fallbackKeys) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();

        addArtKeyCandidate(keys, artKey);

        if (fallbackKeys != null) {
            for (String fallback : fallbackKeys) {
                addArtKeyCandidate(keys, fallback);
            }
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
                try { Minecraft.getInstance().getTextureManager().release(old.id()); } catch (Throwable ignored) {}
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
            try { Minecraft.getInstance().getTextureManager().release(old.id()); } catch (Throwable ignored) {}
            try { old.tex().close(); } catch (Throwable ignored) {}
        }

        // If DISK_INDEX had a stale mapping, drop it so resolveCachedFile re-discovers
        DISK_INDEX.remove(artKey);
        saveIndexAsync();
    }

    private static int deleteCachedFiles(String artKey) {
        int deleted = 0;
        try {
            for (String candidate : artKeyCandidates(artKey)) {
                for (Path dir : artKeyDeleteDirs()) {
                    for (String ext : new String[]{"webp", "png", "jpg", "jpeg"}) {
                        Path file = dir.resolve(candidate + "." + ext);
                        if (Files.deleteIfExists(file)) {
                            deleted++;
                        }
                    }
                }
                DISK_INDEX.remove(candidate);
            }
            saveIndexAsync();
        } catch (Throwable ignored) {
        }
        return deleted;
    }

    private static List<Path> artKeyDeleteDirs() {
        java.util.ArrayList<Path> dirs = new java.util.ArrayList<>();
        dirs.add(mainArtCacheDir());
        dirs.add(customArtRoot());
        try (var stream = Files.list(customArtRoot())) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        } catch (Exception ignored) {
        }
        dirs.add(legacyArtCacheDir());
        return dirs;
    }

    public static void tryLoadNow(String artKey) {
        if (artKey == null || artKey.isBlank()) return;

        Path file = resolveAnyCachedFile(artKey);
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
