package com.spider.mtgcard.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ScryfallInfoManager {
    private ScryfallInfoManager() {}

    private static final boolean DEBUG = true;

    private static void log(String msg) {
        if (!DEBUG) return;
    }

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // "Feels live" but avoids hammering Scryfall.
    private static final long TTL_MS = 15 * 60 * 1000L;

    private static final ConcurrentHashMap<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> IN_FLIGHT = new ConcurrentHashMap<>();

    public static Entry getCached(String scryfallId) {
        if (scryfallId == null || scryfallId.isBlank()) {
            return null;
        }
        Entry e = CACHE.get(scryfallId);
        return e;
    }

    /** Ensures we have fresh-ish data. Returns immediately; fetch is async if needed. */
    public static void ensureFresh(String scryfallId) {
        if (scryfallId == null || scryfallId.isBlank()) {
            return;
        }

        Entry existing = CACHE.get(scryfallId);
        if (existing != null && existing.isFresh()) {
            return;
        }

        CompletableFuture<Void> in = IN_FLIGHT.get(scryfallId);
        if (in != null && !in.isDone()) {
            return;
        }

        IN_FLIGHT.computeIfAbsent(scryfallId, ScryfallInfoManager::fetchAsync);
    }

    /** Always re-fetch (used when opening the info panel). */
    public static void forceRefresh(String scryfallId) {
        if (scryfallId == null || scryfallId.isBlank()) {
            return;
        }

        CompletableFuture<Void> in = IN_FLIGHT.get(scryfallId);
        if (in != null && !in.isDone()) {
            return;
        }

        IN_FLIGHT.computeIfAbsent(scryfallId, ScryfallInfoManager::fetchAsync);
    }

    public static boolean isFetching(String scryfallId) {
        if (scryfallId == null || scryfallId.isBlank()) return false;
        var f = IN_FLIGHT.get(scryfallId);
        return f != null && !f.isDone();
    }

    private static CompletableFuture<Void> fetchAsync(String scryfallId) {
        long start = System.currentTimeMillis();
        URI uri = URI.create("https://api.scryfall.com/cards/" + scryfallId);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "MtgCardMod/1.0 (Minecraft client)")
                .GET()
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    long took = System.currentTimeMillis() - start;
                    int code = resp.statusCode();
                    String body = resp.body();

                    if (code / 100 != 2) {
                        CACHE.put(scryfallId, Entry.blank());
                        return;
                    }

                    try {
                        JsonObject root = GSON.fromJson(body, JsonObject.class);
                        if (root == null) {
                            CACHE.put(scryfallId, Entry.blank());
                            return;
                        }

                        Entry entry = Entry.fromScryfall(root);
                        CACHE.put(scryfallId, entry);

                    } catch (Throwable t) {
                        CACHE.put(scryfallId, Entry.blank());
                    }
                })
                .exceptionally(ex -> {
                    long took = System.currentTimeMillis() - start;
                    CACHE.put(scryfallId, Entry.blank());
                    return null;
                })
                .whenComplete((v, t) -> {
                    IN_FLIGHT.remove(scryfallId);
                });
    }

    public static final class Entry {
        public final long fetchedAtMs;
        public final String usd, usdFoil, usdEtched, eur, eurFoil, tix;
        public final Map<String, String> legalities;

        private Entry(long fetchedAtMs,
                      String usd, String usdFoil, String usdEtched,
                      String eur, String eurFoil, String tix,
                      Map<String, String> legalities) {
            this.fetchedAtMs = fetchedAtMs;
            this.usd = usd;
            this.usdFoil = usdFoil;
            this.usdEtched = usdEtched;
            this.eur = eur;
            this.eurFoil = eurFoil;
            this.tix = tix;
            this.legalities = legalities;
        }

        public boolean isFresh() {
            return (System.currentTimeMillis() - fetchedAtMs) <= TTL_MS;
        }

        public static Entry blank() {
            long now = System.currentTimeMillis();
            return new Entry(now, "—","—","—","—","—","—", new ConcurrentHashMap<>());
        }

        public static Entry fromLocalMeta(CompoundTag meta) {
            if (meta == null) return blank();

            long now = System.currentTimeMillis();
            String usd = readMetaPrice(meta, "usd", "price_usd", "price");
            String usdFoil = readNestedMetaPrice(meta, "usd_foil", "usdFoil");
            String usdEtched = readNestedMetaPrice(meta, "usd_etched", "usdEtched");
            String eur = readNestedMetaPrice(meta, "eur");
            String eurFoil = readNestedMetaPrice(meta, "eur_foil", "eurFoil");
            String tix = readNestedMetaPrice(meta, "tix");

            Map<String, String> legalities = new ConcurrentHashMap<>();
            meta.getCompound("legalities").ifPresent(legs -> {
                for (String key : legs.keySet()) {
                    String value = legs.getString(key).orElse("");
                    if (!value.isBlank()) legalities.put(key, value);
                }
            });

            return new Entry(now, usd, usdFoil, usdEtched, eur, eurFoil, tix, legalities);
        }

        private static String readPrice(JsonObject prices, String key) {
            if (prices == null) return "—";
            if (!prices.has(key) || prices.get(key).isJsonNull()) return "—";
            String s = prices.get(key).getAsString();
            return (s == null || s.isBlank()) ? "—" : s;
        }

        private static String readMetaPrice(CompoundTag meta, String... keys) {
            if (meta == null || keys == null) return "â€”";

            for (String key : keys) {
                String direct = readMetaScalar(meta, key);
                if (!direct.equals("â€”")) return direct;
            }
            return readNestedMetaPrice(meta, keys);
        }

        private static String readNestedMetaPrice(CompoundTag meta, String... keys) {
            if (meta == null || keys == null) return "â€”";
            for (String compoundKey : new String[]{"prices", "price"}) {
                CompoundTag prices = meta.getCompound(compoundKey).orElse(null);
                if (prices == null) continue;
                for (String key : keys) {
                    String value = readMetaScalar(prices, key);
                    if (!value.equals("â€”")) return value;
                }
            }
            return "â€”";
        }

        private static String readMetaScalar(CompoundTag tag, String key) {
            if (tag == null || key == null || key.isBlank()) return "â€”";

            String value = tag.getString(key).orElse("").trim();
            if (!value.isBlank()) return value;

            var d = tag.getDouble(key);
            if (d.isPresent()) return String.format(java.util.Locale.ROOT, "%.2f", d.get());

            var i = tag.getInt(key);
            if (i.isPresent()) return String.valueOf(i.get());

            return "â€”";
        }

        public static Entry fromScryfall(JsonObject root) {
            long now = System.currentTimeMillis();

            JsonObject prices = root.has("prices") && root.get("prices").isJsonObject()
                    ? root.getAsJsonObject("prices")
                    : null;

            String usd = readPrice(prices, "usd");
            String usdFoil = readPrice(prices, "usd_foil");
            String usdEtched = readPrice(prices, "usd_etched");
            String eur = readPrice(prices, "eur");
            String eurFoil = readPrice(prices, "eur_foil");
            String tix = readPrice(prices, "tix");

            Map<String, String> legalities = new ConcurrentHashMap<>();
            if (root.has("legalities") && root.get("legalities").isJsonObject()) {
                JsonObject legs = root.getAsJsonObject("legalities");
                for (var e : legs.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isJsonNull()) {
                        legalities.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }

            return new Entry(now, usd, usdFoil, usdEtched, eur, eurFoil, tix, legalities);
        }
    }
}
