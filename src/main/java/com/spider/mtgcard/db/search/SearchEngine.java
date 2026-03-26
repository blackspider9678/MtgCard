package com.spider.mtgcard.db.search;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.*;
import java.util.function.Predicate;

import static javax.management.ObjectName.unquote;

public final class SearchEngine {

    /** A light row view extracted from ItemStack once for cheap matching. */
    public static final class Row {
        public final ItemStack stack;
        public final String name;
        public final String set;
        public final String rarity;
        public final String typeLine;
        public final Set<String> colors;
        public final Set<String> colorId;
        public final int mv;
        public final boolean foil;
        public final boolean tokenLike;

        // NEW:
        public final double priceUsd;     // numeric, NaN if missing
        public final String powerRaw;     // "2", "*", "1+*", etc (may be "")
        public final String toughnessRaw;

        public Row(ItemStack st, String name, String set, String rarity,
                   String typeLine, Set<String> colors, Set<String> colorId,
                   int mv, boolean foil, boolean tokenLike,
                   double priceUsd, String powerRaw, String toughnessRaw) {

            this.stack = st;
            this.name = name;
            this.set = set;
            this.rarity = rarity;
            this.typeLine = typeLine;
            this.colors = colors;
            this.colorId = colorId;
            this.mv = mv;
            this.foil = foil;
            this.tokenLike = tokenLike;

            this.priceUsd = priceUsd;
            this.powerRaw = powerRaw;
            this.toughnessRaw = toughnessRaw;
        }
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
        var comp = st.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, null);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        String name = meta.getString("name").orElse("");
        String set  = meta.getString("set").orElse("");

        // rarity normalization -> common|uncommon|rare|mythic (your sorter also accepts c/u/r/m)
        String rarRaw = meta.getString("rarity").orElse("");
        String rarity = normalizeRarity(rarRaw);

        // prefer meta.type_line; fall back to first face.type_line if missing
        String type = meta.getString("type_line").orElse("");
        if (type.isEmpty()) type = firstFace(meta, "type_line");

        // mv comes from "cmc" (your writer uses meta.putInt("cmc", ...))
        int mv = meta.getInt("cmc").orElse(0);

        // colors & color_identity are NbtList of strings like "W","U","B","R","G"
        Set<String> cols = readColors(meta.getList("colors").orElse(null));
        Set<String> cid  = readColors(meta.getList("color_identity").orElse(null));

        // flags
        boolean foil      = root.getBoolean("mtg_foil").orElse(false);
        boolean tokenLike = meta.getBoolean("is_token").orElse(false);

        // --- price (best-effort) ---
        // Pick ONE canonical storage key in your writer if you can.
        // These reads are defensive so it won’t crash if you change formats.
        double priceUsd = Double.NaN;

        // common patterns:
        priceUsd = readDouble(meta, "usd", Double.NaN);              // e.g. meta.usd = "1.23" or 1.23
        if (Double.isNaN(priceUsd)) priceUsd = readDouble(meta, "price_usd", Double.NaN);
        if (Double.isNaN(priceUsd)) priceUsd = readDouble(meta, "price", Double.NaN);

        // --- power/toughness (prefer meta; fallback to face 0) ---
        String power = meta.getString("power").orElse("");
        if (power.isEmpty()) power = firstFace(meta, "power");

        String toughness = meta.getString("toughness").orElse("");
        if (toughness.isEmpty()) toughness = firstFace(meta, "toughness");

