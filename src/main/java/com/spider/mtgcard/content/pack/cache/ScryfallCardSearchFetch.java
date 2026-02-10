
package com.spider.mtgcard.content.pack.cache;

import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.server.world.ServerWorld;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Multi-card search for partial names (ex: "Katara").
 * Uses Scryfall /cards/search with unique=prints so you get alt arts / showcase / promos too.
 */
public final class ScryfallCardSearchFetch {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // IMPORTANT: must be (name, set, collectorNumber) to match ScryfallJson.parseSearchPage(...)
    public record Hit(String name, String set, String collectorNumber) {}
    public record Page(List<Hit> hits, int totalCards, boolean hasMore) {}

    public static CompletableFuture<Page> fetchCardsSliceAsync(ServerWorld world, String userQuery, int page, int pageSize) {
        int p = Math.max(1, page);

        String q = buildQuery(userQuery);
        if (q.isEmpty()) {
            return CompletableFuture.completedFuture(new Page(List.of(), 0, false));
        }

        String url =
                "https://api.scryfall.com/cards/search"
                        + "?q=" + enc(q)
                        + "include%3Aextras"
                        + "&unique=prints"
                        + "&page=" + p
                        + "&order=released"
                        + "&dir=desc";

        return ScryfallService.supplyAsync(() -> {
            String body = ScryfallHttp.get(url);
            return ScryfallJson.parseSearchPage(body);
        });
    }

    /** If user didn't type advanced syntax, treat it as name:<query>. */
    private static String buildQuery(String user) {
        String t = (user == null) ? "" : user.trim();
        if (t.isEmpty()) return "";

        // Allow advanced searches as-is, but still optionally inject lang if missing
        String q = t.contains(":") ? t : ("name:" + t);
        q = q + " include:extras";

        // If user already included lang:, don't override it (case-insensitive check).
        String qLower = q.toLowerCase(Locale.ROOT);
        if (!qLower.contains("lang:")) {
            String lang = MtgcardConfig.get().Card_Language;
            lang = (lang == null) ? "" : lang.trim().toLowerCase(Locale.ROOT);

            if (!lang.isBlank() && !"en".equals(lang)) {
                q = q + " lang:" + lang;
            }
        }

        return q;
    }

    private ScryfallCardSearchFetch() {}
}