package com.spider.mtgcard.content.pack.cache;

import com.google.gson.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class PersistentCardStore {
    private final Map<String, ScryfallModels.Card> byId = new HashMap<>();
    private final Path file;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PersistentCardStore(Path file) { this.file = file; }

    public static PersistentCardStore load(ServerWorld world) {
        Path dir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("mtgcard");
        Path file = dir.resolve("scryfall_cache.json");
        try {
            Files.createDirectories(dir);
            PersistentCardStore store = new PersistentCardStore(file);
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    for (var e : root.entrySet()) {
                        ScryfallModels.Card c = ScryfallJson.parseCard(e.getValue().toString());
                        if (c != null && c.id != null && !c.id.isEmpty()) store.byId.put(c.id, c);
                    }
                }
            }
            return store;
        } catch (IOException ex) {
            throw new RuntimeException("Failed loading persistent cache: " + file, ex);
        }
    }

    public synchronized ScryfallModels.Card get(String id) { return byId.get(id); }

    public synchronized void put(ScryfallModels.Card c) {
        if (c != null && c.id != null && !c.id.isEmpty()) byId.put(c.id, c);
    }

    public synchronized void save() {
        JsonObject root = new JsonObject();
        for (var e : byId.entrySet()) {
            // Re-serialize through GSON: we only need enough fields for re-hydration
            root.add(e.getKey(), JsonParser.parseString(ScryfallJson.toJson(e.getValue())));
        }
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, w);
        } catch (IOException ex) {
            throw new RuntimeException("Failed saving persistent cache: " + file, ex);
        }
    }
}
