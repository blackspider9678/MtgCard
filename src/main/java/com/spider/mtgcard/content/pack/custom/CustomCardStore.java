// com.spider.mtgcard.content.pack.custom.CustomCardStore.java
package com.spider.mtgcard.content.pack.custom;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.net.CustomCardPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CustomCardStore {
    private final Path artDir;
    private final Path json;
    private final Map<String, CardMeta> byId = new HashMap<>();
    public String artKeyFront = "";
    public String artKeyBack  = "";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final ExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mtgcard-custom-card-save");
                t.setDaemon(true);
                return t;
            });


    public CustomCardStore(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve("mtgcard");
        this.artDir = root.resolve("art");
        this.json   = root.resolve("custom").resolve("cards.json");
        try { Files.createDirectories(artDir); Files.createDirectories(json.getParent()); } catch (Exception ignored) {}
        load();
    }

    public synchronized int addAllFromClient(List<CustomCardPackets.BatchEntry> entries, ServerPlayer who) {
        int added = 0;

        for (var be : entries) {
            if (be == null) continue;

            String id = sanitizeClientId(be.id());
            if (id == null) continue;

            CardMeta meta = CardMeta.fromBatch(id, be);
            byId.put(id, meta);
            added++;
        }

        // Save once for the whole batch
        save();
        return added;
    }

    private boolean dirty = false;

    public synchronized String addFromClient(CustomCardPackets.BatchEntry be, ServerPlayer who) {
        try {
            String id = sanitizeClientId(be.id());
            if (id == null) return null;

            CardMeta meta = CardMeta.fromBatch(id, be);
            byId.put(id, meta);
            dirty = true;               // ✅ mark dirty, no save here
            return id;
        } catch (Exception e) {
            System.out.println("[MTGCard] addFromClient failed: " + e);
            return null;
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        saveSnapshot(new ArrayList<>(byId.values()));
    }

    public synchronized CompletableFuture<Boolean> saveIfDirtyAsync() {
        if (!dirty) return CompletableFuture.completedFuture(false);
        dirty = false;
        return saveSnapshotAsync(new ArrayList<>(byId.values()));
    }

    private static String sanitizeClientId(String id) {
        if (id == null) return null;
        id = id.trim();
        if (id.isEmpty()) return null;
        // allow only safe chars for filenames/nbt/json; length cap
        if (id.length() > 64) id = id.substring(0, 64);
        if (!id.matches("[a-zA-Z0-9_\\-]+")) return null;
        return id;
    }


    public synchronized Collection<CardMeta> all() { return List.copyOf(byId.values()); }

    public synchronized CardMeta getById(String id) {
        if (id == null || id.isBlank()) return null;
        return byId.get(id);
    }

    public synchronized List<CardMeta> findByName(String name, String setFilter) {
        if (name == null || name.isBlank()) return List.of();

        String needleName = name.trim().toLowerCase(Locale.ROOT);
        String needleSet = setFilter == null || setFilter.isBlank()
                ? null
                : setFilter.trim().toLowerCase(Locale.ROOT);

        ArrayList<CardMeta> matches = new ArrayList<>();
        for (CardMeta meta : byId.values()) {
            if (meta == null) continue;
            if (meta.name == null || !meta.name.trim().toLowerCase(Locale.ROOT).equals(needleName)) continue;
            if (needleSet != null) {
                String set = meta.set == null ? "" : meta.set.trim().toLowerCase(Locale.ROOT);
                if (!set.equals(needleSet)) continue;
            }
            matches.add(meta);
        }
        return matches;
    }

    public synchronized RemoveResult removeByIds(Collection<String> ids, boolean removeOwnedArt) {
        if (ids == null || ids.isEmpty()) {
            return new RemoveResult(List.of(), List.of(), 0, 0, 0, false);
        }

        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) wanted.add(id);
        }
        if (wanted.isEmpty()) {
            return new RemoveResult(List.of(), List.of(), 0, 0, 0, false);
        }

        ArrayList<CardMeta> removed = new ArrayList<>();
        for (String id : wanted) {
            CardMeta meta = byId.remove(id);
            if (meta != null) {
                removed.add(meta);
            }
        }

        if (removed.isEmpty()) {
            return new RemoveResult(List.of(), List.of(), 0, 0, 0, false);
        }

        LinkedHashSet<String> artKeys = new LinkedHashSet<>();
        for (CardMeta meta : removed) {
            collectArtKeys(meta, artKeys);
        }

        int artFilesDeleted = 0;
        int artKeysRemoved = 0;
        int artKeysKept = 0;
        ArrayList<String> invalidated = new ArrayList<>();

        if (removeOwnedArt) {
            for (String key : artKeys) {
                if (key == null || key.isBlank()) continue;

                if (isArtKeyUsed(key)) {
                    artKeysKept++;
                    continue;
                }

                artFilesDeleted += deleteArtFiles(key);
                artKeysRemoved++;
                invalidated.add(key);
            }
        } else {
            artKeysKept = artKeys.size();
        }

        boolean saved = saveSnapshot(new ArrayList<>(byId.values()));
        dirty = false;
        return new RemoveResult(List.copyOf(removed), List.copyOf(invalidated), artFilesDeleted, artKeysRemoved, artKeysKept, saved);
    }

    public record RemoveResult(
            List<CardMeta> removed,
            List<String> invalidatedArtKeys,
            int artFilesDeleted,
            int artKeysRemoved,
            int artKeysKept,
            boolean saved
    ) {}

    // Replace your load() and save() with these:

    @SuppressWarnings("unchecked")
    private void load() {
        byId.clear();
        try {
            if (Files.exists(json)) {
                String s = Files.readString(json, StandardCharsets.UTF_8);
                CardMeta[] arr = GSON.fromJson(s, CardMeta[].class);
                if (arr != null) {
                    for (CardMeta m : arr) {
                        if (m != null && m.id != null && !m.id.isBlank()) {
                            byId.put(m.id, m);
                        }
                    }
                }
            } else {
                // ensure parent dirs exist; file will be created on first save()
                Files.createDirectories(json.getParent());
            }
        } catch (Exception e) {
            // Optional: log once so you know why it failed
            Mtgcard.LOGGER.warn("[MTGCard] Failed to load {}: {}", json, e.toString());
        }
    }

    private void save() {
        saveSnapshot(new ArrayList<>(byId.values()));
    }

    private CompletableFuture<Boolean> saveSnapshotAsync(List<CardMeta> snapshot) {
        List<CardMeta> stableSnapshot = List.copyOf(snapshot);
        return CompletableFuture.supplyAsync(() -> saveSnapshot(stableSnapshot), SAVE_EXECUTOR);
    }

    private boolean saveSnapshot(List<CardMeta> snapshot) {
        try {
            Files.createDirectories(json.getParent());
            String s = GSON.toJson(snapshot);
            Path tmp = json.resolveSibling(json.getFileName().toString() + ".tmp");
            Files.writeString(tmp, s, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            moveAtomically(tmp, json);
            return true;
        } catch (Exception e) {
            Mtgcard.LOGGER.error("[MTGCard] Failed to save {}: {}", json, e.toString());
            return false;
        }
    }

    private static void moveAtomically(Path tmp, Path target) throws java.io.IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Server-side DTO (no networking dependency). */


    private static BufferedImage resize(BufferedImage src, int max) {
        double s = Math.min(1.0, (double)max / Math.max(src.getWidth(), src.getHeight()));
        if (s >= 1.0) return src;
        int w = (int)Math.round(src.getWidth() * s), h = (int)Math.round(src.getHeight() * s);
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.drawImage(src, 0, 0, w, h, null); g.dispose();
        return img;
    }

    private boolean artExists(String artKey) {
        if (artKey == null || artKey.isBlank()) return false;
        try (var s = Files.list(artDir)) {
            final String prefix = artKey + ".";
            return s.anyMatch(p -> p.getFileName().toString().startsWith(prefix));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void collectArtKeys(CardMeta meta, Set<String> out) {
        if (meta == null || out == null) return;

        addArtKey(out, normalizeCustomKey(meta.artKeyFront, meta.id, 0));
        addArtKey(out, normalizeCustomKey(meta.artKeyBack, meta.id, 1));
    }

    private static void addArtKey(Set<String> out, String key) {
        if (key != null && !key.isBlank()) {
            out.add(key.trim());
        }
    }

    private boolean isArtKeyUsed(String key) {
        if (key == null || key.isBlank()) return false;
        String needle = key.trim();

        for (CardMeta meta : byId.values()) {
            if (meta == null) continue;
            if (needle.equals(normalizeCustomKey(meta.artKeyFront, meta.id, 0))) return true;
            if (needle.equals(normalizeCustomKey(meta.artKeyBack, meta.id, 1))) return true;
        }
        return false;
    }

    private int deleteArtFiles(String artKey) {
        if (artKey == null || artKey.isBlank()) return 0;

        int deleted = 0;
        for (String ext : List.of("webp", "png", "jpg", "jpeg")) {
            try {
                if (Files.deleteIfExists(artDir.resolve(artKey + "." + ext))) {
                    deleted++;
                }
            } catch (Exception e) {
                Mtgcard.LOGGER.warn("[MTGCard] Failed to delete custom art {}.{}: {}", artKey, ext, e.toString());
            }
        }
        return deleted;
    }

    private static String normalizeCustomKey(String key, String customId, int face) {
        String fallback = "custom_" + (customId == null ? "" : customId.trim()) + "_f" + face;
        if (key == null || key.isBlank()) return fallback;

        String normalized = key.trim();
        if (!normalized.startsWith("custom_")) normalized = "custom_" + normalized;
        return normalized;
    }

    // Minimal meta POJO you can extend later
    public static final class CardMeta {
        public String id, name, manaCost, typeLine, rarity, set, oracleText, power, toughness, loyalty;
        public boolean doubleFaced;
        public String backName, backTypeLine, backOracleText, backPower, backToughness, backLoyalty;
        public String artKeyFront = "";
        public String artKeyBack  = "";

        static CardMeta fromBatch(String id, CustomCardPackets.BatchEntry e) {
            CardMeta m = new CardMeta();
            m.id = id;

            m.name = e.name(); m.manaCost = e.manaCost(); m.typeLine = e.typeLine(); m.rarity = e.rarity();
            m.set = e.set(); m.oracleText = e.oracleText(); m.power = e.power(); m.toughness = e.toughness(); m.loyalty = e.loyalty();

            m.doubleFaced = e.doubleFaced();
            m.backName = e.backName(); m.backTypeLine = e.backTypeLine(); m.backOracleText = e.backOracleText();
            m.backPower = e.backPower(); m.backToughness = e.backToughness(); m.backLoyalty = e.backLoyalty();

            // ✅ Persist art keys
            String canonicalFront = "custom_" + id + "_f0";
            String canonicalBack  = "custom_" + id + "_f1";

            String inFront = e.artKeyFront();
            String inBack  = e.artKeyBack();

            m.artKeyFront = (inFront == null || inFront.isBlank()) ? canonicalFront : inFront.trim();
            m.artKeyBack  = (inBack  == null || inBack.isBlank())  ? canonicalBack  : inBack.trim();

            // ✅ If client sent old-style "<id>_f0", upgrade it
            if (!m.artKeyFront.startsWith("custom_")) m.artKeyFront = canonicalFront;
            if (!m.artKeyBack.startsWith("custom_"))  m.artKeyBack  = canonicalBack;

            return m;
        }
    }
}
