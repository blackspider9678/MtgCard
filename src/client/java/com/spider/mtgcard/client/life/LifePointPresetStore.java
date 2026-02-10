package com.spider.mtgcard.client.life;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class LifePointPresetStore {

    // bumping schema is optional; your loader already accepts both formats.
    // We keep it here for future use.
    public static final int CURRENT_SCHEMA = 2;

    public static final class Preset {
        public String name = "Preset";
        public int playerColor = 0xE8E8E8;
        public int lifeColor   = 0xFFFFFF;
        public String iconKey  = "none";
        public String formatKey = "commander";

        // NEW: commander lethal threshold (defaults to 21)
        public int cmdLethal = 21;

        // optional (null if not stored)
        public Map<String, Integer> customCounters = null;

        // Gson needs a no-arg ctor
        public Preset() {}

        // Back-compat constructor (old call sites)
        public Preset(String name, int playerColor, int lifeColor, String iconKey, String formatKey,
                      Map<String, Integer> customCounters) {
            this.name = name;
            this.playerColor = playerColor;
            this.lifeColor = lifeColor;
            this.iconKey = iconKey;
            this.formatKey = formatKey;
            this.cmdLethal = 21;
            this.customCounters = customCounters;
        }

        // New constructor (includes cmdLethal)
        public Preset(String name, int playerColor, int lifeColor, String iconKey, String formatKey,
                      int cmdLethal, Map<String, Integer> customCounters) {
            this.name = name;
            this.playerColor = playerColor;
            this.lifeColor = lifeColor;
            this.iconKey = iconKey;
            this.formatKey = formatKey;
            this.cmdLethal = cmdLethal;
            this.customCounters = customCounters;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Preset>>(){}.getType();

    private static Path presetPath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("mtgcard")
                .resolve("life_point_presets.json");
    }

    private static void ensureDir() throws IOException {
        Files.createDirectories(presetPath().getParent());
    }

    public static LinkedHashMap<String, Preset> loadAll() {
        Loaded loaded = loadInternal();
        return loaded.presets;
    }

    public static List<String> listKeys() {
        return new ArrayList<>(loadAll().keySet());
    }

    public static Preset get(String key) {
        if (key == null) return null;
        return loadAll().get(safeKey(key));
    }

    public static void upsert(Preset preset) {
        if (preset == null) return;

        String k = safeKey(preset.name);
        if (k.isBlank()) k = "Preset";

        Loaded loaded = loadInternal();
        preset.name = k;

        sanitizePreset(preset, k);

        loaded.presets.put(k, preset);
        saveMap(loaded.presets);
    }

    public static void delete(String key) {
        if (key == null) return;

        Loaded loaded = loadInternal();
        loaded.presets.remove(safeKey(key));
        saveMap(loaded.presets);
    }

    // ---------------- internal ----------------

    private static final class Loaded {
        final LinkedHashMap<String, Preset> presets;
        Loaded(LinkedHashMap<String, Preset> presets) { this.presets = presets; }
    }

    private static Loaded loadInternal() {
        try {
            Path p = presetPath();
            if (!Files.exists(p)) return new Loaded(new LinkedHashMap<>());

            String json = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) return new Loaded(new LinkedHashMap<>());

            JsonElement rootEl = JsonParser.parseString(json);
            if (!rootEl.isJsonObject()) return new Loaded(new LinkedHashMap<>());
            JsonObject root = rootEl.getAsJsonObject();

            LinkedHashMap<String, Preset> presets;

            // Accept both:
            // - envelope: { "schema": 1, "presets": { ... } }
            // - map: { "MyPreset": { ... } }
            if (root.has("presets") && root.get("presets").isJsonObject()) {
                presets = GSON.fromJson(root.getAsJsonObject("presets"), MAP_TYPE);
            } else {
                presets = GSON.fromJson(root, MAP_TYPE);
            }
            if (presets == null) presets = new LinkedHashMap<>();

            LinkedHashMap<String, Preset> out = new LinkedHashMap<>();
            boolean changed = false;

            for (var e : presets.entrySet()) {
                String k = safeKey(e.getKey());
                Preset v = e.getValue();
                if (v == null) { changed = true; continue; }

                if (!Objects.equals(k, e.getKey())) changed = true;

                if (v.name == null || v.name.isBlank()) { v.name = k; changed = true; }
                if (!Objects.equals(safeKey(v.name), k)) { v.name = k; changed = true; }

                // Back-compat: old json won't have cmdLethal, gson leaves it default 0
                if (v.cmdLethal <= 0) { v.cmdLethal = 21; changed = true; }

                sanitizePreset(v, k);
                out.put(k, v);
            }

            if (changed) saveMap(out);
            return new Loaded(out);

        } catch (Exception e) {
            return new Loaded(new LinkedHashMap<>());
        }
    }

    private static void saveMap(LinkedHashMap<String, Preset> all) {
        try {
            ensureDir();
            Path p = presetPath();

            // Keep existing format: just a map (no envelope)
            String json = GSON.toJson(all, MAP_TYPE);

            Path tmp = p.resolveSibling(p.getFileName().toString() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, p,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {}
    }

    private static void sanitizePreset(Preset v, String key) {
        if (v.iconKey == null || v.iconKey.isBlank()) v.iconKey = "none";
        if (v.formatKey == null || v.formatKey.isBlank()) v.formatKey = "commander";

        v.playerColor &= 0xFFFFFF;
        v.lifeColor &= 0xFFFFFF;

        // Cmd lethal clamp + default
        if (v.cmdLethal <= 0) v.cmdLethal = 21;
        v.cmdLethal = Math.max(1, Math.min(9999, v.cmdLethal));

        if (v.name == null || v.name.isBlank()) v.name = key;
        v.name = safeKey(v.name);
        if (v.name.isBlank()) v.name = key;

        // Clean customCounters keys if present
        if (v.customCounters != null) {
            LinkedHashMap<String, Integer> cleaned = new LinkedHashMap<>();
            for (var e : v.customCounters.entrySet()) {
                String ck = safeKey(e.getKey()).toLowerCase(Locale.ROOT);
                if (ck.isBlank()) continue;
                int val = (e.getValue() == null) ? 0 : e.getValue();
                cleaned.put(ck, Math.max(-999999, Math.min(999999, val)));
            }
            v.customCounters = cleaned.isEmpty() ? null : cleaned;
        }
    }

    private static String safeKey(String s) {
        if (s == null) return "";
        s = s.trim();
        s = s.replaceAll("[\\r\\n\\t]", " ");
        s = s.replaceAll("[\\\\/:*?\"<>|]", "-");
        s = s.replaceAll("\\s{2,}", " ").trim();
        if (s.length() > 32) s = s.substring(0, 32).trim();
        return s;
    }

    private LifePointPresetStore() {}
}
