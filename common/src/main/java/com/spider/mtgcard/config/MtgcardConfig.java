package com.spider.mtgcard.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class MtgcardConfig {

    // --- file names ---
    private static final String TOML_NAME = "mtgcard.toml";
    private static final String LEGACY_JSON_NAME = "mtgcard.json";

    // legacy reader (migration only)
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static MtgcardConfig INSTANCE;

    // ========= YOUR SETTINGS (same fields) =========

    public boolean Anyone_Can_Import = false;
    public Set<String> Import_Whitelist = new LinkedHashSet<>();

    // Card language (Scryfall)
    public String Card_Language = "en";

    // Pack: global custom pull chances (0..1)
    public double chance_custom_common = 0.10;
    public double chance_custom_uncommon = 0.05;
    public double chance_custom_wildcard_c_or_u = 0.05;
    public double chance_custom_rare_or_mythic = 0.01;
    public double chance_custom_random = 0.10;
    public double chance_custom_random_foil = 0.08;
    public double chance_custom_basic_land = 0.15;
    public double chance_custom_token_or_art = 0.02;
    public boolean Pack_Debug = false;

    // Loot
    public boolean Loot_Packs_From_Fishing = true;

    // Price display
    public String Price_Item = "minecraft:diamond";
    public String Price_Basis = "USD";

    // ==============================================

    public static MtgcardConfig get() {
        if (INSTANCE == null) INSTANCE = loadInternal();
        return INSTANCE;
    }

    public static void load() { INSTANCE = loadInternal(); }
    public static void reload() { INSTANCE = loadInternal(); }
    public static void save() { if (INSTANCE != null) saveToml(INSTANCE); }

    private static MtgcardConfig loadInternal() {
        Path tomlPath = tomlPath();
        Path jsonPath = legacyJsonPath();

        // 1) If TOML exists, load it.
        if (Files.exists(tomlPath)) {
            MtgcardConfig cfg = loadToml(tomlPath);
            applyDefaultsAndClamp(cfg);
            return cfg;
        }

        // 2) If TOML missing but legacy JSON exists, migrate.
        if (Files.exists(jsonPath)) {
            MtgcardConfig cfg = loadLegacyJson(jsonPath);
            applyDefaultsAndClamp(cfg);
            saveToml(cfg); // write new toml
            return cfg;
        }

        // 3) Nothing exists -> write defaults toml.
        MtgcardConfig cfg = new MtgcardConfig();
        applyDefaultsAndClamp(cfg);
        saveToml(cfg);
        return cfg;
    }

    private static MtgcardConfig loadLegacyJson(Path path) {
        try {
            String json = Files.readString(path);
            MtgcardConfig cfg = GSON.fromJson(json, MtgcardConfig.class);
            return (cfg == null) ? new MtgcardConfig() : cfg;
        } catch (Throwable e) {
            System.out.println("[mtgcard] Failed to load legacy json, using defaults: " + e);
            return new MtgcardConfig();
        }
    }

    private static MtgcardConfig loadToml(Path path) {
        try (CommentedFileConfig file = CommentedFileConfig.builder(path, TomlFormat.instance())
                .preserveInsertionOrder()
                .sync()
                .build()) {

            file.load();

            MtgcardConfig cfg = new MtgcardConfig();

            // --- import ---
            cfg.Anyone_Can_Import = file.getOrElse("import.anyone_can_import", cfg.Anyone_Can_Import);
            cfg.Import_Whitelist = new LinkedHashSet<>(file.getOrElse("import.whitelist", cfg.Import_Whitelist));

            // --- language ---
            cfg.Card_Language = file.getOrElse("cards.language", cfg.Card_Language);

            // --- pack chances ---
            cfg.chance_custom_common = file.getOrElse("pack.custom_chances.common", cfg.chance_custom_common);
            cfg.chance_custom_uncommon = file.getOrElse("pack.custom_chances.uncommon", cfg.chance_custom_uncommon);
            cfg.chance_custom_wildcard_c_or_u = file.getOrElse("pack.custom_chances.wildcard_c_or_u", cfg.chance_custom_wildcard_c_or_u);
            cfg.chance_custom_rare_or_mythic = file.getOrElse("pack.custom_chances.rare_or_mythic", cfg.chance_custom_rare_or_mythic);
            cfg.chance_custom_random = file.getOrElse("pack.custom_chances.random", cfg.chance_custom_random);
            cfg.chance_custom_random_foil = file.getOrElse("pack.custom_chances.random_foil", cfg.chance_custom_random_foil);
            cfg.chance_custom_basic_land = file.getOrElse("pack.custom_chances.basic_land", cfg.chance_custom_basic_land);
            cfg.chance_custom_token_or_art = file.getOrElse("pack.custom_chances.token_or_art", cfg.chance_custom_token_or_art);
            cfg.Pack_Debug = file.getOrElse("pack.debug", cfg.Pack_Debug);

            // --- loot ---
            cfg.Loot_Packs_From_Fishing = file.getOrElse("loot.packs_from_fishing", cfg.Loot_Packs_From_Fishing);

            // --- price ---
            cfg.Price_Item = file.getOrElse("price.item", cfg.Price_Item);
            cfg.Price_Basis = file.getOrElse("price.basis", cfg.Price_Basis);

            return cfg;
        } catch (Throwable e) {
            System.out.println("[mtgcard] Failed to load toml, using defaults: " + e);
            return new MtgcardConfig();
        }
    }

    private static void saveToml(MtgcardConfig cfg) {
        Path path = tomlPath();
        try {
            Files.createDirectories(path.getParent());

            try (CommentedFileConfig file = CommentedFileConfig.builder(path, TomlFormat.instance())
                    .preserveInsertionOrder()
                    .sync()
                    .build()) {

                // write values
                file.set("import.anyone_can_import", cfg.Anyone_Can_Import);
                file.set("import.whitelist", new java.util.ArrayList<>(cfg.Import_Whitelist));

                file.set("cards.language", cfg.Card_Language);

                file.set("pack.custom_chances.common", cfg.chance_custom_common);
                file.set("pack.custom_chances.uncommon", cfg.chance_custom_uncommon);
                file.set("pack.custom_chances.wildcard_c_or_u", cfg.chance_custom_wildcard_c_or_u);
                file.set("pack.custom_chances.rare_or_mythic", cfg.chance_custom_rare_or_mythic);
                file.set("pack.custom_chances.random", cfg.chance_custom_random);
                file.set("pack.custom_chances.random_foil", cfg.chance_custom_random_foil);
                file.set("pack.custom_chances.basic_land", cfg.chance_custom_basic_land);
                file.set("pack.custom_chances.token_or_art", cfg.chance_custom_token_or_art);
                file.set("pack.debug", cfg.Pack_Debug);

                file.set("loot.packs_from_fishing", cfg.Loot_Packs_From_Fishing);

                file.set("price.item", cfg.Price_Item);
                file.set("price.basis", cfg.Price_Basis);

                // add comments (players will thank you)
                addComments(file);

                file.save();
            }
        } catch (Throwable e) {
            System.out.println("[mtgcard] Failed to save toml config: " + e);
        }
    }

    private static void addComments(CommentedConfig file) {
        file.setComment("import",
                "Import settings.\n" +
                        "anyone_can_import: if true, any player can use the custom card importer.\n" +
                        "whitelist: if anyone_can_import is false, only these usernames can import.");

        file.setComment("import.anyone_can_import", "Allow anyone to import custom cards.");
        file.setComment("import.whitelist", "Usernames allowed to import when anyone_can_import=false.");

        file.setComment("cards",
                "Card settings.\n" +
                        "language: Scryfall language code for downloaded cards.\n" +
                        "Examples: en, es, fr, de, it, pt, ja, ko, ru, zhs, zht");

        file.setComment("cards.language", "Preferred Scryfall language code (falls back to English if not available).");

        file.setComment("pack",
                "Pack settings.\n" +
                        "custom_chances are 0.0 to 1.0 (e.g. 0.10 = 10%).\n" +
                        "debug: enables verbose server-side booster pack logs.");

        file.setComment("pack.custom_chances.common", "Chance for a custom COMMON.");
        file.setComment("pack.custom_chances.uncommon", "Chance for a custom UNCOMMON.");
        file.setComment("pack.custom_chances.wildcard_c_or_u", "Chance for a custom wildcard (common/uncommon).");
        file.setComment("pack.custom_chances.rare_or_mythic", "Chance for a custom RARE/MYTHIC.");
        file.setComment("pack.custom_chances.random", "Chance for a custom random card.");
        file.setComment("pack.custom_chances.random_foil", "Chance for a custom random FOIL card.");
        file.setComment("pack.custom_chances.basic_land", "Chance for a custom basic land.");
        file.setComment("pack.custom_chances.token_or_art", "Chance for a custom token/art slot.");
        file.setComment("pack.debug", "Enable verbose booster pack debug logging on the server.");

        file.setComment("loot",
                "Loot settings.\n" +
                        "packs_from_fishing: if true, fishing can award booster packs.");

        file.setComment("loot.packs_from_fishing", "Allow booster packs to be added to fishing loot.");

        file.setComment("price",
                "Price display settings (Large View panel).\n" +
                        "item: which item is used as the icon.\n" +
                        "basis: which Scryfall price to use: USD, EUR, or TIX.");

        file.setComment("price.item", "Item id used as the price icon (ex: minecraft:diamond).");
        file.setComment("price.basis", "Price basis: USD, EUR, or TIX.");
    }

    private static void applyDefaultsAndClamp(MtgcardConfig cfg) {
        if (cfg.Import_Whitelist == null) cfg.Import_Whitelist = new LinkedHashSet<>();

        if (cfg.Price_Item == null || cfg.Price_Item.isBlank()) cfg.Price_Item = "minecraft:diamond";
        if (cfg.Price_Basis == null || cfg.Price_Basis.isBlank()) cfg.Price_Basis = "USD";

        if (cfg.Card_Language == null || cfg.Card_Language.isBlank()) cfg.Card_Language = "en";
        cfg.Card_Language = normalizeLang(cfg.Card_Language);

        cfg.chance_custom_common = clamp01(cfg.chance_custom_common);
        cfg.chance_custom_uncommon = clamp01(cfg.chance_custom_uncommon);
        cfg.chance_custom_wildcard_c_or_u = clamp01(cfg.chance_custom_wildcard_c_or_u);
        cfg.chance_custom_rare_or_mythic = clamp01(cfg.chance_custom_rare_or_mythic);
        cfg.chance_custom_random = clamp01(cfg.chance_custom_random);
        cfg.chance_custom_random_foil = clamp01(cfg.chance_custom_random_foil);
        cfg.chance_custom_basic_land = clamp01(cfg.chance_custom_basic_land);
        cfg.chance_custom_token_or_art = clamp01(cfg.chance_custom_token_or_art);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static String normalizeLang(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.matches("^[a-z]{2,3}$")) return s;        // en, ja, ko, zhs, zht, grc, etc.
        if (s.matches("^[a-z]{2}-[a-z]{2}$")) return s; // permissive
        return "en";
    }

    private static Path tomlPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(TOML_NAME);
    }

    private static Path legacyJsonPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(LEGACY_JSON_NAME);
    }

    /** Returns the configured chance (0..1) to pull a CUSTOM card for this slot. */
    public double customChanceForSlot(
            com.spider.mtgcard.content.pack.PackGenerator.RaritySlot slot
    ) {
        return switch (slot) {
            case COMMON -> clamp01(chance_custom_common);
            case UNCOMMON -> clamp01(chance_custom_uncommon);
            case WILDCARD_C_OR_U -> clamp01(chance_custom_wildcard_c_or_u);
            case RARE_OR_MYTHIC -> clamp01(chance_custom_rare_or_mythic);
            case RANDOM -> clamp01(chance_custom_random);
            case FOIL_RANDOM -> clamp01(chance_custom_random_foil);
            case BASIC_LAND -> clamp01(chance_custom_basic_land);
            case TOKEN_OR_ART -> clamp01(chance_custom_token_or_art);
        };
    }

    public static boolean packDebugEnabled() {
        MtgcardConfig cfg = get();
        return cfg != null && cfg.Pack_Debug;
    }

    public static boolean fishingPackLootEnabled() {
        MtgcardConfig cfg = get();
        return cfg == null || cfg.Loot_Packs_From_Fishing;
    }

    private MtgcardConfig() {}
}
