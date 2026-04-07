package com.spider.mtgcard.content.pack.cache;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.spider.mtgcard.config.MtgcardConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal "cards/named?fuzzy=" resolver:
 * returns (set, collector_number, name) for a fuzzy name query.
 */
public final class ScryfallNamedFetch {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson GSON = new Gson();

    public record NamedHit(String name, String set, String collectorNumber) {}

    public static CompletableFuture<NamedHit> fetchNamedFuzzyAsync(String nameQuery) {
        String q = (nameQuery == null) ? "" : nameQuery.trim();
        if (q.isEmpty()) return CompletableFuture.completedFuture(null);

        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.scryfall.com/cards/named?fuzzy=" + enc);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "MtgCardMod")
                .GET()
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) return null;

                    JsonObject obj = GSON.fromJson(resp.body(), JsonObject.class);
                    if (obj == null) return null;

                    // Scryfall fields
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
