package com.spider.mtgcard.content.pack.cache;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ScryfallCollectionFetch {
    private static final Gson GSON = new Gson();

    public record Id(String set, String cn) {}

    public static CompletableFuture<Map<Id, ScryfallModels.Card>> fetchBySetCnBatchAsync(
            ServerLevel world,
            List<Id> ids
    ) {
        // Chunking should be done by caller (<= 75), but we’ll be safe:
        List<Id> slice = ids.size() > 75 ? ids.subList(0, 75) : ids;

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject root = new JsonObject();
                JsonArray arr = new JsonArray();

                for (Id id : slice) {
                    JsonObject one = new JsonObject();
                    one.addProperty("set", id.set());
                    one.addProperty("collector_number", id.cn());
                    arr.add(one);
                }
                root.add("identifiers", arr);

                String url = "https://api.scryfall.com/cards/collection";
                String body = root.toString();

                // You need a POST helper; add postJson() to ScryfallHttp (shown below).
                String json = ScryfallHttp.postJson(url, body);

                // Response is: { data:[...cards...], not_found:[...], ... }
                JsonObject resp = GSON.fromJson(json, JsonObject.class);
                JsonArray data = resp.has("data") ? resp.getAsJsonArray("data") : new JsonArray();

                Map<Id, ScryfallModels.Card> out = new HashMap<>();
                for (var el : data) {
                    if (!el.isJsonObject()) continue;
                    // Parse into your existing model type:
                    ScryfallModels.Card card = GSON.fromJson(el, ScryfallModels.Card.class);

                    String set = card.set == null ? "" : card.set.toLowerCase(Locale.ROOT);
                    String cn  = card.collectorNumber == null ? "" : card.collectorNumber;

                    if (!set.isBlank() && !cn.isBlank()) {
                        out.put(new Id(set, cn), card);
                    }
                }
                return out;
            } catch (Throwable t) {
                return Map.of();
            }
        });
    }

    private ScryfallCollectionFetch() {}
}