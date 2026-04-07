package com.spider.mtgcard.content.pack.cache;

import com.google.gson.*;
import com.spider.mtgcard.config.MtgcardConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ScryfallPrintSearchFetch {

    public record PrintHit(String set, String collectorNumber) {}
    public record Page(List<PrintHit> hits, int totalCards, boolean hasMore) {}

    /**
     * Mirrors: https://scryfall.com/search?q=<term>&order=name&unique=prints
     * API: /cards/search?q=<term>&order=name&unique=prints
     */
    public static CompletableFuture<Page> fetchSearchSliceAsync(String term, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;
        return fetchSearchOffsetSliceAsync(term, offset, pageSize);
    }

    public static CompletableFuture<Page> fetchSearchOffsetSliceAsync(String term, int offset, int limit) {
        final int SCRY_PAGE_SIZE = 175;

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);

        int scryPage = (safeOffset / SCRY_PAGE_SIZE) + 1;
        int inPageOffset = safeOffset % SCRY_PAGE_SIZE;

        return fetchSearchAsync(term, scryPage).thenCompose(p1 -> {
            int total = p1.totalCards();
            boolean ourHasMore = total > safeOffset + safeLimit;

            List<PrintHit> slice = new ArrayList<>();
            List<PrintHit> hits1 = p1.hits();

            if (inPageOffset < hits1.size()) {
                slice.addAll(hits1.subList(inPageOffset, Math.min(hits1.size(), inPageOffset + safeLimit)));
            }

            int remaining = safeLimit - slice.size();
            if (remaining > 0 && p1.hasMore()) {
                return fetchSearchAsync(term, scryPage + 1).thenApply(p2 -> {
                    List<PrintHit> hits2 = p2.hits();
                    slice.addAll(hits2.subList(0, Math.min(remaining, hits2.size())));
                    return new Page(slice, total, ourHasMore);
                });
            }

            return CompletableFuture.completedFuture(new Page(slice, total, ourHasMore));
        });
    }

    public static CompletableFuture<Page> fetchSearchAsync(String term, int page) {
        String q = (term == null) ? "" : term.trim();
        if (q.isEmpty()) return CompletableFuture.completedFuture(new Page(List.of(), 0, false));

        // Inject preferred language unless the user already specified lang:
        q = applyPreferredLang(q);

        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);

        String url = "https://api.scryfall.com/cards/search?q=" + enc
                + "&page=" + Math.max(1, page)
                + "&order=name"
                + "&unique=prints";

        return ScryfallService.supplyAsync(() -> {
            String body = ScryfallHttp.get(url);
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int total = root.has("total_cards") ? root.get("total_cards").getAsInt() : 0;
            boolean hasMore = root.has("has_more") && root.get("has_more").getAsBoolean();

            List<PrintHit> out = new ArrayList<>();
            JsonArray data = root.getAsJsonArray("data");
            if (data != null) {
                for (JsonElement el : data) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String set = o.has("set") ? o.get("set").getAsString() : "";
                    String cn  = o.has("collector_number") ? o.get("collector_number").getAsString() : "";
                    if (!set.isBlank() && !cn.isBlank()) out.add(new PrintHit(set, cn));
                }
            }
            return new Page(out, total, hasMore);
        });
    }

    private static String applyPreferredLang(String q) {
        // If the user already typed lang:, don't override.
        if (q.contains("lang:")) return q;

        String lang = MtgcardConfig.get().Card_Language;
        if (lang == null) return q;

        lang = lang.trim().toLowerCase(Locale.ROOT);
        if (lang.isBlank() || "en".equals(lang)) return q;

        // If this is an "advanced" query they typed, still safe to append.
        return q + " lang:" + lang;
    }

    private ScryfallPrintSearchFetch() {}
}
