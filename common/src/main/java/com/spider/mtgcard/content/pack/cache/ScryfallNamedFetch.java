package com.spider.mtgcard.content.pack.cache;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal "cards/named?fuzzy=" resolver:
 * returns (set, collector_number, name) for a fuzzy name query.
 */
public final class ScryfallNamedFetch {
    private static final Gson GSON = new Gson();

    public record NamedHit(String name, String set, String collectorNumber) {}

    public static CompletableFuture<NamedHit> fetchNamedFuzzyAsync(String nameQuery) {
        String q = (nameQuery == null) ? "" : nameQuery.trim();
        if (q.isEmpty()) return CompletableFuture.completedFuture(null);

        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String url = "https://api.scryfall.com/cards/named?fuzzy=" + enc;

        return ScryfallService.supplyAsync("named fuzzy " + q, () -> {
                    String body = ScryfallHttp.get(url);
                    JsonObject obj = GSON.fromJson(body, JsonObject.class);
                    if (obj == null) return null;

                    String nm = obj.has("name") ? obj.get("name").getAsString() : "";
                    String set = obj.has("set") ? obj.get("set").getAsString() : "";
                    String cn  = obj.has("collector_number") ? obj.get("collector_number").getAsString() : "";

                    nm = nm == null ? "" : nm;
                    set = set == null ? "" : set;
                    cn = cn == null ? "" : cn;

                    if (set.isBlank() || cn.isBlank()) return null;
                    return new NamedHit(nm, set.toLowerCase(), cn.toLowerCase());
                })
                .exceptionally(ex -> null);
    }

    private ScryfallNamedFetch() {}
}
