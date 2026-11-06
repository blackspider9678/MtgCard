package com.spider.mtgcard.client.java;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spider.mtgcard.shared.CardArtCommon;
import com.spider.mtgcard.shared.MtgCardPaths;
import com.spider.mtgcard.util.ArtImageStorage;
import com.spider.mtgcard.util.StackData;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
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
 * - Supports custom world art (tcg_meta/mtg_meta world_art_front/world_art_back).
 * - Still supports image URLs (image_png/card_faces[i].image_png/faces[i].image_png).
 * - Writes art_index.json in integrated server; in remote MP we rely on server pushes.
 *
 * NEW:
 * - Requests are QUEUED and sent gradually (one-by-one / rate-limited) to avoid spamming 80 at once.
 */
public final class CardArtManager {

    public record TextureRef(ResourceLocation id, int texW, int texH, DynamicTexture tex) {}

    private static final Map<String, TextureRef> TEX = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> DISK_INDEX = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> ART_SET_INDEX = new ConcurrentHashMap<>();
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mtg-card-textures");
        t.setDaemon(true);
        return t;
    });
    private static final Gson GSON = new Gson();
    private static final Object INDEX_WRITE_LOCK = new Object();
    private static final Type INDEX_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    // artKey -> last request time (ms)
    private static final ConcurrentMap<String, Long> IN_FLIGHT = new ConcurrentHashMap<>();

    // -----------------------
    // NEW: request queue
    // -----------------------
    private record PendingReq(String requestKey, String game, String artKey, String url, String fileName, String fallbackKeys, String setCode) {}
    private record PendingDiskLoad(String textureKey, Path file, byte[] bytes) {}

    private static final ConcurrentLinkedQueue<PendingReq> PENDING = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingDiskLoad> PENDING_DISK_LOADS = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger ACTIVE_DISK_LOADS = new AtomicInteger();

    // Keep initial container loads gradual. Image decode/upload is expensive enough that
    // even a small burst is visible when a deckbox or card database first opens.
    private static final int MAX_SENDS_PER_TICK = 1;
    private static final int MAX_INFLIGHT = 4;
    private static final int MAX_DISK_LOADS_PER_TICK = 1;
    private static final int MAX_ACTIVE_DISK_LOADS = 2;
    private static final long REQUEST_COOLDOWN_MS = 1500;
    private static final ConcurrentMap<String, PendingReq> REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Integer> FAILURES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();
    private static final Set<String> RECEIVING = ConcurrentHashMap.newKeySet();
    private static final Set<String> CACHE_LOOKUPS = ConcurrentHashMap.newKeySet();
    private static volatile long sessionGeneration;

    private static void retryRequest(String key) {
        IN_FLIGHT.remove(key);
        PendingReq request = REQUESTS.get(key);
        int failures = FAILURES.merge(key, 1, Integer::sum);
        System.out.println("[MTGCard] Art retry " + key + " failure=" + failures);
        if (request == null || failures > 3) return;
        RETRY_AFTER.put(key, System.currentTimeMillis() + REQUEST_COOLDOWN_MS);
        if (QUEUED.add(key)) PENDING.add(request);
    }

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
        if (IN_FLIGHT.containsKey(requestKey) || RECEIVING.contains(requestKey)
                || FAILURES.getOrDefault(requestKey, 0) > 3) return;

        // If it's already queued, do nothing
        if (!QUEUED.add(requestKey)) return;

        // Store filename mapping early (so resolveCachedFile() has something)
        if (fileName != null && !fileName.isBlank()) {
            DISK_INDEX.putIfAbsent(scopedArtKey(safeGame, artKey), fileName);
            saveIndexAsync(safeGame);
        }
        if (setCode != null && !setCode.isBlank()) {
            ART_SET_INDEX.put(requestKey, MtgCardPaths.sanitizeSetFolder(setCode));
        }

        PendingReq request = new PendingReq(
                requestKey,
                safeGame,
                artKey,
                url == null ? "" : url,
                fileName == null ? "" : fileName,
                fallbackKeys == null ? "" : fallbackKeys,
                setCode == null ? "" : setCode
        );
        REQUESTS.put(requestKey, request);
        PENDING.add(request);
    }

    /**
     * Pump a few queued requests per tick.
     * Call this from ClientTickEvents.END_CLIENT_TICK.
     */
    public static void pumpQueue() {
        var mc = Minecraft.getInstance();
        if (mc == null) return;

        pumpDiskLoads();

        // Only pump when we’re in-world & networking is live
        if (mc.player == null || mc.getConnection() == null) return;

        long now = System.currentTimeMillis();
        IN_FLIGHT.forEach((key, sent) -> {
            if (now - sent >= 30000 && !RECEIVING.contains(key)) retryRequest(key);
        });
        int started = 0;
        int remaining = PENDING.size();

        while (remaining-- > 0 && started < MAX_SENDS_PER_TICK && IN_FLIGHT.size() < MAX_INFLIGHT) {
            PendingReq req = PENDING.poll();
            if (req == null) break;
            if (RETRY_AFTER.getOrDefault(req.requestKey(), 0L) > now) {
                PENDING.add(req);
                continue;
            }
            if (RECEIVING.contains(req.requestKey()) || LOADING.contains(req.requestKey())) {
                PENDING.add(req);
                continue;
            }
            if (TEX.containsKey(req.requestKey())) {
                QUEUED.remove(req.requestKey());
                continue;
            }

            // Allow it to be queued again in the future if needed
            QUEUED.remove(req.requestKey());

            // cooldown gate (prevents hammering one bad key)
            if (!shouldRequestNow(req.requestKey())) {
                continue;
            }

            // send request packet (server will fetch & respond)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.spider.mtgcard.net.ArtPackets.ArtRequest(req.artKey(), req.url(), req.fallbackKeys(), req.setCode(), req.game())
            );

            started++;
        }
    }

    private static void pumpDiskLoads() {
        int started = 0;
        while (started < MAX_DISK_LOADS_PER_TICK
                && ACTIVE_DISK_LOADS.get() < MAX_ACTIVE_DISK_LOADS) {
            PendingDiskLoad pending = PENDING_DISK_LOADS.poll();
            if (pending == null) break;

            ACTIVE_DISK_LOADS.incrementAndGet();
            started++;
            submitDiskLoad(pending);
        }
    }

    /** Clears queued/inflight state (call on disconnect/world switch if you want). */
    public static void clearRequestState() {
        sessionGeneration++;
        PENDING.clear();
        PENDING_DISK_LOADS.clear();
        QUEUED.clear();
        IN_FLIGHT.clear();
        LOADING.clear();
        REQUESTS.clear();
        FAILURES.clear();
        RETRY_AFTER.clear();
        RECEIVING.clear();
        CACHE_LOOKUPS.clear();
        DISK_INDEX.clear();
        ART_SET_INDEX.clear();
    }

    // ---- lifecycle ----
    public static void init() {
        loadIndex();

    }

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

    private static List<Path> mainArtSearchDirs(String game) {
        return List.of(mainArtCacheDir(game));
    }

    private static List<Path> customArtSearchDirs(String game, String setCode) {
        return List.of(customArtDir(game, setCode));
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
        String game = StackData.readCustom(stack).getCompound("tcg_meta").getString("game");
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

    private static boolean isIntegrated(Minecraft mc) {
        return mc.getSingleplayerServer() != null && mc.getCurrentServer() == null;
    }

    private static boolean usePersistentArtCache() {
        return true;
    }

    private static void loadIndex() {
        var file = indexFile();
        if (file == null || !Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> m = GSON.fromJson(r, INDEX_TYPE);
            if (m != null) m.forEach((key, value) -> DISK_INDEX.put(scopedArtKey("mtg", key), value));
        } catch (Exception ignored) {}
    }

    public static void saveIndexAsync() {
        saveIndexAsync(MtgCardPaths.GAME_MTG);
    }

    private static void saveIndexAsync(String game) {
        var file = indexFile(game);
        if (file == null) return;
        Map<String, String> snapshot = new java.util.TreeMap<>();
        String prefix = MtgCardPaths.sanitizeGameFolder(game) + ":";
        DISK_INDEX.forEach((key, value) -> {
            if (key.startsWith(prefix)) snapshot.put(key.substring(prefix.length()), value);
        });
        IO.submit(() -> {
            synchronized (INDEX_WRITE_LOCK) {
                try {
                    Files.createDirectories(file.getParent());
                    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                    Files.writeString(temp, GSON.toJson(snapshot), StandardCharsets.UTF_8);
                    try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
                } catch (Exception ignored) {}
            }
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
        if (StackData.readCustom(stack).getCompound("mtg_meta").getBoolean("mtg_hidden")) {
            var sleeve = com.spider.mtgcard.api.CardSleeves.get(stack);
            if (sleeve.isPresent()) return new TextureRef(sleeve.get().backTexture(), 16, 16, null);
        }
        CompoundTag root = StackData.readCustom(stack);
        CompoundTag meta = artMetaForStack(root);
        String game = gameForStack(stack);

        // world art path
        String worldKey = extractWorldArtKey(meta, faceIndex);

        if (worldKey != null && !worldKey.isBlank()) {
            String setCode = meta.getString("set");
            return getOrRequestWorld(game, worldKey.trim(), setCode);
        }

        // Scryfall path
        String artKey = CardArtCommon.computeArtKey(meta, faceIndex);
        String textureKey = scopedArtKey(game, artKey);

        TextureRef cached = getLiveCached(textureKey);
        if (cached != null) return cached;


        if (CACHE_LOOKUPS.contains(textureKey) || LOADING.contains(textureKey) || RECEIVING.contains(textureKey)
                || QUEUED.contains(textureKey) || IN_FLIGHT.containsKey(textureKey)
                || FAILURES.getOrDefault(textureKey, 0) > 3) return null;
        String sourceUrl = CardArtCommon.extractImageUrl(meta, faceIndex);
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            REQUESTS.putIfAbsent(textureKey, new PendingReq(textureKey, game, artKey, sourceUrl,
                    artKey + ".webp", "", ""));
        }
        if (!CACHE_LOOKUPS.add(textureKey)) return null;
        long generation = sessionGeneration;
        IO.submit(() -> {
            try {
                if (generation != sessionGeneration) return;
                if (!usePersistentArtCache()) {
                    enqueueRequest(game, artKey, sourceUrl, artKey + ".webp",
                            "", "");
                    return;
                }
                Path file = resolveMainCachedFile(game, artKey);
                if (generation != sessionGeneration) return;

                if (Files.exists(file)) {
                    DISK_INDEX.put(scopedArtKey(game, artKey), file.getFileName().toString());
                    saveIndexAsync(game);

                    queueDiskLoad(textureKey, file);
                    return;
                }


                String url = CardArtCommon.extractImageUrl(meta, faceIndex);
                if (url == null || url.isEmpty()) return;

                // IMPORTANT: queue instead of sending immediately
                String fileName = DISK_INDEX.getOrDefault(scopedArtKey(game, artKey), artKey + ".webp");
                enqueueRequest(game, artKey, url, fileName, "", "");
            } catch (Exception error) {
                if (generation == sessionGeneration) {
                    System.out.println("[MTGCard] Cache lookup failed for " + textureKey + ": " + error);
                    retryRequest(textureKey);
                }
            } finally {
                if (generation == sessionGeneration) CACHE_LOOKUPS.remove(textureKey);
            }
        });
        return null;
    }

    private static CompoundTag artMetaForStack(CompoundTag root) {
        if (root == null) return new CompoundTag();

        CompoundTag fallback = root.getCompound("mtg_meta");
        CompoundTag preferred = root.getCompound("tcg_meta");

        CompoundTag merged = fallback.copy();
        for (String key : preferred.getAllKeys()) {
            var value = preferred.get(key);
            if (value == null) continue;
            if (value instanceof StringTag && preferred.getString(key).isBlank() && merged.contains(key)) {
                continue;
            }
            if (value instanceof ListTag list && list.size() == 0 && merged.contains(key)) {
                continue;
            }
            merged.put(key, value.copy());
        }
        return merged;
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

        if (CACHE_LOOKUPS.contains(textureKey) || LOADING.contains(textureKey) || RECEIVING.contains(textureKey)
                || QUEUED.contains(textureKey) || IN_FLIGHT.containsKey(textureKey)
                || FAILURES.getOrDefault(textureKey, 0) > 3) return null;
        String safeSetCode = MtgCardPaths.sanitizeSetFolder(setCode);
        REQUESTS.putIfAbsent(textureKey, new PendingReq(textureKey, game, artKey, "",
                artKey + ".webp", "", safeSetCode));
        ART_SET_INDEX.put(textureKey, safeSetCode);

        if (!CACHE_LOOKUPS.add(textureKey)) return null;
        long generation = sessionGeneration;
        IO.submit(() -> {
            try {
                if (generation != sessionGeneration) return;
                if (!usePersistentArtCache()) {
                    enqueueRequest(game, artKey, "", artKey + ".webp", "", safeSetCode);
                    return;
                }
                Path file = resolveCustomCachedFile(game, artKey, safeSetCode);
                if (generation != sessionGeneration) return;

                if (Files.exists(file)) {
                    DISK_INDEX.put(scopedArtKey(game, artKey), file.getFileName().toString());
                    saveIndexAsync(game);

                    queueDiskLoad(textureKey, file);
                    return;
                }


                DISK_INDEX.putIfAbsent(scopedArtKey(game, artKey), artKey + ".webp");
                saveIndexAsync(game);

                // IMPORTANT: queue instead of sending immediately
                enqueueRequest(game, artKey, "", artKey + ".webp", "", safeSetCode);
            } catch (Exception error) {
                if (generation == sessionGeneration) {
                    System.out.println("[MTGCard] Cache lookup failed for " + textureKey + ": " + error);
                    retryRequest(textureKey);
                }
            } finally {
                if (generation == sessionGeneration) CACHE_LOOKUPS.remove(textureKey);
            }
        });
        return null;
    }

    private static String extractWorldArtKey(CompoundTag meta, int faceIndex) {
        String worldKey = "";
        if (faceIndex <= 0) {
            worldKey = meta.getString("world_art_front");
            if (worldKey.isBlank()) worldKey = meta.getString("world_art");
            if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, 0);
            return worldKey;
        }

        worldKey = meta.getString("world_art_back");
        if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, faceIndex);
        if (worldKey.isBlank()) {
            worldKey = meta.getString("world_art_front");
            if (worldKey.isBlank()) worldKey = meta.getString("world_art");
            if (worldKey.isBlank()) worldKey = extractFaceWorldArt(meta, 0);
        }
        return worldKey;
    }

    private static String extractFaceWorldArt(CompoundTag meta, int faceIndex) {
        String value = extractFaceWorldArt(meta, "card_faces", faceIndex);
        if (!value.isBlank()) return value;
        return extractFaceWorldArt(meta, "faces", faceIndex);
    }

    private static String extractFaceWorldArt(CompoundTag meta, String facesKey, int faceIndex) {
        ListTag faces = meta.getList(facesKey, net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (faces.isEmpty()) return "";
        int idx = Math.max(0, Math.min(faceIndex, faces.size() - 1));
        return faces.getCompound(idx).getString("world_art");
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
        if (imgBytes == null || imgBytes.length == 0) {
            retryRequest(textureKey);
            return;
        }
        if (!RECEIVING.add(textureKey)) return;
        String setCode = ART_SET_INDEX.get(textureKey);
        boolean persist = usePersistentArtCache();
        Path destination = persist
                ? (setCode == null || setCode.isBlank() ? mainArtCacheDir(safeGame) : customArtDir(safeGame, setCode))
                : null;
        long generation = sessionGeneration;
        IO.submit(() -> {
            try {
                if (generation == sessionGeneration)
                    storeArtBytes(safeGame, artKey, setCode, imgBytes, destination, generation, persist);
            } finally {
                if (generation == sessionGeneration) {
                    RECEIVING.remove(textureKey);
                    IN_FLIGHT.remove(textureKey);
                }
            }
        });
    }

    private static void storeArtBytes(String game, String artKey, String setCode, byte[] imgBytes, Path destination,
                                      long generation, boolean persist) {
        if (artKey == null || artKey.isBlank() || imgBytes == null || imgBytes.length == 0) {
            return;
        }

        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String safeArtKey = CardArtCommon.sanitizeArtKey(artKey);
        if (safeArtKey.isBlank()) {
            return;
        }

        String textureKey = scopedArtKey(safeGame, safeArtKey);
        String safeSetCode = setCode == null || setCode.isBlank() ? "" : MtgCardPaths.sanitizeSetFolder(setCode);
        if (!safeSetCode.isBlank()) {
            ART_SET_INDEX.put(textureKey, safeSetCode);
        }

        if (!persist) {
            if (generation == sessionGeneration && LOADING.add(textureKey)) {
                PENDING_DISK_LOADS.add(new PendingDiskLoad(textureKey, null, imgBytes));
            }
            return;
        }

        try {
            String ext = ArtImageStorage.detectExt(imgBytes);
            if ("bin".equals(ext)) {
                ext = ArtImageStorage.normalizeExt(safeArtKey);
            }
            Path dir = destination;
            Path file = ArtImageStorage.write(dir, safeArtKey, new ArtImageStorage.StoredArt(imgBytes, ext));
            String fileName = file.getFileName().toString();
            if (generation != sessionGeneration) return;


            DISK_INDEX.put(scopedArtKey(safeGame, safeArtKey), fileName);
            saveIndexAsync(safeGame);

            if (LOADING.add(textureKey)) {
                PENDING_DISK_LOADS.add(new PendingDiskLoad(textureKey, file, imgBytes));
            }
        } catch (Exception e) {
            System.out.println("[MTGCard] Failed to store art key=" + textureKey + ": " + e);
            if (generation == sessionGeneration) retryRequest(textureKey);
        }
    }

    private static Path resolveAnyCachedFile(String artKey) {
        Path main = resolveMainCachedFile(MtgCardPaths.GAME_MTG, artKey);
        if (Files.exists(main)) return main;

        String setCode = ART_SET_INDEX.getOrDefault(scopedArtKey(MtgCardPaths.GAME_MTG, artKey), "");
        Path custom = resolveCustomCachedFile(MtgCardPaths.GAME_MTG, artKey, setCode);
        if (Files.exists(custom)) return custom;

        return main;
    }

    private static Path resolveMainCachedFile(String game, String artKey) {
        Path dir = mainArtCacheDir(game);
        Path indexed = resolveIndexedFile(game, dir, artKey);
        if (indexed != null) return indexed;
        Path exact = resolveExactFile(dir, artKey);
        return exact != null ? exact : dir.resolve(artKey + ".webp");
    }

    private static Path resolveCustomCachedFile(String game, String artKey, String setCode) {
        Path dir = customArtDir(game, setCode);
        Path exact = resolveExactFile(dir, artKey);
        return exact != null ? exact : dir.resolve(artKey + ".webp");
    }

    private static Path resolveIndexedFile(String game, Path dir, String artKey) {
        String mapped = DISK_INDEX.get(scopedArtKey(game, artKey));
        if (mapped == null || mapped.isBlank()) return null;

        Path path = dir.resolve(mapped).normalize();
        return path.startsWith(dir.normalize()) && Files.isRegularFile(path) ? path : null;
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
                        DISK_INDEX.put(scopedArtKey("mtg", key), name);
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
        String key = CardArtCommon.sanitizeArtKey(artKey);
        return key.isBlank() ? List.of() : List.of(key);
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
                ni.setPixelRGBA(x, y, abgr);
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

        PENDING_DISK_LOADS.add(new PendingDiskLoad(artKey, file, null));
    }

    private static void submitDiskLoad(PendingDiskLoad pending) {
        String artKey = pending.textureKey();
        Path file = pending.file();
        long generation = sessionGeneration;
        IO.submit(() -> {
            NativeImage img = null;
            try (var in = pending.bytes() == null ? Files.newInputStream(file)
                    : new ByteArrayInputStream(pending.bytes())) {
                BufferedImage bi = readAnyImage(in);
                img = bufferedToNative(bi);
                NativeImage finalImg = img;
                img = null;

                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    try {
                        if (generation == sessionGeneration) bindTexture(artKey, finalImg);
                        else finalImg.close();
                    } finally {
                        if (generation == sessionGeneration) LOADING.remove(artKey);
                        ACTIVE_DISK_LOADS.decrementAndGet();
                    }
                });
            } catch (Throwable t) {
                if (img != null) {
                    try { img.close(); } catch (Throwable ignored) {}
                }
                if (generation == sessionGeneration) {
                    removeCorruptCachedArt(artKey, file, t);
                    LOADING.remove(artKey);
                }
                ACTIVE_DISK_LOADS.decrementAndGet();
            }
        });
    }

    private static void removeCorruptCachedArt(String textureKey, Path file, Throwable reason) {
        System.out.println("[MTGCard] Failed to load art key=" + textureKey + " from " + file + ": " + reason);

        String rawKey = unscopedArtKey(textureKey);
        DISK_INDEX.remove(textureKey);
        if (file != null && file.getFileName() != null) {
            DISK_INDEX.remove(scopedArtKey(scopedGame(textureKey), stripImageExt(file.getFileName().toString())));
        }
        saveIndexAsync(scopedGame(textureKey));

        if (file != null) {
            try {
                if (Files.deleteIfExists(file)) {
                    System.out.println("[MTGCard] Deleted corrupt cached art " + file);
                }
            } catch (Throwable deleteFailure) {
                System.out.println("[MTGCard] Could not delete corrupt cached art " + file + ": " + deleteFailure);
            }
        }

        IN_FLIGHT.remove(textureKey);
        QUEUED.remove(textureKey);
        retryRequest(textureKey);
    }

    private static String scopedGame(String textureKey) {
        int colon = textureKey == null ? -1 : textureKey.indexOf(':');
        return colon > 0 ? textureKey.substring(0, colon) : MtgCardPaths.GAME_MTG;
    }

    private static String unscopedArtKey(String textureKey) {
        int colon = textureKey == null ? -1 : textureKey.indexOf(':');
        return colon >= 0 ? textureKey.substring(colon + 1) : textureKey;
    }

    private static String stripImageExt(String fileName) {
        if (fileName == null) {
            return "";
        }

        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".webp", ".png", ".jpg", ".jpeg"}) {
            if (lower.endsWith(ext)) {
                return fileName.substring(0, fileName.length() - ext.length());
            }
        }
        return fileName;
    }

    private static void bindTexture(String artKey, NativeImage img) {
        try {
            ResourceLocation texId = ResourceLocation.fromNamespaceAndPath("mtgcard", "card/" + sha1(artKey));

            TextureRef old = TEX.remove(artKey);
            if (old != null) {
                try { Minecraft.getInstance().getTextureManager().release(old.id()); } catch (Throwable ignored) {}
                try { old.tex().close(); } catch (Throwable ignored) {}
            }

            DynamicTexture tex = new DynamicTexture(img);
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
        for (String key : REQUESTS.keySet()) {
            if (unscopedArtKey(key).equals(artKey)) {
                FAILURES.remove(key);
                RETRY_AFTER.remove(key);
                IN_FLIGHT.remove(key);
                QUEUED.remove(key);
            }
        }

        // Stop any “cooldown/inflight” suppression
        IN_FLIGHT.remove(artKey);
        QUEUED.remove(artKey);

        // Remove pending queue entries for this key (optional but nice)
        PENDING.removeIf(r -> r.artKey().equals(artKey));

        // Stored textures use game-qualified keys; invalidate their actual entries.
        for (String key : TEX.keySet()) {
            if (!unscopedArtKey(key).equals(artKey)) continue;
            TextureRef old = TEX.remove(key);
            if (old != null) {
                try { Minecraft.getInstance().getTextureManager().release(old.id()); } catch (Throwable ignored) {}
                try { old.tex().close(); } catch (Throwable ignored) {}
            }
        }

        // If DISK_INDEX had a stale mapping, drop it so resolveCachedFile re-discovers
        DISK_INDEX.keySet().removeIf(key -> unscopedArtKey(key).equals(artKey));
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
                DISK_INDEX.remove(scopedArtKey("mtg", candidate));
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
        return dirs;
    }

    public static void tryLoadNow(String artKey) {
        if (artKey == null || artKey.isBlank()) return;

        Path file = resolveAnyCachedFile(artKey);
        if (Files.exists(file)) {
            DISK_INDEX.put(scopedArtKey("mtg", artKey), file.getFileName().toString());
            saveIndexAsync();
            queueDiskLoad(scopedArtKey("mtg", artKey), file);
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
