package com.spider.mtgcard.db.search;

import com.spider.mtgcard.util.TcgCardMeta;
import com.spider.mtgcard.api.CardSearchProviderRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

public final class SearchEngine {

    /** A light row view extracted from ItemStack once for cheap matching. */
    public static final class Row implements ScryfallSyntax.CardView {
        public final ItemStack stack;
        public final String name;
        public final String set;
        public final String collectorNumber;
        public final String rarity;
        public final String manaCost;
        public final String typeLine;
        public final String oracleText;
        public final Set<String> colors;
        public final Set<String> colorId;
        public final int mv;
        public final boolean foil;
        public final boolean tokenLike;
        public final boolean legendary;
        public final String commanderLegality;
        public final String layout;
        public final String game;

        // NEW:
        public final double priceUsd;     // numeric, NaN if missing
        public final String powerRaw;     // "2", "*", "1+*", etc (may be "")
        public final String toughnessRaw;
        public final String loyaltyRaw;

        public Row(ItemStack st, String name, String set, String rarity,
                   String typeLine, Set<String> colors, Set<String> colorId,
                   int mv, boolean foil, boolean tokenLike,
                   double priceUsd, String powerRaw, String toughnessRaw) {
            this(st, name, set, "", rarity, "", typeLine, "", colors, colorId, mv,
                    foil, tokenLike, priceUsd, powerRaw, toughnessRaw, "",
                    "", false, "");
        }

        public Row(ItemStack st, String name, String set, String collectorNumber,
                   String rarity, String manaCost, String typeLine, String oracleText,
                   Set<String> colors, Set<String> colorId, int mv,
                   boolean foil, boolean tokenLike, double priceUsd,
                   String powerRaw, String toughnessRaw, String loyaltyRaw,
                   String layout, boolean legendary, String commanderLegality) {

            this.stack = st;
            this.name = name;
            this.set = set;
            this.collectorNumber = collectorNumber;
            this.rarity = rarity;
            this.manaCost = manaCost;
            this.typeLine = typeLine;
            this.oracleText = oracleText;
            this.colors = colors;
            this.colorId = colorId;
            this.mv = mv;
            this.foil = foil;
            this.tokenLike = tokenLike;
            this.legendary = legendary;
            this.commanderLegality = commanderLegality;
            this.layout = layout;
            this.game = TcgCardMeta.read(st).game();

            this.priceUsd = priceUsd;
            this.powerRaw = powerRaw;
            this.toughnessRaw = toughnessRaw;
            this.loyaltyRaw = loyaltyRaw;
        }

        @Override public String name() { return name; }
        @Override public String set() { return set; }
        @Override public String collectorNumber() { return collectorNumber; }
        @Override public String rarity() { return rarity; }
        @Override public String manaCost() { return manaCost; }
        @Override public String typeLine() { return typeLine; }
        @Override public String oracleText() { return oracleText; }
        @Override public String power() { return powerRaw; }
        @Override public String toughness() { return toughnessRaw; }
        @Override public String loyalty() { return loyaltyRaw; }
        @Override public String layout() { return layout; }
        @Override public int manaValue() { return mv; }
        @Override public Set<String> colors() { return colors == null ? Set.of() : colors; }
        @Override public Set<String> colorIdentity() { return colorId == null ? Set.of() : colorId; }
        @Override public boolean foil() { return foil; }
        @Override public boolean tokenLike() { return tokenLike; }
        @Override public boolean legendary() { return legendary; }
        @Override public boolean doubleFaced() {
            String l = layout == null ? "" : layout.toLowerCase(Locale.ROOT);
            return l.contains("transform") || l.contains("modal_dfc") || l.contains("double");
        }
        @Override public String commanderLegality() { return commanderLegality; }
        @Override public double priceUsd() { return priceUsd; }
    }