        return new Row(st, name, set, rarity, type, cols, cid, mv, foil, tokenLike, priceUsd, power, toughness);

    }

    private static Set<String> readColors(ListTag lst) {
        Set<String> out = new HashSet<>();
        if (lst == null) return out;
        for (int i = 0; i < lst.size(); i++) {
            String s = lst.getString(i).orElse("").trim().toUpperCase(Locale.ROOT);
            if (!s.isEmpty()) {
                // store as single-letter tokens "W","U","B","R","G"
                out.add(String.valueOf(s.charAt(0)));
            }
        }
        return out;
    }

    private static String firstFace(CompoundTag meta, String key) {
        var facesOpt = meta.getList("card_faces");
        if (facesOpt.isEmpty()) return "";
        var faces = facesOpt.get();
        for (int i = 0; i < faces.size(); i++) {
            var f = faces.getCompound(i).orElse(null);
            if (f == null) continue;
            String v = f.getString(key).orElse("");
            if (!v.isEmpty()) return v;
        }
        return "";
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
        List<List<Term>> groups = parseRaw(raw);
        if (groups.isEmpty()) return r -> true;

        // OR over groups
        List<Predicate<Row>> ors = new ArrayList<>();
        for (var terms : groups) {
            // AND within each group
            List<Predicate<Row>> ands = new ArrayList<>();
            for (var t : terms) {
                Predicate<Row> p;
                String f = (t.field == null) ? "free" : t.field;
                switch (f) {
                    case "name" -> p = r -> contains(r.name, t.value);
                    case "set"  -> p = r -> equalsIgnoreCase(r.set, t.value);
                    case "rarity" -> p = r -> equalsIgnoreCase(r.rarity, t.value)
                            || equalsIgnoreCase(r.rarity, normalizeRarity(t.value));
                    case "type" -> p = r -> contains(r.typeLine, t.value);
                    case "color" -> {
                        Set<String> need = letters(t.value);
                        String op = (t.op == null || t.op.isBlank()) ? ":" : t.op;
                        p = r -> matchColorSet(r.colors, need, op);
                    }
                    case "color_id" -> {
                        Set<String> need = letters(t.value);
                        String op = (t.op == null || t.op.isBlank()) ? ":" : t.op;
                        p = r -> matchColorSet(r.colorId, need, op);
                    }
                    case "mv" -> {
                        int n = safeInt(t.value.trim(), Integer.MIN_VALUE);
                        String op = t.op == null ? "=" : t.op;
                        p = r -> switch (op) {
                            case ">"  -> r.mv > n;
                            case ">=" -> r.mv >= n;
                            case "<"  -> r.mv < n;
                            case "<=" -> r.mv <= n;
                            default   -> r.mv == n;
                        };
                    }
                    case "is" -> {
                        String v = t.value.toLowerCase(Locale.ROOT);
                        if (v.equals("foil")) p = r -> r.foil;
                        else if (v.equals("token")) p = r -> r.tokenLike;
                        else p = r -> true;
                    }
                    default /* free */ -> p = r -> contains(r.name, t.value) || contains(r.typeLine, t.value);
                }
                if (t.neg) p = p.negate();
                ands.add(p);
            }
            ors.add(ands.stream().reduce(x -> true, Predicate::and));
        }
        return ors.stream().reduce(x -> false, Predicate::or);
    }

    public static Predicate<Row> buildPredicate(ScryfallQuery q) {
        List<Predicate<Row>> ands = new ArrayList<>();

        for (var t : q.terms) {
            Predicate<Row> p;
            switch (t.field) {
                case NAME -> p = r -> t.exact
                        ? equalsIgnoreCase(r.name, t.value)
                        : contains(r.name, t.value);
                case SET  -> p = r -> equalsIgnoreCase(r.set, t.value);
                case RARITY -> p = r -> equalsIgnoreCase(r.rarity, t.value)
                        || equalsIgnoreCase(r.rarity, normalizeRarity(t.value));
                case TYPE -> p = r -> t.exact
                        ? equalsIgnoreCase(r.typeLine, t.value)
                        : contains(r.typeLine, t.value);
                case COLOR -> {
                    var need = letters(t.value);
                    String op = (t.op == null || t.op.isBlank()) ? ":" : t.op;
                    p = r -> r.colors != null && matchColorSet(r.colors, need, op);
                }
                case COLOR_ID -> {
                    var need = letters(t.value);
                    String op = (t.op == null || t.op.isBlank()) ? ":" : t.op;
                    p = r -> r.colorId != null && matchColorSet(r.colorId, need, op);
                }
                case MV -> {
                    String v = (t.value == null ? "" : t.value.trim());
                    String op = (t.op == null || t.op.isBlank()) ? "=" : t.op;
                    int n = safeInt(v, Integer.MIN_VALUE);
                    final String fop = op; final int fn = n;
                    p = r -> switch (fop) {
                        case ">"  -> r.mv >  fn;
                        case ">=" -> r.mv >= fn;
                        case "<"  -> r.mv <  fn;
                        case "<=" -> r.mv <= fn;
                        default   -> r.mv == fn;
                    };
                }
                case IS -> {
                    String v = (t.value == null ? "" : t.value).toLowerCase(Locale.ROOT);
                    if (v.equals("foil"))      p = r -> r.foil;
                    else if (v.equals("token")) p = r -> r.tokenLike;
                    else                        p = r -> true; // unknown is: ignored
                }

                case FREE -> p = r -> t.exact
                        ? equalsIgnoreCase(r.name, t.value) || equalsIgnoreCase(r.typeLine, t.value)
                        : contains(r.name, t.value) || contains(r.typeLine, t.value);
                default -> p = r -> true;
            }
            if (t.negated) p = p.negate();
            ands.add(p);
        }

        return ands.stream().reduce(x -> true, Predicate::and);
    }

    /* ------------ Sorting ------------- */

    // Build comparator from the query's enum sort key
    private static Comparator<Row> buildComparator(ScryfallQuery.SortKey sort, boolean ascending) {
        // Default: sort by name (null-safe, case-insensitive)
        Comparator<Row> cmp = Comparator.comparing(
                r -> nullLow(r == null ? null : r.name),
                String.CASE_INSENSITIVE_ORDER
        );

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
            }
        }

        if (!ascending) cmp = cmp.reversed();
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
                default          -> key = ScryfallQuery.SortKey.NAME;
            }
        }
        return buildComparator(key, ascending);
    }

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

    private static double readDouble(CompoundTag nbt, String key, double def) {
        if (nbt == null || key == null) return def;

        // If stored as string
        var sOpt = nbt.getString(key);
        if (sOpt.isPresent()) {
            String s = sOpt.get().trim();
            if (s.isEmpty()) return def;
            try { return Double.parseDouble(s); } catch (Exception ignore) {}
        }

        // If stored as number (int/float/double)
        var dOpt = nbt.getDouble(key);
        if (dOpt.isPresent()) return dOpt.get();

        var iOpt = nbt.getInt(key);
        if (iOpt.isPresent()) return (double) iOpt.get();

        return def;
    }
}
