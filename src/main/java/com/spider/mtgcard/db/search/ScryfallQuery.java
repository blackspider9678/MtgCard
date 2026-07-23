package com.spider.mtgcard.db.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Query state for local card database searches. */
public final class ScryfallQuery {
    public static final class Term {
        public enum Field { NAME, SET, RARITY, TYPE, COLOR, COLOR_ID, MV, IS, FREE }
        public final Field field;
        public final String value;
        public final boolean negated;
        public final boolean exact;
        public final String op;

        public Term(Field f, String v, boolean n, boolean e) {
            this(f, v, n, e, null);
        }

        public Term(Field f, String v, boolean n, boolean e, String op) {
            this.field = f;
            this.value = v;
            this.negated = n;
            this.exact = e;
            this.op = op;
        }
    }

    public enum SortKey {
        NAME, SET, RARITY, MV, COLOR, COLOR_ID, FOIL, TOKEN,
        PRICE, TYPE, POWER, TOUGHNESS
    }

    public enum Dir { ASC, DESC }

    public final List<Term> terms = new ArrayList<>();
    public SortKey sort = SortKey.NAME;
    public Dir dir = Dir.ASC;
    public int limit = 54;
    public int offset = 0;
    private ScryfallSyntax.Parsed parsed = ScryfallSyntax.parse("");

    public boolean matches(ScryfallSyntax.CardView card) {
        return parsed.matches(card);
    }

    public static ScryfallQuery parse(String raw, int limit, int offset, String order, String direction) {
        ScryfallQuery q = new ScryfallQuery();
        q.limit = Math.max(1, limit);
        q.offset = Math.max(0, offset);
        q.parsed = ScryfallSyntax.parse(raw == null ? "" : raw);

        applySort(q, order);
        applyDirection(q, direction);

        Map<String, String> options = ScryfallSyntax.options(raw);
        applySort(q, options.get("order"));
        applySort(q, options.get("sort"));
        applyDirection(q, options.get("dir"));
        applyDirection(q, options.get("direction"));

        return q;
    }

    private static void applySort(ScryfallQuery q, String order) {
        if (q == null || order == null || order.isBlank()) return;

        switch (order.trim().toLowerCase(Locale.ROOT)) {
            case "name" -> q.sort = SortKey.NAME;
            case "set", "s", "edition" -> q.sort = SortKey.SET;
            case "rarity", "r" -> q.sort = SortKey.RARITY;
            case "mv", "cmc", "mana", "manavalue", "mana_value" -> q.sort = SortKey.MV;
            case "color", "colors", "c" -> q.sort = SortKey.COLOR;
            case "color_id", "colorid", "ci", "id", "identity" -> q.sort = SortKey.COLOR_ID;
            case "foil" -> q.sort = SortKey.FOIL;
            case "token" -> q.sort = SortKey.TOKEN;
            case "price", "usd", "eur", "tix" -> q.sort = SortKey.PRICE;
            case "type", "t" -> q.sort = SortKey.TYPE;
            case "power", "pow", "p" -> q.sort = SortKey.POWER;
            case "toughness", "tou" -> q.sort = SortKey.TOUGHNESS;
            default -> {
            }
        }
    }

    private static void applyDirection(ScryfallQuery q, String direction) {
        if (q == null || direction == null || direction.isBlank()) return;

        String value = direction.trim().toLowerCase(Locale.ROOT);
        q.dir = switch (value) {
            case "desc", "descending", "down" -> Dir.DESC;
            default -> Dir.ASC;
        };
    }
}
