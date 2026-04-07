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

            // cache + persist
            if (card != null && card.id != null && !card.id.isEmpty()) {
                synchronized (ScryfallCache.class) {
                    SESSION.put(card.id, card);
                    store(world).put(card);
                }
            }
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
    private static final Random RNG = new Random();
    private static final Map<String, ScryfallModels.Card> SESSION = new HashMap<>();
    private static PersistentCardStore PERSISTENT; // lazy

    private static PersistentCardStore store(ServerLevel w) {
        if (PERSISTENT == null) PERSISTENT = PersistentCardStore.load(w);
        return PERSISTENT;
    }

    /** Flexible picker (kept for other callers). */
    public static CompletableFuture<ScryfallModels.Card> pickRandomAsync(
            ServerLevel world, Context ctx, Query q, boolean foil, boolean allowVariant
    ) {
        final String query = buildQuery(ctx, q, foil, allowVariant);
        final String url = "https://api.scryfall.com/cards/random?q=" + url(query) + "&unique=prints";
        System.out.println("[mtgcard:packs] ScryfallFetch url=" + url + " onlySet=" + ctx.onlySet());

        return ScryfallService.supplyAsync("random query " + query, () -> {
            String body = ScryfallHttp.get(url);
            ScryfallModels.Card card = ScryfallJson.parseCard(body);

            if ((ctx.blacklist() != null && ctx.blacklist().contains(card.id))
                    || (ctx.excludeSets() != null && ctx.excludeSets().contains(card.set))) {
                String body2 = ScryfallHttp.get(url);
                card = ScryfallJson.parseCard(body2);
                System.out.println("[mtgcard:packs] ScryfallResult id=" + (card==null?"null":card.id)
                        + " name=" + (card==null?"null":card.name)
                        + " set=" + (card==null?"null":card.set));
            }

            if (card.id != null && !card.id.isEmpty()) {
                synchronized (ScryfallCache.class) {
                    SESSION.put(card.id, card);
                    store(world).put(card);
                }
            }
            return card;
        }).exceptionally(ex -> {
            synchronized (ScryfallCache.class) {
                if (!SESSION.isEmpty()) {
                    return new ArrayList<>(SESSION.values())
                            .get(RNG.nextInt(SESSION.size()));
                }
                throw new RuntimeException("Scryfall fetch failed: " + ex.getMessage(), ex);
            }
        });
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
