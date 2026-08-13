package com.spider.mtgcard.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

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
    public boolean Mob_Pack_Drops_Enabled = false;
    public boolean Mob_Pack_Drop_Player_Kill_Only = true;
    public double Mob_Pack_Drop_Chance = 0.01;
    public int Mob_Pack_Drop_Warden = 2;
    public int Mob_Pack_Drop_Elder_Guardian = 2;
    public int Mob_Pack_Drop_Ender_Dragon = 2;
    public int Mob_Pack_Drop_Wither = 2;
    public Set<String> Mob_Pack_Drop_Blacklist = new LinkedHashSet<>();

    public boolean Dice_Mob_Drops_Enabled = false;
    public boolean Dice_Mob_Drop_Player_Kill_Only = true;
    public double Dice_Mob_Drop_Chance = 0.01;
    public int Dice_Mob_Drop_Warden = 0;
    public int Dice_Mob_Drop_Elder_Guardian = 0;
    public int Dice_Mob_Drop_Ender_Dragon = 0;
    public int Dice_Mob_Drop_Wither = 0;
    public Set<String> Dice_Mob_Drop_Blacklist = new LinkedHashSet<>();

    // Card Store
    public boolean Card_Store_Enabled = true;

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
            if (needsMobDropBackfill(tomlPath)) {
                saveToml(cfg);
            }
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
            cfg.Mob_Pack_Drops_Enabled = file.getOrElse("pack.mob_drops.enabled", cfg.Mob_Pack_Drops_Enabled);
            cfg.Mob_Pack_Drop_Player_Kill_Only = file.getOrElse("pack.mob_drops.player_kill_only", cfg.Mob_Pack_Drop_Player_Kill_Only);
            cfg.Mob_Pack_Drop_Chance = file.getOrElse("pack.mob_drops.chance", cfg.Mob_Pack_Drop_Chance);
            cfg.Mob_Pack_Drop_Warden = file.getOrElse("pack.mob_drops.bosses.warden_count", cfg.Mob_Pack_Drop_Warden);
            cfg.Mob_Pack_Drop_Elder_Guardian = file.getOrElse("pack.mob_drops.bosses.elder_guardian_count", cfg.Mob_Pack_Drop_Elder_Guardian);
            cfg.Mob_Pack_Drop_Ender_Dragon = file.getOrElse("pack.mob_drops.bosses.ender_dragon_count", cfg.Mob_Pack_Drop_Ender_Dragon);
            cfg.Mob_Pack_Drop_Wither = file.getOrElse("pack.mob_drops.bosses.wither_count", cfg.Mob_Pack_Drop_Wither);
            cfg.Mob_Pack_Drop_Blacklist = new LinkedHashSet<>(file.getOrElse("pack.mob_drops.blacklist", cfg.Mob_Pack_Drop_Blacklist));
            cfg.Dice_Mob_Drops_Enabled = file.getOrElse("dice.mob_drops.enabled", cfg.Dice_Mob_Drops_Enabled);
            cfg.Dice_Mob_Drop_Player_Kill_Only = file.getOrElse("dice.mob_drops.player_kill_only", cfg.Dice_Mob_Drop_Player_Kill_Only);
            cfg.Dice_Mob_Drop_Chance = file.getOrElse("dice.mob_drops.chance", cfg.Dice_Mob_Drop_Chance);
            cfg.Dice_Mob_Drop_Warden = file.getOrElse("dice.mob_drops.bosses.warden_count", cfg.Dice_Mob_Drop_Warden);
            cfg.Dice_Mob_Drop_Elder_Guardian = file.getOrElse("dice.mob_drops.bosses.elder_guardian_count", cfg.Dice_Mob_Drop_Elder_Guardian);
            cfg.Dice_Mob_Drop_Ender_Dragon = file.getOrElse("dice.mob_drops.bosses.ender_dragon_count", cfg.Dice_Mob_Drop_Ender_Dragon);
            cfg.Dice_Mob_Drop_Wither = file.getOrElse("dice.mob_drops.bosses.wither_count", cfg.Dice_Mob_Drop_Wither);
            cfg.Dice_Mob_Drop_Blacklist = new LinkedHashSet<>(file.getOrElse("dice.mob_drops.blacklist", cfg.Dice_Mob_Drop_Blacklist));

            // --- card store ---
            cfg.Card_Store_Enabled = file.getOrElse("card_store.enabled", cfg.Card_Store_Enabled);

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
                file.set("pack.mob_drops.enabled", cfg.Mob_Pack_Drops_Enabled);
                file.set("pack.mob_drops.player_kill_only", cfg.Mob_Pack_Drop_Player_Kill_Only);
                file.set("pack.mob_drops.chance", cfg.Mob_Pack_Drop_Chance);
                file.set("pack.mob_drops.bosses.warden_count", cfg.Mob_Pack_Drop_Warden);
                file.set("pack.mob_drops.bosses.elder_guardian_count", cfg.Mob_Pack_Drop_Elder_Guardian);
                file.set("pack.mob_drops.bosses.ender_dragon_count", cfg.Mob_Pack_Drop_Ender_Dragon);
                file.set("pack.mob_drops.bosses.wither_count", cfg.Mob_Pack_Drop_Wither);
                file.set("pack.mob_drops.blacklist", new java.util.ArrayList<>(cfg.Mob_Pack_Drop_Blacklist));
                file.set("dice.mob_drops.enabled", cfg.Dice_Mob_Drops_Enabled);
                file.set("dice.mob_drops.player_kill_only", cfg.Dice_Mob_Drop_Player_Kill_Only);
                file.set("dice.mob_drops.chance", cfg.Dice_Mob_Drop_Chance);
                file.set("dice.mob_drops.bosses.warden_count", cfg.Dice_Mob_Drop_Warden);
                file.set("dice.mob_drops.bosses.elder_guardian_count", cfg.Dice_Mob_Drop_Elder_Guardian);
                file.set("dice.mob_drops.bosses.ender_dragon_count", cfg.Dice_Mob_Drop_Ender_Dragon);
                file.set("dice.mob_drops.bosses.wither_count", cfg.Dice_Mob_Drop_Wither);
                file.set("dice.mob_drops.blacklist", new java.util.ArrayList<>(cfg.Dice_Mob_Drop_Blacklist));

                file.set("card_store.enabled", cfg.Card_Store_Enabled);

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
                        "debug: enables verbose server-side booster pack logs.\n" +
                        "mob_drops: controls booster pack drops from hostile mobs and bosses.");

        file.setComment("pack.custom_chances.common", "Chance for a custom COMMON.");
        file.setComment("pack.custom_chances.uncommon", "Chance for a custom UNCOMMON.");
        file.setComment("pack.custom_chances.wildcard_c_or_u", "Chance for a custom wildcard (common/uncommon).");
        file.setComment("pack.custom_chances.rare_or_mythic", "Chance for a custom RARE/MYTHIC.");
        file.setComment("pack.custom_chances.random", "Chance for a custom random card.");
        file.setComment("pack.custom_chances.random_foil", "Chance for a custom random FOIL card.");
        file.setComment("pack.custom_chances.basic_land", "Chance for a custom basic land.");
        file.setComment("pack.custom_chances.token_or_art", "Chance for a custom token/art slot.");
        file.setComment("pack.debug", "Enable verbose booster pack debug logging on the server.");
        file.setComment("pack.mob_drops",
                "Mob pack drops.\n" +
                        "enabled: master toggle for booster pack drops from hostile mobs and the listed bosses.\n" +
                        "player_kill_only: if true, only player kills can drop booster packs.\n" +
                        "chance: 0.0 to 1.0 chance for normal hostile mobs.\n" +
                        "blacklist: entity ids that should never drop booster packs, for example minecraft:zombie.\n" +
                        "bosses: guaranteed pack counts for supported bosses, clamped to 2..3.");
        file.setComment("pack.mob_drops.enabled", "Enable booster pack drops from hostile mobs and configured bosses.");
        file.setComment("pack.mob_drops.player_kill_only", "If true, booster packs only drop when the mob was killed by a player.");
        file.setComment("pack.mob_drops.chance", "Chance for a non-boss hostile mob to drop 1 booster pack.");
        file.setComment("pack.mob_drops.blacklist", "Entity ids that should never drop booster packs (example: minecraft:zombie).");
        file.setComment("pack.mob_drops.bosses",
                "Guaranteed boss pack drops.\n" +
                        "Each count is clamped to 2..3 while mob drops are enabled.");
        file.setComment("pack.mob_drops.bosses.warden_count", "Guaranteed booster pack count for the Warden.");
        file.setComment("pack.mob_drops.bosses.elder_guardian_count", "Guaranteed booster pack count for the Elder Guardian.");
        file.setComment("pack.mob_drops.bosses.ender_dragon_count", "Guaranteed booster pack count for the Ender Dragon.");
        file.setComment("pack.mob_drops.bosses.wither_count", "Guaranteed booster pack count for the Wither.");

        file.setComment("dice",
                "Dice settings.\n" +
                        "mob_drops: controls random dice drops from hostile mobs.");
        file.setComment("dice.mob_drops",
                "Mob dice drops.\n" +
                        "enabled: master toggle for random dice drops from hostile mobs.\n" +
                        "player_kill_only: if true, only player kills can drop dice.\n" +
                        "chance: 0.0 to 1.0 chance for a hostile mob to drop one random dice item.\n" +
                        "blacklist: entity ids that should never drop dice, for example minecraft:zombie.\n" +
                        "bosses: guaranteed random dice drop counts for supported bosses, clamped to 0..3.");
        file.setComment("dice.mob_drops.enabled", "Enable random dice drops from hostile mobs.");
        file.setComment("dice.mob_drops.player_kill_only", "If true, dice only drop when the mob was killed by a player.");
        file.setComment("dice.mob_drops.chance", "Chance for a hostile mob to drop 1 random dice item.");
        file.setComment("dice.mob_drops.blacklist", "Entity ids that should never drop dice (example: minecraft:zombie).");
        file.setComment("dice.mob_drops.bosses",
                "Guaranteed boss dice drops.\n" +
                        "Each count is clamped to 0..3 and each dropped item rolls a random dice type.");
        file.setComment("dice.mob_drops.bosses.warden_count", "Guaranteed random dice drop count for the Warden.");
        file.setComment("dice.mob_drops.bosses.elder_guardian_count", "Guaranteed random dice drop count for the Elder Guardian.");
        file.setComment("dice.mob_drops.bosses.ender_dragon_count", "Guaranteed random dice drop count for the Ender Dragon.");
        file.setComment("dice.mob_drops.bosses.wither_count", "Guaranteed random dice drop count for the Wither.");

        file.setComment("card_store",
                "Card Store settings.\n" +
                        "enabled: if false, the Card Store recipe is not loaded and placed stores cannot be opened.");
        file.setComment("card_store.enabled", "Enable the Card Store block and recipe.");

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
        cfg.Mob_Pack_Drop_Chance = clamp01(cfg.Mob_Pack_Drop_Chance);
        cfg.Mob_Pack_Drop_Warden = clampInt(cfg.Mob_Pack_Drop_Warden, 2, 3);
        cfg.Mob_Pack_Drop_Elder_Guardian = clampInt(cfg.Mob_Pack_Drop_Elder_Guardian, 2, 3);
        cfg.Mob_Pack_Drop_Ender_Dragon = clampInt(cfg.Mob_Pack_Drop_Ender_Dragon, 2, 3);
        cfg.Mob_Pack_Drop_Wither = clampInt(cfg.Mob_Pack_Drop_Wither, 2, 3);
        cfg.Mob_Pack_Drop_Blacklist = normalizeIdentifierSet(cfg.Mob_Pack_Drop_Blacklist);
        cfg.Dice_Mob_Drop_Chance = clamp01(cfg.Dice_Mob_Drop_Chance);
        cfg.Dice_Mob_Drop_Warden = clampInt(cfg.Dice_Mob_Drop_Warden, 0, 3);
        cfg.Dice_Mob_Drop_Elder_Guardian = clampInt(cfg.Dice_Mob_Drop_Elder_Guardian, 0, 3);
        cfg.Dice_Mob_Drop_Ender_Dragon = clampInt(cfg.Dice_Mob_Drop_Ender_Dragon, 0, 3);
        cfg.Dice_Mob_Drop_Wither = clampInt(cfg.Dice_Mob_Drop_Wither, 0, 3);
        cfg.Dice_Mob_Drop_Blacklist = normalizeIdentifierSet(cfg.Dice_Mob_Drop_Blacklist);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> normalizeIdentifierSet(Set<String> raw) {
        Set<String> normalized = new LinkedHashSet<>();
        if (raw == null) return normalized;

        for (String entry : raw) {
            if (entry == null) continue;
            String cleaned = entry.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isBlank()) {
                normalized.add(cleaned);
            }
        }
        return normalized;
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

    public boolean mobPackDropsEnabled() {
        return Mob_Pack_Drops_Enabled;
    }

    public double mobPackDropChance() {
        return clamp01(Mob_Pack_Drop_Chance);
    }

    public boolean mobPackDropsRequirePlayerKill() {
        return Mob_Pack_Drop_Player_Kill_Only;
    }

    public boolean isPackMobDropBlacklisted(EntityType<?> type) {
        if (type == null) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().toLowerCase(Locale.ROOT);
        return Mob_Pack_Drop_Blacklist.contains(id);
    }

    public int mobPackBossDropCount(EntityType<?> type) {
        if (type == EntityType.WARDEN) return Mob_Pack_Drop_Warden;
        if (type == EntityType.ELDER_GUARDIAN) return Mob_Pack_Drop_Elder_Guardian;
        if (type == EntityType.ENDER_DRAGON) return Mob_Pack_Drop_Ender_Dragon;
        if (type == EntityType.WITHER) return Mob_Pack_Drop_Wither;
        return 0;
    }

    public boolean diceMobDropsEnabled() {
        return Dice_Mob_Drops_Enabled;
    }

    public boolean diceMobDropsRequirePlayerKill() {
        return Dice_Mob_Drop_Player_Kill_Only;
    }

    public int diceMobBossDropCount(EntityType<?> type) {
        if (type == EntityType.WARDEN) return Dice_Mob_Drop_Warden;
        if (type == EntityType.ELDER_GUARDIAN) return Dice_Mob_Drop_Elder_Guardian;
        if (type == EntityType.ENDER_DRAGON) return Dice_Mob_Drop_Ender_Dragon;
        if (type == EntityType.WITHER) return Dice_Mob_Drop_Wither;
        return 0;
    }

    public boolean isDiceMobDropBlacklisted(EntityType<?> type) {
        if (type == null) return false;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().toLowerCase(Locale.ROOT);
        return Dice_Mob_Drop_Blacklist.contains(id);
    }

    public double diceMobDropChance() {
        return clamp01(Dice_Mob_Drop_Chance);
    }

    private static boolean needsMobDropBackfill(Path path) {
        try {
            String raw = Files.readString(path);
            return !raw.contains("[pack.mob_drops]")
                    || !raw.contains("[pack.mob_drops.bosses]")
                    || !raw.contains("[dice.mob_drops]")
                    || !raw.contains("[dice.mob_drops.bosses]")
                    || !raw.contains("[card_store]")
                    || countOccurrences(raw, "player_kill_only") < 2
                    || countOccurrences(raw, "blacklist =") < 2;
        } catch (Throwable e) {
            return false;
        }
    }

    private static int countOccurrences(String raw, String needle) {
        int count = 0;
        int index = 0;
        while ((index = raw.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    public static boolean cardStoreEnabled() {
        MtgcardConfig cfg = get();
        return cfg == null || cfg.Card_Store_Enabled;
    }

    private MtgcardConfig() {}
}
