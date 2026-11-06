package com.spider.mtgcard.db.search;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal Scryfall-ish query model + parser. */
public final class ScryfallQuery {
    public static final class Term {
        public enum Field { NAME, SET, RARITY, TYPE, COLOR, COLOR_ID, MV, IS, FREE }
        public final Field field;
        public final String value;
        public final boolean negated;
        public final boolean exact;   // quoted or explicit '=' after the colon
        public final String op;       // ":", "=", ">=", "<=", ">", "<" (null => default)

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

    // Include extra keys so SearchEngine switching is future-safe
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

    private static final Pattern TOKEN   = Pattern.compile("\"([^\"]+)\"|(\\S+)");
    // operator-aware field tokens (c=rw, ci>=ub, mv<=3, mv>2, etc.)
    private static final Pattern COLOR_OP = Pattern.compile("^(?i)(c|color)(:|>=|<=|=|>|<)(.+)$");
    private static final Pattern CI_OP    = Pattern.compile("^(?i)(ci|color_id|id)(:|>=|<=|=|>|<)(.+)$");
    private static final Pattern MV_OP    = Pattern.compile("^(?i)(mv|cmc)(:|>=|<=|=|>|<)(.+)$");

    public static ScryfallQuery parse(String raw, int limit, int offset, String order, String direction) {
        ScryfallQuery q = new ScryfallQuery();
        q.limit = Math.max(1, limit);
        q.offset = Math.max(0, offset);

        if (order != null) {
            switch (order.toLowerCase(Locale.ROOT)) {
                case "name" -> q.sort = SortKey.NAME;
                case "set", "s" -> q.sort = SortKey.SET;
                case "rarity", "r" -> q.sort = SortKey.RARITY;
                case "mv", "cmc" -> q.sort = SortKey.MV;
                case "color" -> q.sort = SortKey.COLOR;
                case "color_id", "ci", "id" -> q.sort = SortKey.COLOR_ID;
                case "foil" -> q.sort = SortKey.FOIL;
                case "token" -> q.sort = SortKey.TOKEN;
                case "price", "usd" -> q.sort = SortKey.PRICE;
                case "type" -> q.sort = SortKey.TYPE;
                case "power" -> q.sort = SortKey.POWER;
                case "toughness" -> q.sort = SortKey.TOUGHNESS;
            }
        }
        if (direction != null) {
            q.dir = direction.equalsIgnoreCase("desc") ? Dir.DESC : Dir.ASC;
        }

        if (raw == null || raw.isBlank()) return q;

        Matcher m = TOKEN.matcher(raw);
        while (m.find()) {
            final boolean wasQuoted = (m.group(1) != null);
            String tok = wasQuoted ? m.group(1) : m.group(2);
            if (tok == null || tok.isBlank()) continue;

            boolean neg = tok.startsWith("-");
            String t = neg ? tok.substring(1) : tok;
            String lower = t.toLowerCase(Locale.ROOT);

            // --- Operator-aware fields FIRST (so they don’t get consumed by simple prefixes)
            Matcher mc = COLOR_OP.matcher(t);
            if (mc.matches()) {
                String op = mc.group(2);
                String v  = mc.group(3).trim();
                q.terms.add(new Term(Term.Field.COLOR, v, neg, false, op));
                continue;
            }
            Matcher mi = CI_OP.matcher(t);
            if (mi.matches()) {
                String op = mi.group(2);
                String v  = mi.group(3).trim();
                q.terms.add(new Term(Term.Field.COLOR_ID, v, neg, false, op));
                continue;
            }
            Matcher mm = MV_OP.matcher(t);
            if (mm.matches()) {
                String op = mm.group(2);
                String v  = mm.group(3).trim();
                q.terms.add(new Term(Term.Field.MV, v, neg, false, op));
                continue;
            }

            // --- Inline order/dir passthrough
            if (lower.startsWith("order:")) {
                String v = t.substring(6);
                switch (v.toLowerCase(Locale.ROOT)) {
                    case "name" -> q.sort = SortKey.NAME;
                    case "set", "s" -> q.sort = SortKey.SET;
                    case "rarity", "r" -> q.sort = SortKey.RARITY;
                    case "mv", "cmc" -> q.sort = SortKey.MV;
                    case "color" -> q.sort = SortKey.COLOR;
                    case "color_id", "ci", "id" -> q.sort = SortKey.COLOR_ID;
                    case "foil" -> q.sort = SortKey.FOIL;
                    case "token" -> q.sort = SortKey.TOKEN;
                    case "price", "usd" -> q.sort = SortKey.PRICE;
                    case "type" -> q.sort = SortKey.TYPE;
                    case "power" -> q.sort = SortKey.POWER;
                    case "toughness" -> q.sort = SortKey.TOUGHNESS;
                }
                continue;
            }

            if (lower.startsWith("dir:")) {
                String v = t.substring(4);
                q.dir = v.equalsIgnoreCase("desc") ? Dir.DESC : Dir.ASC;
                continue;
            }

            // --- Simple prefixes (no explicit operator in token)
            if (lower.startsWith("name:")) {
                String v = t.substring(5);
                boolean exact = wasQuoted || v.startsWith("="); // allow name:=Bolt
                if (v.startsWith("=")) v = v.substring(1);
                q.terms.add(new Term(Term.Field.NAME, v, neg, exact));
            } else if (lower.startsWith("set:") || lower.startsWith("s:")) {
                String v = t.substring(t.indexOf(':') + 1);
                q.terms.add(new Term(Term.Field.SET, v, neg, false));
            } else if (lower.startsWith("rarity:") || lower.startsWith("r:")) {
                String v = t.substring(t.indexOf(':') + 1);
                q.terms.add(new Term(Term.Field.RARITY, v, neg, false));
            } else if (lower.startsWith("t:") || lower.startsWith("type:")) {
                String v = t.substring(t.indexOf(':') + 1);
                boolean exact = wasQuoted || v.startsWith("=");
                if (v.startsWith("=")) v = v.substring(1);
                q.terms.add(new Term(Term.Field.TYPE, v, neg, exact));
            } else if (lower.startsWith("c:")) {
                q.terms.add(new Term(Term.Field.COLOR, t.substring(2).trim(), neg, false, ":"));
            } else if (lower.startsWith("ci:")) {
                q.terms.add(new Term(Term.Field.COLOR_ID, t.substring(3).trim(), neg, false, ":"));
            } else if (lower.startsWith("mv:") || lower.startsWith("cmc:")) {
                String v = t.substring(t.indexOf(':') + 1).trim();
                q.terms.add(new Term(Term.Field.MV, v, neg, false, "=")); // colon → equality default
            } else if (lower.startsWith("is:")) {
                q.terms.add(new Term(Term.Field.IS, t.substring(3), neg, false));
            } else {
                // FREE text; quoted => exact
                q.terms.add(new Term(Term.Field.FREE, t, neg, wasQuoted));
            }
        }
        return q;
    }
}