    public static Page search(
            List<ItemStack> intakeAll,
            ScryfallQuery q
    ) {
        var pred = buildPredicate(q);

        var rows = new ArrayList<Row>(intakeAll.size());
        for (var st : intakeAll) {
            if (st == null || st.isEmpty()) continue;
            rows.add(toRow(st));
        }

        rows.removeIf(pred.negate());

        Comparator<Row> cmp =
                buildComparator(q.sort, q.dir == ScryfallQuery.Dir.ASC);
        rows.sort(cmp);

        int total = rows.size();
        int from  = Math.min(Math.max(0, q.offset), total);
        int to    = Math.min(total, from + Math.max(1, q.limit));
        List<Row> page = (from < to) ? rows.subList(from, to) : List.of();
        int next = (to < total) ? to : -1;

        return new Page(total, page, next);
    }

    /* ------------ Row extraction from NBT (matches CardNBTUtil) ------------- */
    public static Row toRow(ItemStack st) {
        TcgCardMeta.Info meta = TcgCardMeta.read(st);

        // rarity normalization -> common|uncommon|rare|mythic (your sorter also accepts c/u/r/m)
        String rarity = normalizeRarity(meta.rarity());

        // prefer meta.type_line; fall back to first face.type_line if missing
        String type = meta.typeLine();

        // mv comes from "cmc" (your writer uses meta.putInt("cmc", ...))
        int mv = meta.manaValue();

        // colors & color_identity are NbtList of strings like "W","U","B","R","G"
        Set<String> cols = normalizeColors(meta.colors());
        Set<String> cid  = normalizeColors(meta.colorIdentity());

        // flags
        boolean foil      = meta.foil();
        boolean tokenLike = meta.tokenLike();

        // --- price (best-effort) ---
        // Pick ONE canonical storage key in your writer if you can.
        // These reads are defensive so it won’t crash if you change formats.
        double priceUsd = meta.priceUsd();

        // common patterns:
        // --- power/toughness (prefer meta; fallback to face 0) ---
        String power = meta.power();
        String toughness = meta.toughness();
        String loyalty = meta.loyalty();

        return new Row(
                st,
                meta.name(),
                meta.set(),
                meta.collectorNumber(),
                rarity,
                meta.manaCost(),
                type,
                meta.oracleText(),
                cols,
                cid,
                mv,
                foil,
                tokenLike,
                priceUsd,
                power,
                toughness,
                loyalty,
                meta.layout(),
                meta.legendary(),
                meta.commanderLegality()
        );

    }

