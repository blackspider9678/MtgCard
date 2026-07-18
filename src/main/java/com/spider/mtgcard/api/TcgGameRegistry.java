package com.spider.mtgcard.api;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TcgGameRegistry {
    public static final String ALL_GAMES = "";
    public static final String MTG = "mtg";

    private static final Entry ALL_ENTRY = new Entry(ALL_GAMES, Component.translatable("tcg_game.mtgcard.all"));
    private static final Map<String, Entry> GAMES = new LinkedHashMap<>();

    static {
        register(MTG, Component.translatable("tcg_game.mtgcard.mtg"));
    }

    public static synchronized Entry register(String id, Component label) {
        String normalized = normalizeGameId(id);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("TCG game id must not be blank or 'all'");
        }
        Entry entry = new Entry(normalized, label == null ? Component.literal(prettyName(normalized)) : label);
        GAMES.put(normalized, entry);
        return entry;
    }

    public static synchronized List<Entry> gameEntries() {
        ArrayList<Entry> entries = new ArrayList<>(GAMES.values());
        entries.sort(Comparator.comparing(Entry::id));
        return List.copyOf(entries);
    }

    public static List<Entry> filterOptions() {
        ArrayList<Entry> entries = new ArrayList<>();
        entries.add(ALL_ENTRY);
        entries.addAll(gameEntries());
        return List.copyOf(entries);
    }

    public static boolean hasMultipleGames() {
        return gameEntries().size() > 1;
    }

    public static Entry allEntry() {
        return ALL_ENTRY;
    }

    public static Component labelForFilter(String id) {
        String normalized = normalizeFilterId(id);
        if (normalized.isBlank()) return ALL_ENTRY.label();
        return labelForGame(normalized);
    }

    public static synchronized Component labelForGame(String id) {
        String normalized = normalizeGameId(id);
        Entry entry = GAMES.get(normalized);
        return entry == null ? Component.literal(prettyName(normalized)) : entry.label();
    }

    public static int filterIndex(String id) {
        String normalized = normalizeFilterId(id);
        if (normalized.isBlank()) return 0;

        List<Entry> options = filterOptions();
        for (int i = 1; i < options.size(); i++) {
            if (options.get(i).id().equals(normalized)) return i;
        }
        return 0;
    }

    public static String filterId(int index) {
        List<Entry> options = filterOptions();
        if (index < 0 || index >= options.size()) return ALL_GAMES;
        return options.get(index).id();
    }

    public static String shortLabel(String id) {
        String normalized = normalizeFilterId(id);
        if (normalized.isBlank()) return "A";
        if (MTG.equals(normalized)) return "M";

        String label = labelForGame(normalized).getString().trim();
        if (label.isEmpty()) label = prettyName(normalized);
        return label.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    public static String normalizeFilterId(String id) {
        if (id == null) return ALL_GAMES;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return "all".equals(normalized) ? ALL_GAMES : normalized;
    }

    public static String normalizeGameId(String id) {
        String normalized = normalizeFilterId(id);
        if (normalized.isBlank()) return ALL_GAMES;
        if (!normalized.matches("[a-z0-9_.:-]+")) {
            throw new IllegalArgumentException("Invalid TCG game id: " + id);
        }
        return normalized;
    }

    private static String prettyName(String id) {
        String normalized = normalizeFilterId(id);
        if (normalized.isBlank()) return "All";

        String tail = normalized;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < tail.length()) tail = tail.substring(colon + 1);

        String[] parts = tail.replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.isEmpty() ? normalized : out.toString();
    }

    public record Entry(String id, Component label) {
    }

    private TcgGameRegistry() {
    }
}
