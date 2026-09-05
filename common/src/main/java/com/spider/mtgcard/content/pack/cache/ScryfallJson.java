package com.spider.mtgcard.content.pack.cache;

import com.google.gson.*;

import java.util.*;

final class ScryfallJson {
    private static final Gson GSON = new Gson();

    static ScryfallModels.Card parseCard(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return new ScryfallModels.Card();
        JsonObject o = root.getAsJsonObject();
        ScryfallModels.Card c = new ScryfallModels.Card();

        c.id               = getStr(o, "id");
        c.lang             = getStr(o, "lang");
        c.oracleId         = getStr(o, "oracle_id");
        c.name             = getStr(o, "name");
        c.set              = getStr(o, "set");
        c.collectorNumber  = getStr(o, "collector_number");
        c.rarity           = getStr(o, "rarity");
        c.layout           = getStr(o, "layout");
        c.manaCost         = getStr(o, "mana_cost");
        c.typeLine         = getStr(o, "type_line");
        c.oracleText       = getStr(o, "oracle_text");
        c.power            = getStr(o, "power");
        c.toughness        = getStr(o, "toughness");
        c.loyalty          = getStr(o, "loyalty");
        c.manaValue        = o.has("cmc") ? o.get("cmc").getAsInt() : 0;

        c.colors        = getStringList(o, "colors");
        c.colorIdentity = getStringList(o, "color_identity");
        c.legalities    = getStringMap(o.getAsJsonObject("legalities"));
        c.scryfallUri   = getStr(o, "scryfall_uri");

        // single-face images
        if (o.has("image_uris") && o.get("image_uris").isJsonObject()) {
            c.imageUris = getStringMap(o.getAsJsonObject("image_uris"));
        }

        // faces
        if (o.has("card_faces") && o.get("card_faces").isJsonArray()) {
            JsonArray arr = o.getAsJsonArray("card_faces");
            List<ScryfallModels.Card.Face> faces = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject fo = el.getAsJsonObject();
                ScryfallModels.Card.Face f = new ScryfallModels.Card.Face();
                f.name       = getStr(fo, "name");
                f.manaCost   = getStr(fo, "mana_cost");
                f.typeLine   = getStr(fo, "type_line");
                f.oracleText = getStr(fo, "oracle_text");
                f.power      = getStr(fo, "power");
                f.toughness  = getStr(fo, "toughness");
                f.loyalty    = getStr(fo, "loyalty");
                if (fo.has("image_uris")) {
                    f.imageUris = getStringMap(fo.getAsJsonObject("image_uris"));
                }
                faces.add(f);
            }
            c.faces = faces;
        }

        // crude token/art detection
        String layout = c.layout == null ? "" : c.layout.toLowerCase(Locale.ROOT);
        String typeLn = c.typeLine == null ? "" : c.typeLine.toLowerCase(Locale.ROOT);
        c.isTokenLike =
                layout.contains("art_series")
                        || layout.contains("token")
                        || layout.contains("emblem")
                        || layout.contains("planar")
                        || layout.contains("scheme")
                        || layout.contains("vanguard")
                        || hasTypeWord(typeLn, "token")
                        || hasTypeWord(typeLn, "emblem")
                        || hasTypeWord(typeLn, "dungeon")
                        || hasTypeWord(typeLn, "attraction")
                        || hasTypeWord(typeLn, "sticker")
                        || hasTypeWord(typeLn, "contraption")
                        || hasTypeWord(typeLn, "scheme")
                        || hasTypeWord(typeLn, "plane")
                        || hasTypeWord(typeLn, "phenomenon")
                        || hasTypeWord(typeLn, "vanguard");

        // prices block (some fields can be null)
        // prices block (Scryfall returns strings or null)
        // prices block (Scryfall returns strings or null)
        ScryfallModels.Card.Price p = new ScryfallModels.Card.Price();
        if (o.has("prices") && o.get("prices").isJsonObject()) {
            JsonObject prices = o.getAsJsonObject("prices");

            p.usd       = getPrice(prices, "usd");
            p.usdFoil   = getPrice(prices, "usd_foil");
            p.usdEtched = getPrice(prices, "usd_etched");
            p.eur       = getPrice(prices, "eur");
            p.eurFoil   = getPrice(prices, "eur_foil");
            p.tix       = getPrice(prices, "tix");
        }
        c.price = p;