    private static Set<String> normalizeColors(Set<String> colors) {
        if (colors == null || colors.isEmpty()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String color : colors) {
            String value = color == null ? "" : color.trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty()) out.add(String.valueOf(value.charAt(0)));
        }
        return out;
    }

    private static String normalizeRarity(String r) {
        String x = (r == null) ? "" : r.toLowerCase(Locale.ROOT).trim();
        return switch (x) {
            case "c", "common"      -> "common";
            case "u", "uncommon"    -> "uncommon";
            case "r", "rare"        -> "rare";
            case "m", "mythic", "mythic rare" -> "mythic";
            default -> x;
        };
    }

    private static boolean matchColorSet(Set<String> card, Set<String> query, String op) {
        if (card == null) card = Set.of();
        if (query == null) query = Set.of();
        // normalize op
        String o = (op == null || op.isBlank()) ? ":" : op;
        switch (o) {
            case ":", ">=":
                // card ⊇ query  (contains at least those colors)
                return card.containsAll(query);
            case "=":
                // exact match
                return card.equals(query);
            case "<=":
                // card ⊆ query
                return query.containsAll(card);
            case ">":
                // strict superset
                return card.containsAll(query) && !card.equals(query);
            case "<":
                // strict subset
                return query.containsAll(card) && !card.equals(query);
            default:
                // unknown -> be permissive (no filter)
                return true;
        }
    }

    /* ------------ Simple Scryfall-ish parser ------------- */
    private static final class Term {
        final String field;   // name,set,rarity,type,c,color,ci,colorid,mv,cmc,is (foil|token), or FREE text
        final String op;      // =, <, <=, >, >=  (only for mv)
        final String value;
        final boolean neg;

        Term(String field, String op, String value, boolean neg) {
            this.field = field;
            this.op = op;
            this.value = value;
            this.neg = neg;
        }
    }

    /** Split by OR (case-insensitive) into disjunctions of AND-terms. */
    private static List<List<Term>> parseRaw(String raw) {
        if (raw == null) raw = "";
        String q = raw.trim();
        if (q.isEmpty()) return List.of(List.of());

        // naive split on " or " tokens
        String[] ors = q.split("(?i)\\s+or\\s+");
        List<List<Term>> groups = new ArrayList<>();

        for (String seg : ors) {
            List<Term> terms = new ArrayList<>();

            // split by whitespace, but allow quoted values "like this"
            List<String> tokens = lex(seg);
            for (String t : tokens) {
                boolean neg = false;
                if (t.startsWith("-")) { neg = true; t = t.substring(1); }

                String field = null, value, op = "=";

                // Support either "field:value" or "field[=<>]=?value"
                // Examples: c:rw, c=rw, ci>=ub, mv<=3
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^([A-Za-z_]+)(:|>=|<=|=|>|<)?(.*)$")
                        .matcher(t);

                if (m.matches()) {
                    field = m.group(1).toLowerCase(Locale.ROOT);
                    String opRaw = m.group(2);
                    value = m.group(3) == null ? "" : m.group(3);

                    if (opRaw == null) {
                        // No explicit operator → treat as FREE term
                        field = null;
                        value = t;
                    } else {
                        op = opRaw;
                    }

                    // normalize aliases
                    if (field != null) {
                        if (field.equals("c")) field = "color";
                        if (field.equals("ci") || field.equals("id")) field = "color_id";
                        if (field.equals("cmc")) field = "mv";
                        if (field.equals("t") || field.equals("type")) field = "type";
                        if (field.equals("r") || field.equals("rar")) field = "rarity";

                        // For mv, if value begins with a comparator (e.g., mv:>=3), strip once.
                        if (field.equals("mv")) {
                            String v = value.trim();
                            if (v.startsWith(">=") || v.startsWith("<=")) {
                                op = v.substring(0, 2); v = v.substring(2);
                            } else if (v.startsWith(">") || v.startsWith("<") || v.startsWith("=") || v.startsWith(":")) {
                                if (v.startsWith(":")) v = v.substring(1); // ":" → "=" semantics for mv
                                else { op = v.substring(0, 1); v = v.substring(1); }
                            }
                            value = v.trim();
                        }

                        // For color / color_id, allow ":" (contains) and comparisons (=, >=, <=, >, <)
                        if (field.equals("color") || field.equals("color_id")) {
                            if (op == null || op.isBlank()) op = ":"; // default
                        }
                    }

                    terms.add(new Term(field, op, unquote(value.trim()), neg));
                } else {
                    // FREE term
                    terms.add(new Term(null, "=", unquote(t), neg));
                }
            }

            groups.add(terms);
        }

        return groups;
    }


    private static List<String> lex(String s) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') { inQ = !inQ; continue; }
            if (!inQ && Character.isWhitespace(ch)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String unquote(String v) {
        if (v == null) return "";
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) return v.substring(1, v.length()-1);
        return v;
    }

    // Convert a Row into an IndexRecord the UI can show
    public static IndexRecord toIndexRecord(Row r) {
        var ir = new IndexRecord();
        ir.id        = null; // (optional) set if you have a stable id
        ir.name      = r.name;
        ir.set       = r.set;
        ir.rarity    = r.rarity;
        ir.typeLine  = r.typeLine;
        ir.mv        = r.mv;
        ir.colors    = (r.colors == null) ? List.of()
                : new ArrayList<>(r.colors);
        ir.imageSmall   = null; // fill later if/when you have art URLs
        ir.nameTokens   = null; // if you pre-tokenize, assign here
        ir.oracleTokens = null;
        ir.typeTokens   = null;
        ir.timestamp    = System.currentTimeMillis();
        return ir;
    }


    /* ------------ Predicate building (supports OR groups) ------------- */
    public static Predicate<Row> buildPredicateFromRaw(String raw) {
        ScryfallSyntax.Parsed parsed = ScryfallSyntax.parse(raw);
        return parsed::matches;
    }

    public static Predicate<Row> buildPredicate(ScryfallQuery q) {
        if (q == null) return r -> true;
        return row -> CardSearchProviderRegistry.get(row.game)
                .map(provider -> provider.matches(row.stack, q.raw()))
                .orElseGet(() -> q.matches(row));
    }

    /* ------------ Sorting ------------- */

    // Build comparator from the query's enum sort key
    private static Comparator<Row> buildComparator(ScryfallQuery.SortKey sort, boolean ascending) {
        // Default: sort by name (null-safe, case-insensitive)
        Comparator<Row> cmp = Comparator.comparing(
                r -> nullLow(r == null ? null : r.name),
                String.CASE_INSENSITIVE_ORDER
        );
        boolean handledDirection = false;

        if (sort != null) {
            switch (sort) {
                case NAME -> cmp = Comparator.comparing(r -> nullLow(r.name), String.CASE_INSENSITIVE_ORDER);
                case SET -> cmp = Comparator.comparing(r -> nullLow(r.set), String.CASE_INSENSITIVE_ORDER);
                case RARITY -> cmp = Comparator.comparingInt(SearchEngine::rarityRank); // uses rarityRank(Row)
                case MV -> cmp = Comparator.comparingInt(r -> r.mv);
                case COLOR -> cmp = Comparator.comparing(r -> colorKey(r.colors), String.CASE_INSENSITIVE_ORDER);
                case COLOR_ID -> cmp = Comparator.comparing(r -> colorKey(r.colorId), String.CASE_INSENSITIVE_ORDER);
                case FOIL -> cmp = Comparator.comparing(r -> r.foil);        // false < true
                case TOKEN -> cmp = Comparator.comparing(r -> r.tokenLike);  // false < true
                case PRICE -> {
                    cmp = compareByPrice(ascending);
                    handledDirection = true;
                }
                case TYPE -> cmp = Comparator.comparing(r -> nullLow(r.typeLine), String.CASE_INSENSITIVE_ORDER);
                case POWER -> {
                    cmp = compareByStat(r -> r == null ? null : r.powerRaw, ascending);
                    handledDirection = true;
                }
                case TOUGHNESS -> {
                    cmp = compareByStat(r -> r == null ? null : r.toughnessRaw, ascending);
                    handledDirection = true;
                }
            }
        }

        if (sort != ScryfallQuery.SortKey.NAME) {
            cmp = cmp.thenComparing(r -> nullLow(r == null ? null : r.name), String.CASE_INSENSITIVE_ORDER);
        }

        if (!handledDirection && !ascending) cmp = cmp.reversed();
        return cmp;
    }

    // Convenience overload if something passes a string like "name", "set", etc.
    private static Comparator<Row> buildComparator(String order, boolean ascending) {
        ScryfallQuery.SortKey key;
        if (order == null) {
            key = ScryfallQuery.SortKey.NAME;
        } else {
            switch (order.toLowerCase(Locale.ROOT)) {
                case "name"      -> key = ScryfallQuery.SortKey.NAME;
                case "set"       -> key = ScryfallQuery.SortKey.SET;
                case "rarity"    -> key = ScryfallQuery.SortKey.RARITY;
                case "mv", "cmc", "mana", "manavalue"
                        -> key = ScryfallQuery.SortKey.MV;
                case "color"     -> key = ScryfallQuery.SortKey.COLOR;
                case "color_id"  -> key = ScryfallQuery.SortKey.COLOR_ID;
                case "foil"      -> key = ScryfallQuery.SortKey.FOIL;
                case "token"     -> key = ScryfallQuery.SortKey.TOKEN;
                case "price", "usd" -> key = ScryfallQuery.SortKey.PRICE;
                case "type" -> key = ScryfallQuery.SortKey.TYPE;
                case "power" -> key = ScryfallQuery.SortKey.POWER;
                case "toughness" -> key = ScryfallQuery.SortKey.TOUGHNESS;
                default          -> key = ScryfallQuery.SortKey.NAME;
            }
        }
        return buildComparator(key, ascending);
    }

    private static Comparator<Row> compareByPrice(boolean ascending) {
        return (a, b) -> compareNullableDouble(
                a == null ? Double.NaN : a.priceUsd,
                b == null ? Double.NaN : b.priceUsd,
                ascending
        );
    }

    private static Comparator<Row> compareByStat(java.util.function.Function<Row, String> getter, boolean ascending) {
        return (a, b) -> compareStatValues(
                getter.apply(a),
                getter.apply(b),
                ascending
        );
    }

    private static int compareNullableDouble(double a, double b, boolean ascending) {
        boolean aMissing = Double.isNaN(a);
        boolean bMissing = Double.isNaN(b);
        if (aMissing != bMissing) return aMissing ? 1 : -1;
        if (aMissing) return 0;

        int cmp = Double.compare(a, b);
        return ascending ? cmp : -cmp;
    }

    private static int compareStatValues(String aRaw, String bRaw, boolean ascending) {
        StatValue a = parseStatValue(aRaw);
        StatValue b = parseStatValue(bRaw);

        if (a.missing != b.missing) return a.missing ? 1 : -1;
        if (a.missing) return 0;

        if (a.numeric != b.numeric) return a.numeric ? -1 : 1;

        if (a.numeric) {
            int cmp = Double.compare(a.numericValue, b.numericValue);
            if (cmp != 0) return ascending ? cmp : -cmp;
        }

        int cmp = a.text.compareToIgnoreCase(b.text);
        return ascending ? cmp : -cmp;
    }

    private static StatValue parseStatValue(String raw) {
        String text = nullLow(raw).trim();
        if (text.isEmpty()) return new StatValue(true, false, 0.0, "");

        try {
            return new StatValue(false, true, Double.parseDouble(text), text);
        } catch (NumberFormatException ignored) {
            return new StatValue(false, false, 0.0, text);
        }
    }

    private record StatValue(boolean missing, boolean numeric, double numericValue, String text) {}

    private static String safeStr(String s) { return (s == null) ? "" : s; }

    private static int safeInt(Integer i) { return (i == null) ? Integer.MAX_VALUE : i; }

    private static int rarityRank(String rarity) {
        if (rarity == null) return 999;
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "c", "common"      -> 0;
            case "u", "uncommon"    -> 1;
            case "r", "rare"        -> 2;
            case "m", "mythic", "mythic rare" -> 3;
            default -> 999; // unknowns sort last
        };
    }

    private static String colorKey(Set<String> s) {
        if (s == null || s.isEmpty()) return "";
        // stable key like "B,G,R" etc
        ArrayList<String> a = new ArrayList<>(s);
        a.sort(String::compareTo);
        return String.join(",", a);
    }

    private static int rarityRank(Row r) {
        String s = (r.rarity == null) ? "" : r.rarity.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "mythic" -> 3;
            case "rare" -> 2;
            case "uncommon" -> 1;
            case "common" -> 0;
            default -> -1;
        };
    }

    public static final class Page {
        private final int total;
        private final List<Row> items;
        private final int nextOffset;

        public Page(int total, List<Row> items, int nextOffset) {
            this.total = total;
            this.items = (items == null) ? List.of() : items;
            this.nextOffset = nextOffset;
        }

        public int total() { return total; }
        public List<Row> items() { return items; }
        public int nextOffset() { return nextOffset; }
    }

    /* ------------ Helpers ------------- */
    private static boolean contains(String s, String needle) {
        if (s == null || needle == null) return false;
        return s.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
    private static Set<String> letters(String s) {
        Set<String> r = new HashSet<>();
        if (s == null) return r;
        for (char ch : s.toUpperCase(Locale.ROOT).toCharArray()) {
            if ("WUBRG".indexOf(ch) >= 0) r.add(String.valueOf(ch));
        }
        return r;
    }
    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    private static String nullLow(String s) { return (s == null) ? "" : s; }

    public static List<ItemStack> filterStacks(
            List<ItemStack> intakeAll,
            ScryfallQuery q
    ) {
        var pred = buildPredicate(q);
        var rows = new ArrayList<Row>(intakeAll.size());
        for (var st : intakeAll) {
            if (st == null || st.isEmpty()) continue;
            rows.add(toRow(st));
        }
        rows.removeIf(pred.negate());

        // sort
        Comparator<Row> cmp = buildComparator(q.sort, q.dir == ScryfallQuery.Dir.ASC);
        rows.sort(cmp);

        // return stacks in that order
        var out = new ArrayList<ItemStack>(rows.size());
        for (var r : rows) out.add(r.stack);
        return out;
    }

}
