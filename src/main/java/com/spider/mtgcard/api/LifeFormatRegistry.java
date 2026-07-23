package com.spider.mtgcard.api;

import com.spider.mtgcard.life.LifeFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Public addon API for health tracker formats shown in the Life Point edit tab.
 *
 * <p>Examples:
 * LifeFormatRegistry.registerSimple("pokemon", Identifier.fromNamespaceAndPath("pokemon", "prize_cards"),
 *         Component.literal("Pokemon Prizes"), 6, 100);
 * LifeFormatRegistry.registerSimple("lorcana", Identifier.fromNamespaceAndPath("lorcana", "lore"),
 *         Component.literal("Lorcana Lore"), 0, 100);
 * LifeFormatRegistry.registerSharedTeams("mtg", "twoheaded", Component.literal("Two Headed Giant"), 30, 2, 40);
 */
public final class LifeFormatRegistry {
    public static final String COMMANDER = LifeFormat.DEFAULT_KEY;
    public static final String STANDARD = "standard";
    public static final String BRAWL = "brawl";
    public static final String TWO_HEADED = "twoheaded";

    private static final Map<String, LifeFormat> FORMATS = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        register(LifeFormat.DEFAULT);
        register(new LifeFormat(
                TcgGameRegistry.MTG,
                STANDARD,
                Component.literal("Standard"),
                20,
                false,
                1,
                20,
                List.of("std")
        ));
        register(new LifeFormat(
                TcgGameRegistry.MTG,
                BRAWL,
                Component.literal("Brawl"),
                25,
                false,
                1,
                30
        ));
        register(new LifeFormat(
                TcgGameRegistry.MTG,
                TWO_HEADED,
                Component.literal("Two Headed Giant"),
                30,
                false,
                2,
                40,
                List.of("2hg", "thg", "twoheadedgiant")
        ));
    }

    public static LifeFormat simple(String game, Identifier id, Component label, int startingLife, int sortOrder) {
        return simple(game, key(id), label, startingLife, sortOrder);
    }

    public static LifeFormat simple(String game, String key, Component label, int startingLife, int sortOrder) {
        return format(game, key, label, startingLife, false, 1, sortOrder);
    }

    public static LifeFormat commanderDamage(String game, Identifier id, Component label, int startingLife,
                                             int sortOrder) {
        return commanderDamage(game, key(id), label, startingLife, sortOrder);
    }

    public static LifeFormat commanderDamage(String game, String key, Component label, int startingLife,
                                             int sortOrder) {
        return format(game, key, label, startingLife, true, 1, sortOrder);
    }

    public static LifeFormat sharedTeams(String game, Identifier id, Component label, int startingLife,
                                         int teamSize, int sortOrder) {
        return sharedTeams(game, key(id), label, startingLife, teamSize, sortOrder);
    }

    public static LifeFormat sharedTeams(String game, String key, Component label, int startingLife,
                                         int teamSize, int sortOrder) {
        return format(game, key, label, startingLife, false, teamSize, sortOrder);
    }

    public static LifeFormat format(String game, Identifier id, Component label, int startingLife,
                                    boolean commanderDamage, int turnGroupSize, int sortOrder) {
        return format(game, key(id), label, startingLife, commanderDamage, turnGroupSize, sortOrder);
    }

    public static LifeFormat format(String game, String key, Component label, int startingLife,
                                    boolean commanderDamage, int turnGroupSize, int sortOrder) {
        return new LifeFormat(game, key, label, startingLife, commanderDamage, turnGroupSize, sortOrder);
    }

    public static synchronized LifeFormat register(LifeFormat format) {
        LifeFormat safe = Objects.requireNonNull(format, "format");
        String key = safe.key();
        if (!TcgGameRegistry.containsGame(safe.game())) {
            TcgGameRegistry.register(safe.game(), TcgGameRegistry.labelForGame(safe.game()));
        }

        FORMATS.put(key, safe);
        ALIASES.put(compactAlias(key), key);
        for (String alias : safe.aliases()) {
            if (alias == null || alias.isBlank()) continue;
            ALIASES.put(compactAlias(alias), key);
        }
        return safe;
    }

    public static LifeFormat registerSimple(String game, Identifier id, Component label, int startingLife,
                                            int sortOrder) {
        return register(simple(game, id, label, startingLife, sortOrder));
    }

    public static LifeFormat registerSimple(String game, String key, Component label, int startingLife,
                                            int sortOrder) {
        return register(simple(game, key, label, startingLife, sortOrder));
    }

    public static LifeFormat registerCommanderDamage(String game, Identifier id, Component label, int startingLife,
                                                     int sortOrder) {
        return register(commanderDamage(game, id, label, startingLife, sortOrder));
    }

    public static LifeFormat registerCommanderDamage(String game, String key, Component label, int startingLife,
                                                     int sortOrder) {
        return register(commanderDamage(game, key, label, startingLife, sortOrder));
    }

    public static LifeFormat registerSharedTeams(String game, Identifier id, Component label, int startingLife,
                                                 int teamSize, int sortOrder) {
        return register(sharedTeams(game, id, label, startingLife, teamSize, sortOrder));
    }

    public static LifeFormat registerSharedTeams(String game, String key, Component label, int startingLife,
                                                 int teamSize, int sortOrder) {
        return register(sharedTeams(game, key, label, startingLife, teamSize, sortOrder));
    }

    public static synchronized Optional<LifeFormat> get(String key) {
        String normalized = resolveKnownKey(key);
        return normalized.isBlank() ? Optional.empty() : Optional.ofNullable(FORMATS.get(normalized));
    }

    public static synchronized LifeFormat getOrDefault(String key) {
        LifeFormat format = FORMATS.get(resolveKnownKey(key));
        return format == null ? LifeFormat.DEFAULT : format;
    }

    public static synchronized boolean contains(String key) {
        return !resolveKnownKey(key).isBlank();
    }

    public static synchronized List<LifeFormat> entries() {
        ArrayList<LifeFormat> entries = new ArrayList<>(FORMATS.values());
        entries.sort(formatComparator());
        return List.copyOf(entries);
    }

    public static synchronized List<LifeFormat> entriesForGame(String game) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        ArrayList<LifeFormat> entries = new ArrayList<>();
        for (LifeFormat format : FORMATS.values()) {
            if (format.game().equals(normalized)) entries.add(format);
        }
        entries.sort(formatComparator());
        return List.copyOf(entries);
    }

    public static synchronized String normalizeKey(String key) {
        String resolved = resolveKnownKey(key);
        return resolved.isBlank() ? LifeFormat.DEFAULT_KEY : resolved;
    }

    private static String resolveKnownKey(String key) {
        String raw = normalizeRawKey(key);
        if (!raw.isBlank() && FORMATS.containsKey(raw)) return raw;

        String alias = ALIASES.get(compactAlias(key));
        if (alias != null && FORMATS.containsKey(alias)) return alias;

        return "";
    }

    public static String normalizeRawKey(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.isBlank()) return "";
        try {
            return LifeFormat.sanitizeKey(k);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public static String compactAlias(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }

    private static String key(Identifier id) {
        if (id == null) throw new IllegalArgumentException("Life format id must not be null");
        return id.toString().toLowerCase(Locale.ROOT);
    }

    private static Comparator<LifeFormat> formatComparator() {
        return Comparator
                .comparingInt(LifeFormat::sortOrder)
                .thenComparing(f -> f.label().getString())
                .thenComparing(LifeFormat::key);
    }

    private LifeFormatRegistry() {
    }
}
