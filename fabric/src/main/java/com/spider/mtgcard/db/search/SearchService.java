package com.spider.mtgcard.db.search;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SearchService {
    private static SearchService INSTANCE;
    private final List<IndexRecord> all;

    private SearchService() {
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("mtg/db/index_v1");
        this.all = new IndexLoader(cfg).loadAll();
        Mtgcard.LOGGER.info("Loaded {} index records for Card DB", all.size());
    }

    public static SearchService get() {
        if (INSTANCE == null) INSTANCE = new SearchService();
        return INSTANCE;
    }

    public SearchResults search(Filters f, String q, PageCursor c) {
        Stream<IndexRecord> s = all.stream();

        // Set & rarity
        if (!f.sets.isEmpty()) s = s.filter(r -> r.set != null && f.sets.contains(r.set));
        if (!f.rarities.isEmpty()) s = s.filter(r -> r.rarity != null && f.rarities.contains(r.rarity));

        // Mana value
        s = s.filter(r -> r.mv >= f.mvMin && r.mv <= f.mvMax);

        // Colors (basic includes-any; exact/identity handling can be added later)
        if (!f.colorsAny.isEmpty())
            s = s.filter(r -> r.colors != null && r.colors.stream().anyMatch(co -> hasColor(f, co)));

        // Types
        if (!f.typesAny.isEmpty())
            s = s.filter(r -> r.typeTokens != null &&
                    r.typeTokens.stream().anyMatch(t -> f.typesAny.contains(t)));
        if (!f.typesAll.isEmpty())
            s = s.filter(r -> r.typeTokens != null &&
                    f.typesAll.stream().allMatch(t -> r.typeTokens.contains(t)));

        // Boolean query (name:, type:, text:/oracle:, quotes, -, prefix*)
        Query query = Query.parse(q);
        if (!query.isEmpty()) s = s.filter(query::matches);

        // Sorting (be explicit about generic type to avoid raw Comparator inference)
        Comparator<IndexRecord> cmp = switch (f.sortBy) {
            case NAME -> Comparator.comparing((IndexRecord r) -> lower(r.name));
            case SET -> Comparator.comparing((IndexRecord r) -> lower(r.set))
                    .thenComparing(r -> lower(r.name));
            case RARITY -> Comparator.comparing((IndexRecord r) -> lower(r.rarity))
                    .thenComparing(r -> lower(r.name));
            case MV -> Comparator.comparingInt((IndexRecord r) -> r.mv);
            case TIMESTAMP -> Comparator.comparingLong((IndexRecord r) -> r.timestamp);
        };
        if (!f.sortAsc) cmp = cmp.reversed();

        List<IndexRecord> filtered = s.sorted(cmp).collect(Collectors.toList());

        int pageSize = (c == null ? Math.max(1, f.pageSize) : c.pageSize());
        int offset   = (c == null ? 0 : Math.max(0, c.offset()));
        int end      = Math.min(filtered.size(), offset + pageSize);
        List<IndexRecord> page = filtered.subList(offset, end);

        SearchResults res = new SearchResults();
        res.items = page;
        res.total = filtered.size();
        res.next  = (end < filtered.size()) ? new PageCursor(end, pageSize) : null;
        return res;
    }

    private static String lower(String s) {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    private boolean hasColor(Filters f, String color) {
        try {
            return f.colorsAny.contains(Filters.Color.valueOf(color));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // -------- Boolean query parser & matcher --------
    static final class Query {
        final List<Token> req = new ArrayList<>();
        final List<Token> neg = new ArrayList<>();

        static Query parse(String raw) {
            Query q = new Query();
            if (raw == null || raw.isBlank()) return q;

            for (String part : lex(raw)) {
                boolean isNeg = part.startsWith("-");
                String body = isNeg ? part.substring(1) : part;

                String field = null, term = body;
                int idx = body.indexOf(':');
                if (idx > 0) {
                    field = body.substring(0, idx);
                    term  = body.substring(idx + 1);
                }

                boolean prefix = term.endsWith("*");
                if (prefix) term = term.substring(0, term.length() - 1);

                Token t = new Token(field, term.toLowerCase(Locale.ROOT), prefix);
                (isNeg ? q.neg : q.req).add(t);
            }
            return q;
        }

        static List<String> lex(String s) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQ = false;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '"') { inQ = !inQ; continue; }
                if (Character.isWhitespace(ch) && !inQ) {
                    if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                    continue;
                }
                cur.append(ch);
            }
            if (cur.length() > 0) out.add(cur.toString());
            return out;
        }

        boolean isEmpty() { return req.isEmpty() && neg.isEmpty(); }

        boolean matches(IndexRecord r) {
            return req.stream().allMatch(t -> t.matches(r))
                    && neg.stream().noneMatch(t -> t.matches(r));
        }
    }

    static final class Token {
        final String field;
        final String term;
        final boolean prefix;

        Token(String f, String t, boolean p) { field = f; term = t; prefix = p; }

        boolean matches(IndexRecord r) {
            String f = (field == null) ? "*" : field;
            return switch (f) {
                case "name" -> in(r.nameTokens) || contains(r.name);
                case "type" -> in(r.typeTokens) || contains(r.typeLine);
                case "text", "oracle" -> in(r.oracleTokens);
                default -> in(r.nameTokens) || in(r.typeTokens) || in(r.oracleTokens) || contains(r.name);
            };
        }

        private boolean in(List<String> toks) {
            if (toks == null) return false;
            return prefix ? toks.stream().anyMatch(x -> x.startsWith(term))
                    : toks.contains(term);
        }

        private boolean contains(String s) {
            if (s == null) return false;
            String x = s.toLowerCase(Locale.ROOT);
            return prefix ? x.startsWith(term) : x.contains(term);
        }
    }
}
