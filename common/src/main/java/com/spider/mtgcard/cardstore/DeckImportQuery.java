package com.spider.mtgcard.cardstore;

import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Locale;

/** Literal card names and optional printing hints, never user-supplied search syntax. */
public final class DeckImportQuery {
    private DeckImportQuery() {}

    public static String newest(String name, String set, String collector, LocalDate today) {
        boolean hasName = name != null && !name.isBlank();
        if (!hasName && (set == null || set.isBlank() || collector == null || collector.isBlank())) {
            throw new IllegalArgumentException("A card name or an exact printing is required");
        }
        String query = (hasName ? "!" + quoted(name) + " " : "") + "date<=" + today + " -layout:art_series";
        if (set != null && !set.isBlank()) query += " set:" + quoted(set);
        if (collector != null && !collector.isBlank()) query += " cn:" + quoted(collector);
        return query;
    }

    private static String quoted(String value) {
        return "\"" + value.trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Prefer the whole card; a front-face name is a fallback, never a back/adventure face alone. */
    public static int nameMatchRank(String requested, String cardName, String frontFaceName) {
        if (requested == null || requested.isBlank()) return 0;
        String name = normalizedName(requested);
        if (name.equals(normalizedName(cardName))) return 0;
        if (name.equals(normalizedName(frontFaceName))) return 1;
        return 2;
    }

    private static String normalizedName(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('\u2019', '\'').replaceAll("\\s*//\\s*", " // ")
                .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
