package com.spider.mtgcard.content.pack.cache;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.server.level.ServerLevel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ScryfallExactFetch {

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static CompletableFuture<ScryfallModels.Card> fetchBySetCollectorAsync(ServerLevel world, String setCode, String collectorNumber) {
        String set = (setCode == null) ? "" : setCode.trim().toLowerCase(Locale.ROOT);

        // ✅ DO NOT lowercase collector number
        String cn  = (collectorNumber == null) ? "" : collectorNumber.trim();

        final String baseUrl = "https://api.scryfall.com/cards/" + enc(set) + "/" + enc(cn);

        // Preferred language
        String lang = MtgcardConfig.get().Card_Language;
        lang = (lang == null) ? "" : lang.trim().toLowerCase(Locale.ROOT);

        // ✅ fallback query (works when printing route fails)
        final String searchUrl = "https://api.scryfall.com/cards/search?q="
                + enc("set:" + set + " cn:" + cn);

        if (!lang.isBlank() && !"en".equals(lang)) {
            final String langUrl = baseUrl + "/" + enc(lang);

            return ScryfallService.supplyAsync(() -> {
                try {
                    String body = ScryfallHttp.get(langUrl);
                    return ScryfallJson.parseCard(body);
                } catch (Throwable ignored) {
                    // 2) fallback to English/default printing endpoint
                    try {
                        String body = ScryfallHttp.get(baseUrl);
                        return ScryfallJson.parseCard(body);
                    } catch (Throwable ignored2) {
                        // 3) final fallback: search endpoint, first result
                        String body = ScryfallHttp.get(searchUrl);
                        return ScryfallJson.parseFirstCardFromSearch(body);
                    }
                }
            });
        }

        // English/default with fallback
        return ScryfallService.supplyAsync(() -> {
            try {
                String body = ScryfallHttp.get(baseUrl);
                return ScryfallJson.parseCard(body);
            } catch (Throwable ignored) {
                String body = ScryfallHttp.get(searchUrl);
                return ScryfallJson.parseFirstCardFromSearch(body);
            }
        });

    }
    // in ScryfallHttp

    public static CompletableFuture<List<ScryfallModels.Card>> fetchCollectionBySetCollectorAsync(
            ServerLevel world,
            List<ScryfallCardSearchFetch.Hit> hits
    ) {
        if (hits == null || hits.isEmpty()) return CompletableFuture.completedFuture(List.of());

        final int CHUNK = 75;
        final String url = "https://api.scryfall.com/cards/collection";

        return ScryfallService.supplyAsync(() -> {
            ArrayList<ScryfallModels.Card> all = new ArrayList<>();

            for (int i = 0; i < hits.size(); i += CHUNK) {
                int end = Math.min(hits.size(), i + CHUNK);
                List<ScryfallCardSearchFetch.Hit> slice = hits.subList(i, end);

                JsonArray identifiers = new JsonArray();
                for (var h : slice) {
                    JsonObject id = new JsonObject();
                    id.addProperty("set", (h.set() == null ? "" : h.set().trim().toLowerCase(Locale.ROOT)));
                    id.addProperty("collector_number", (h.collectorNumber() == null ? "" : h.collectorNumber().trim()));
                    identifiers.add(id);
                }

                JsonObject body = new JsonObject();
                body.add("identifiers", identifiers);

                String resp;
                try {
                    resp = ScryfallHttp.postJson(url, body.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                List<ScryfallModels.Card> got = ScryfallJson.parseCollection(resp);
                if (got != null && !got.isEmpty()) all.addAll(got);
            }

            return all;
        });
    }

    public static CompletableFuture<List<ScryfallModels.Card>> fetchCollectionByPrintHitsAsync(
            List<ScryfallPrintSearchFetch.PrintHit> hits
    ) {
        if (hits == null || hits.isEmpty()) return CompletableFuture.completedFuture(List.of());

        final int CHUNK = 75;
        final String url = "https://api.scryfall.com/cards/collection";

        return ScryfallService.supplyAsync(() -> {
            ArrayList<ScryfallModels.Card> all = new ArrayList<>();

            for (int i = 0; i < hits.size(); i += CHUNK) {
                int end = Math.min(hits.size(), i + CHUNK);
                List<ScryfallPrintSearchFetch.PrintHit> slice = hits.subList(i, end);

                JsonArray identifiers = new JsonArray();
                for (var h : slice) {
                    JsonObject id = new JsonObject();
                    id.addProperty("set", (h.set() == null ? "" : h.set().trim().toLowerCase(Locale.ROOT)));
                    id.addProperty("collector_number", (h.collectorNumber() == null ? "" : h.collectorNumber().trim()));
                    identifiers.add(id);
                }

                JsonObject body = new JsonObject();
                body.add("identifiers", identifiers);

                String resp;
                try {
                    resp = ScryfallHttp.postJson(url, body.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                List<ScryfallModels.Card> got = ScryfallJson.parseCollection(resp);
                if (got != null && !got.isEmpty()) all.addAll(got);
            }

            return all;
        });
    }

    private ScryfallExactFetch() {}
}
