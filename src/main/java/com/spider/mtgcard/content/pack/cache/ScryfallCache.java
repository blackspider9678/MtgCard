package com.spider.mtgcard.content.pack.cache;

import com.spider.mtgcard.content.pack.PackGenerator;
import net.minecraft.server.level.ServerLevel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ScryfallCache {
    public record Context(
            boolean gamePaper,
            List<String> excludeSets,
            Set<String> blacklist,
            String onlySet // ← NEW: restrict all queries to a single set (3–5 char set code)
    ) {
        public static Builder builder(){ return new Builder(); }
        public static final class Builder {
            boolean gamePaper = true;
            List<String> exclude = List.of(); Set<String> blacklist = Set.of();
            String onlySet = null; // ← NEW

            public Builder gamePaper(boolean v){ gamePaper=v; return this; }
            public Builder excludeSets(List<String> v){ exclude=v; return this; }
            public Builder blacklist(Set<String> v){ blacklist=v; return this; }
            public Builder onlySet(String v){ onlySet=v; return this; } // ← NEW

            public Context build(){ return new Context(gamePaper, exclude, blacklist, onlySet); }
        }
    }

    // ------------------------------------------------------------------------------------
    // Hard-coded queries to mirror your JavaScript constants exactly
    // ------------------------------------------------------------------------------------
    public enum HardQuery { COMMON, WILDCARD_C_OR_U, UNCOMMON, RARE_OR_MYTHIC, BASIC, RANDOM, RANDOM_FOIL, TOKEN }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String hardUrl(HardQuery hq) {
        // Matches your JS:
        // exclude sets: 4bb, fbb, rin, ren, ps11, psal
        // use unique=cards and also include unique:prints in q (same as your URLs)
        final String base = "https://api.scryfall.com/cards/random?q=";
        switch (hq) {
            case COMMON: {
                String q = "(-type:basic -type:token) (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "rarity:c -is:foil unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case WILDCARD_C_OR_U: {
                String q = "(-type:basic -type:token) (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "(rarity:c OR rarity:u) -is:foil unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case UNCOMMON: {
                String q = "(-type:basic -type:token) (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "rarity:u -is:foil legal:commander unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case RARE_OR_MYTHIC: {
                String q = "(-type:basic -type:token) (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "(rarity:r OR rarity:m) -is:foil unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case BASIC: {
                String q = "type:land type:basic (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "-is:foil unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case RANDOM: {
                String q = "(-type:basic -type:token) (game:paper) (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "-is:foil unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case RANDOM_FOIL: {
                // You used: -type:basic is:borderless (no game:paper in the JS string) + legal:commander + unique:prints
                String q = "-type:basic is:borderless (-set:4bb -set:fbb -set:rin -set:ren -set:ps11 -set:psal) "
                        + "unique:prints";
                return base + enc(q) + "&unique=cards";
            }
            case TOKEN: {
                String q = "(type:dungeon OR type:emblem OR type:token) (game:paper) unique:prints";
                return base + enc(q) + "&unique=cards";
            }
        }
        throw new IllegalArgumentException("Unknown HardQuery: " + hq);
    }

    /** Fetch with the hard-coded JS-style URL set above. */
    public static CompletableFuture<ScryfallModels.Card> pickHardAsync(ServerLevel world, HardQuery hq) {
        final String url = hardUrl(hq);
        return ScryfallService.supplyAsync("hard random " + hq.name(), () -> {
            String body = ScryfallHttp.get(url);
            ScryfallModels.Card card = ScryfallJson.parseCard(body);

            cacheCard(world, card);
            return card;
        }).exceptionally(ex -> {
            synchronized (ScryfallCache.class) {
                if (!SESSION.isEmpty()) {
                    return new ArrayList<>(SESSION.values()).get(RNG.nextInt(SESSION.size()));
                }
                throw new RuntimeException("Scryfall hard fetch failed: " + ex.getMessage(), ex);
            }
        });
    }

    // Put near your Scryfall query code
    private static String setClauseForSlot(String desiredSet, PackGenerator.RaritySlot slot) {
        if (desiredSet == null || desiredSet.isBlank()) return null;
        if (slot == PackGenerator.RaritySlot.TOKEN_OR_ART) {
            // tokens often live in t<set> on Scryfall
            return "(set:" + desiredSet + " OR set:t" + desiredSet + ")";
        }
        return "set:" + desiredSet;
    }


    // ------------------------------------------------------------------------------------
    // (Existing) flexible query path — keep for other features
    // ------------------------------------------------------------------------------------
    public sealed interface Query permits Query.Rarity, Query.BasicLand, Query.Random, Query.TokenExtra,
            Query.RarityMax, Query.RarityMin, Query.Legendary {
        record Rarity(String rarity) implements Query {}
        record BasicLand() implements Query {}
        record Random() implements Query {}
        record TokenExtra() implements Query {}
        record RarityMax(String code) implements Query {}
        record RarityMin(String code) implements Query {}
        record Legendary() implements Query {}

        public static Query common(){ return new Rarity("c"); }
        public static Query uncommon(){ return new Rarity("u"); }
        public static Query rare(){ return new Rarity("r"); }
        public static Query mythic(){ return new Rarity("m"); }
        public static Query basicLand(){ return new BasicLand(); }
        public static Query randomNonBasic(){ return new Random(); }
        public static Query tokenOrExtra(){ return new TokenExtra(); }
        public static Query commonOrUncommon(){ return new RarityMax("u"); }
        public static Query rareOrMythic(){ return new RarityMin("r"); }
        public static Query legendary(){ return new Legendary(); }
    }

    public static Set<String> getBlackout(ServerLevel world){ return Set.of(); }

    // ---- caches ----
    private static final int QUERY_POOL_TARGET = 36;
    private static final int QUERY_POOL_LOW_WATER = 8;
    private static final int SCRYFALL_SEARCH_PAGE_SIZE = 175;
    private static final int MAX_RANDOM_SEARCH_PAGE = 50;
    private static final Random RNG = new Random();
    private static final Map<String, ScryfallModels.Card> SESSION = new HashMap<>();
    private static final Map<String, QueryPool> QUERY_POOLS = new HashMap<>();
    private static PersistentCardStore PERSISTENT; // lazy

    private static final class QueryPool {
        private final ArrayList<ScryfallModels.Card> cards = new ArrayList<>();
        private CompletableFuture<List<ScryfallModels.Card>> refill;
    }

    private static PersistentCardStore store(ServerLevel w) {
        if (PERSISTENT == null) PERSISTENT = PersistentCardStore.load(w);
        return PERSISTENT;
    }

    private static void cacheCard(ServerLevel world, ScryfallModels.Card card) {
        if (card == null || card.id == null || card.id.isEmpty()) return;
        synchronized (ScryfallCache.class) {
            SESSION.put(card.id, card);
            store(world).put(card);
        }
    }

    private static void cacheCards(ServerLevel world, Collection<ScryfallModels.Card> cards) {
        if (cards == null || cards.isEmpty()) return;
        for (ScryfallModels.Card card : cards) cacheCard(world, card);
    }

    private static QueryPool queryPool(String key) {
        synchronized (ScryfallCache.class) {
            return QUERY_POOLS.computeIfAbsent(key, ignored -> new QueryPool());
        }
    }

    /** Flexible picker (kept for other callers). */
    public static CompletableFuture<ScryfallModels.Card> pickRandomAsync(
            ServerLevel world, Context ctx, Query q, boolean foil, boolean allowVariant
    ) {
        final String query = buildQuery(ctx, q, foil, allowVariant);
        return pickFromPoolAsync(world, ctx, q, query)
                .exceptionally(ex -> null)
                .thenCompose(card -> {
                    if (card != null) return CompletableFuture.completedFuture(card);
                    return fetchRandomRemoteAsync(world, ctx, q, query);
                })
                .exceptionally(ex -> {
                    ScryfallModels.Card fallback = fallbackFromLocalCache(world, ctx, q);
                    if (fallback != null) return fallback;
                    throw new RuntimeException("Scryfall fetch failed: " + ex.getMessage(), ex);
                });
    }

    private static CompletableFuture<ScryfallModels.Card> pickFromPoolAsync(
            ServerLevel world, Context ctx, Query q, String query
    ) {
        QueryPool pool = queryPool(query);

        ScryfallModels.Card cached = takeFromPool(pool, ctx, q);
        if (cached != null) {
            if (poolSize(pool) < QUERY_POOL_LOW_WATER) ensurePoolRefill(world, query, pool);
            return CompletableFuture.completedFuture(cached);
        }

        if (seedPoolFromLocalCache(world, ctx, q, pool) > 0) {
            ScryfallModels.Card seeded = takeFromPool(pool, ctx, q);
            if (seeded != null) {
                if (poolSize(pool) < QUERY_POOL_LOW_WATER) ensurePoolRefill(world, query, pool);
                return CompletableFuture.completedFuture(seeded);
            }
        }

        return ensurePoolRefill(world, query, pool)
                .exceptionally(ex -> List.of())
                .thenApply(ignored -> {
                    ScryfallModels.Card refilled = takeFromPool(pool, ctx, q);
                    if (refilled != null && poolSize(pool) < QUERY_POOL_LOW_WATER) {
                        ensurePoolRefill(world, query, pool);
                    }
                    return refilled;
                });
    }

    private static CompletableFuture<ScryfallModels.Card> fetchRandomRemoteAsync(
            ServerLevel world, Context ctx, Query q, String query
    ) {
        final String url = "https://api.scryfall.com/cards/random?q=" + url(query) + "&unique=prints";
        System.out.println("[mtgcard:packs] ScryfallFetch url=" + url + " onlySet=" + ctx.onlySet());

        return ScryfallService.supplyAsync("random query " + query, () -> {
            for (int attempt = 0; attempt < 2; attempt++) {
                String body = ScryfallHttp.get(url);
                ScryfallModels.Card card = ScryfallJson.parseCard(body);
                if (matchesQuery(card, ctx, q)) {
                    cacheCard(world, card);
                    return card;
                }
            }
            throw new RuntimeException("No matching Scryfall result for query: " + query);
        });
    }

    private static CompletableFuture<List<ScryfallModels.Card>> ensurePoolRefill(
            ServerLevel world, String query, QueryPool pool
    ) {
        synchronized (pool) {
            if (pool.refill != null && !pool.refill.isDone()) return pool.refill;

            CompletableFuture<List<ScryfallModels.Card>> refill = refillPoolAsync(world, query);
            pool.refill = refill;
            refill.whenComplete((cards, ex) -> {
                synchronized (pool) {
                    if (ex == null && cards != null && !cards.isEmpty()) addToPool(pool, cards);
                    if (pool.refill == refill) pool.refill = null;
                }
            });
            return refill;
        }
    }

    private static CompletableFuture<List<ScryfallModels.Card>> refillPoolAsync(ServerLevel world, String query) {
        return ScryfallPrintSearchFetch.fetchSearchAsync(query, 1).thenCompose(firstPage -> {
            if (firstPage == null || firstPage.totalCards() <= 0 || firstPage.hits().isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            int totalPages = Math.max(1, (int) Math.ceil(firstPage.totalCards() / (double) SCRYFALL_SEARCH_PAGE_SIZE));
            int pageCap = Math.max(1, Math.min(totalPages, MAX_RANDOM_SEARCH_PAGE));
            int chosenPage = 1 + RNG.nextInt(pageCap);

            CompletableFuture<ScryfallPrintSearchFetch.Page> pageFuture =
                    (chosenPage == 1)
                            ? CompletableFuture.completedFuture(firstPage)
                            : ScryfallPrintSearchFetch.fetchSearchAsync(query, chosenPage)
                            .exceptionally(ex -> firstPage);

            return pageFuture.thenCompose(page -> {
                List<ScryfallPrintSearchFetch.PrintHit> sample = sampleHits(page.hits(), QUERY_POOL_TARGET);
                if (sample.isEmpty() && page != firstPage) {
                    sample = sampleHits(firstPage.hits(), QUERY_POOL_TARGET);
                }
                if (sample.isEmpty()) return CompletableFuture.completedFuture(List.of());

                return ScryfallExactFetch.fetchCollectionByPrintHitsAsync(sample).thenApply(cards -> {
                    List<ScryfallModels.Card> usable = sanitize(cards);
                    cacheCards(world, usable);
                    return usable;
                });
            });
        });
    }

    private static List<ScryfallPrintSearchFetch.PrintHit> sampleHits(
            List<ScryfallPrintSearchFetch.PrintHit> hits, int count
    ) {
        if (hits == null || hits.isEmpty() || count <= 0) return List.of();
        ArrayList<ScryfallPrintSearchFetch.PrintHit> copy = new ArrayList<>(hits);
        Collections.shuffle(copy, RNG);
        if (copy.size() > count) copy.subList(count, copy.size()).clear();
        return copy;
    }

    private static List<ScryfallModels.Card> sanitize(List<ScryfallModels.Card> cards) {
        if (cards == null || cards.isEmpty()) return List.of();
        ArrayList<ScryfallModels.Card> usable = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ScryfallModels.Card card : cards) {
            if (card == null || card.id == null || card.id.isBlank()) continue;
            if (seen.add(card.id)) usable.add(card);
        }
        return usable;
    }

    private static int seedPoolFromLocalCache(
            ServerLevel world, Context ctx, Query q, QueryPool pool
    ) {
        synchronized (pool) {
            if (!pool.cards.isEmpty()) return 0;
        }

        ArrayList<ScryfallModels.Card> matches = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        synchronized (ScryfallCache.class) {
            for (ScryfallModels.Card card : SESSION.values()) {
                if (matchesQuery(card, ctx, q) && seen.add(card.id)) matches.add(card);
            }
        }
        for (ScryfallModels.Card card : store(world).snapshot()) {
            if (matchesQuery(card, ctx, q) && seen.add(card.id)) matches.add(card);
        }

        if (matches.isEmpty()) return 0;
        Collections.shuffle(matches, RNG);
        if (matches.size() > QUERY_POOL_TARGET) matches.subList(QUERY_POOL_TARGET, matches.size()).clear();

        synchronized (pool) {
            addToPool(pool, matches);
            return pool.cards.size();
        }
    }

    private static void addToPool(QueryPool pool, List<ScryfallModels.Card> cards) {
        HashSet<String> existing = new HashSet<>();
        for (ScryfallModels.Card card : pool.cards) {
            if (card != null && card.id != null && !card.id.isBlank()) existing.add(card.id);
        }
        for (ScryfallModels.Card card : cards) {
            if (card == null || card.id == null || card.id.isBlank()) continue;
            if (existing.add(card.id)) pool.cards.add(card);
        }
        while (pool.cards.size() > QUERY_POOL_TARGET * 2) {
            pool.cards.remove(RNG.nextInt(pool.cards.size()));
        }
    }

    private static ScryfallModels.Card takeFromPool(QueryPool pool, Context ctx, Query q) {
        synchronized (pool) {
            if (pool.cards.isEmpty()) return null;

            ArrayList<Integer> matching = new ArrayList<>();
            for (int i = 0; i < pool.cards.size(); i++) {
                if (matchesQuery(pool.cards.get(i), ctx, q)) matching.add(i);
            }
            if (matching.isEmpty()) return null;

            int chosen = matching.get(RNG.nextInt(matching.size()));
            return pool.cards.remove(chosen);
        }
    }

    private static int poolSize(QueryPool pool) {
        synchronized (pool) {
            return pool.cards.size();
        }
    }

    private static ScryfallModels.Card fallbackFromLocalCache(ServerLevel world, Context ctx, Query q) {
        ArrayList<ScryfallModels.Card> matches = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        synchronized (ScryfallCache.class) {
            for (ScryfallModels.Card card : SESSION.values()) {
                if (matchesQuery(card, ctx, q) && seen.add(card.id)) matches.add(card);
            }
        }
        if (matches.isEmpty()) {
            for (ScryfallModels.Card card : store(world).snapshot()) {
                if (matchesQuery(card, ctx, q) && seen.add(card.id)) matches.add(card);
            }
        }
        if (!matches.isEmpty()) return matches.get(RNG.nextInt(matches.size()));
        return null;
    }

    private static boolean matchesQuery(ScryfallModels.Card card, Context ctx, Query q) {
        if (card == null || card.id == null || card.id.isBlank()) return false;

        String id = card.id;
        String set = lower(card.set);
        if (ctx != null) {
            if (ctx.blacklist() != null && ctx.blacklist().contains(id)) return false;
            if (ctx.excludeSets() != null && !set.isBlank() && ctx.excludeSets().contains(set)) return false;
        }

        if (q == null) return true;

        String onlySet = (ctx == null || ctx.onlySet() == null) ? "" : lower(ctx.onlySet());
        if (!onlySet.isBlank()) {
            if (q instanceof Query.TokenExtra) {
                if (!set.equals(onlySet) && !set.equals("t" + onlySet)) return false;
            } else if (!set.equals(onlySet)) {
                return false;
            }
        }

        String typeLine = lower(card.typeLine);
        String layout = lower(card.layout);
        boolean isBasic = typeLine.contains("basic land");
        boolean isTokenLike = card.isTokenLike
                || typeLine.contains("token")
                || typeLine.contains("emblem")
                || typeLine.contains("dungeon")
                || typeLine.contains("art series")
                || layout.contains("art_series");

        if (q instanceof Query.TokenExtra) return isTokenLike;
        if (q instanceof Query.BasicLand) return isBasic;
        if (isBasic || isTokenLike) return false;

        String rarity = normalizeRarityCode(card.rarity);
        if (q instanceof Query.Rarity r) return rarity.equals(normalizeRarityCode(r.rarity()));
        if (q instanceof Query.RarityMax r) return rarityRank(rarity) <= rarityRank(normalizeRarityCode(r.code()));
        if (q instanceof Query.RarityMin r) return rarityRank(rarity) >= rarityRank(normalizeRarityCode(r.code()));
        if (q instanceof Query.Legendary) return typeLine.contains("legendary");
        return true;
    }

    private static String normalizeRarityCode(String rarity) {
        if (rarity == null) return "";
        return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
            case "c", "common" -> "c";
            case "u", "uncommon" -> "u";
            case "r", "rare" -> "r";
            case "m", "mythic", "mythic rare" -> "m";
            default -> rarity.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static int rarityRank(String rarity) {
        return switch (normalizeRarityCode(rarity)) {
            case "c" -> 1;
            case "u" -> 2;
            case "r" -> 3;
            case "m" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ---- query building (flexible path) ----
    private static String buildQuery(Context ctx, Query q, boolean foil, boolean allowVariant) {
        List<String> parts = new ArrayList<>();

        // ---- TOKEN/ART: no legality filter, no "-type:token" guard ----
        if (q instanceof Query.TokenExtra) {
            parts.add("(type:token OR type:emblem OR type:dungeon OR is:artseries)");
            if (ctx.gamePaper) parts.add("game:paper");
            // keep your set lock and exclude sets
            if (ctx.excludeSets != null) for (String s : ctx.excludeSets) parts.add("-set:" + s);
            if (ctx.onlySet != null && !ctx.onlySet.isBlank()) {
                parts.add("(set:" + ctx.onlySet + " OR set:t" + ctx.onlySet + ")");
            }
            return String.join(" ", parts);
        }

        // ---- BASIC LANDS ----
        if (q instanceof Query.BasicLand) {
            parts.add("type:land");
            parts.add("type:basic");
            if (ctx.gamePaper) parts.add("game:paper");
            if (ctx.excludeSets != null) for (String s : ctx.excludeSets) parts.add("-set:" + s);
            if (ctx.onlySet != null && !ctx.onlySet.isBlank()) parts.add("set:" + ctx.onlySet);
            return String.join(" ", parts);
        }

        // ---- DEFAULT (non-basic, non-token) ----
        parts.add("-type:basic");
        parts.add("-type:token");
        parts.add("-type:emblem");
        parts.add("-type:dungeon");
        parts.add("-is:artseries");
        if (ctx.gamePaper) parts.add("game:paper");
        if (ctx.excludeSets != null) for (String s : ctx.excludeSets) parts.add("-set:" + s);
        if (ctx.onlySet != null && !ctx.onlySet.isBlank()) parts.add("set:" + ctx.onlySet);

        if (q instanceof Query.Rarity r) {
            String rar = switch (r.rarity()) {
                case "c" -> "c"; case "u" -> "u"; case "r" -> "r"; case "m" -> "m"; default -> "c";
            };
            parts.add("rarity:" + rar);
        } else if (q instanceof Query.RarityMax rm) {
            parts.add("rarity<=" + rm.code());
        } else if (q instanceof Query.RarityMin rn) {
            parts.add("rarity>=" + rn.code());
        } else if (q instanceof Query.Legendary) {
            parts.add("type:legendary");
        }

        return String.join(" ", parts);
    }



    private static String url(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static void flush(ServerLevel world) {
        try { store(world).save(); } catch (Exception ignored) {}
    }

    // Map our slot → ScryfallCache.HardQuery for a guaranteed global fallback
    public static HardQuery hardQueryFor(PackGenerator.RaritySlot slot) {
        return switch (slot) {
            case COMMON       -> HardQuery.COMMON;
            case UNCOMMON     -> HardQuery.UNCOMMON;
            case WILDCARD_C_OR_U -> HardQuery.WILDCARD_C_OR_U;
            case RARE_OR_MYTHIC -> HardQuery.RARE_OR_MYTHIC;
            case BASIC_LAND   -> HardQuery.BASIC;
            case RANDOM       -> HardQuery.RANDOM;
            case FOIL_RANDOM  -> HardQuery.RANDOM_FOIL;
            case TOKEN_OR_ART -> HardQuery.TOKEN;
        };
    }


    private ScryfallCache() {}
}