        return c;
    }

    static ScryfallModels.Card parseFirstCardFromSearch(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new RuntimeException("Search JSON root is not an object");

        JsonObject o = root.getAsJsonObject();

        // If Scryfall returned an error object, surface it.
        if (o.has("object") && "error".equals(getStr(o, "object"))) {
            String details = getStr(o, "details");
            if (details.isBlank()) details = "Unknown Scryfall error";
            throw new RuntimeException("Scryfall search error: " + details);
        }

        if (!o.has("data") || !o.get("data").isJsonArray()) {
            throw new RuntimeException("Search JSON has no data[]");
        }

        JsonArray data = o.getAsJsonArray("data");
        if (data.isEmpty()) {
            throw new RuntimeException("Search returned 0 results");
        }

        JsonElement first = data.get(0);
        if (!first.isJsonObject()) {
            throw new RuntimeException("Search first result is not an object");
        }

        // Reuse your existing card parser by feeding it the card object JSON.
        return parseCard(first.toString());
    }

    private static String getPrice(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "—";
        String s = o.get(key).getAsString();
        if (s == null) return "—";
        s = s.trim();
        return s.isEmpty() ? "—" : s;
    }

    private static String getStr(JsonObject o, String key) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : "";
    }
    private static Map<String,String> getStringMap(JsonObject o) {
        if (o == null) return Map.of();
        Map<String,String> m = new HashMap<>();
        for (var e : o.entrySet()) {
            if (!e.getValue().isJsonNull()) m.put(e.getKey(), e.getValue().getAsString());
        }
        return m;
    }
    private static List<String> getStringList(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray(key)) out.add(el.getAsString());
        return out;
    }

    private static boolean hasTypeWord(String typeLine, String word) {
        if (typeLine == null || typeLine.isBlank()) return false;
        for (String token : typeLine.split("[^a-z]+")) {
            if (token.equals(word)) return true;
        }
        return false;
    }

    static String toJson(ScryfallModels.Card c) {
        // Minimal serialization matching parseCard expectations
        JsonObject o = new JsonObject();
        put(o,"id",c.id);
        put(o,"lang",c.lang);
        put(o,"oracle_id",c.oracleId);
        put(o,"name",c.name);
        put(o,"set",c.set);
        put(o,"collector_number",c.collectorNumber);
        put(o,"rarity",c.rarity);
        put(o,"layout",c.layout);
        put(o,"mana_cost",c.manaCost);
        put(o,"type_line",c.typeLine);
        put(o,"oracle_text",c.oracleText);
        put(o,"power",c.power);
        put(o,"toughness",c.toughness);
        put(o,"loyalty",c.loyalty);
        o.addProperty("cmc", c.manaValue);
        o.add("colors", toArray(c.colors));
        o.add("color_identity", toArray(c.colorIdentity));
        o.add("legalities", toObj(c.legalities));
        o.add("image_uris", toObj(c.imageUris));
        if (c.faces != null && !c.faces.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (var f : c.faces) {
                JsonObject fo = new JsonObject();
                put(fo,"name",f.name);
                put(fo,"mana_cost",f.manaCost);
                put(fo,"type_line",f.typeLine);
                put(fo,"oracle_text",f.oracleText);
                put(fo,"power",f.power);
                put(fo,"toughness",f.toughness);
                put(fo,"loyalty",f.loyalty);
                fo.add("image_uris", toObj(f.imageUris));
                arr.add(fo);
            }
            o.add("card_faces", arr);
        }
        put(o,"scryfall_uri",c.scryfallUri);
        JsonObject prices = new JsonObject();
        prices.addProperty("usd", c.price == null ? null : c.price.usd);
        prices.addProperty("usd_foil", c.price == null ? null : c.price.usdFoil);
        prices.addProperty("usd_etched", (String)null);
        o.add("prices", prices);
        return o.toString();
    }
    private static void put(JsonObject o, String k, String v){ if (v!=null && !v.isEmpty()) o.addProperty(k,v); }
    private static JsonArray toArray(List<String> list){
        JsonArray a = new JsonArray(); if (list!=null) for (var s:list) a.add(s); return a;
    }
    private static JsonObject toObj(Map<String,String> map){
        JsonObject o = new JsonObject(); if (map!=null) for (var e:map.entrySet()) if (e.getValue()!=null) o.addProperty(e.getKey(), e.getValue()); return o;
    }

    static ScryfallCardSearchFetch.Page parseSearchPage(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return new ScryfallCardSearchFetch.Page(List.of(), 0, false);

        JsonObject o = root.getAsJsonObject();
        int total = o.has("total_cards") ? o.get("total_cards").getAsInt() : 0;
        boolean hasMore = o.has("has_more") && o.get("has_more").getAsBoolean();

        List<ScryfallCardSearchFetch.Hit> hits = new ArrayList<>();
        if (o.has("data") && o.get("data").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("data")) {
                if (!el.isJsonObject()) continue;
                JsonObject c = el.getAsJsonObject();

                String name = getStr(c, "name");
                String set  = getStr(c, "set");
                String cn   = getStr(c, "collector_number");

                if (!name.isBlank() && !set.isBlank() && !cn.isBlank()) {
                    hits.add(new ScryfallCardSearchFetch.Hit(set, cn, name));
                }
            }
        }
        return new ScryfallCardSearchFetch.Page(hits, total, hasMore);
    }

    static List<ScryfallModels.Card> parseCollection(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return List.of();

        JsonObject o = root.getAsJsonObject();

        // If Scryfall returned an error object, surface it.
        if (o.has("object") && "error".equals(getStr(o, "object"))) {
            String details = getStr(o, "details");
            if (details.isBlank()) details = "Unknown Scryfall error";
            throw new RuntimeException("Scryfall collection error: " + details);
        }

        if (!o.has("data") || !o.get("data").isJsonArray()) return List.of();

        List<ScryfallModels.Card> out = new ArrayList<>();
        for (JsonElement el : o.getAsJsonArray("data")) {
            if (!el.isJsonObject()) continue;
            out.add(parseCard(el.toString()));
        }
        return out;
    }


    private ScryfallJson() {}
}
