// com.spider.mtgcard.content.pack.custom.CustomCardStore.java
package com.spider.mtgcard.content.pack.custom;

import com.spider.mtgcard.net.CustomCardPackets;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;

public final class CustomCardStore {
    private final Path artDir;
    private final Path json;
    private final Map<String, CardMeta> byId = new HashMap<>();
    public String artKeyFront = "";
    public String artKeyBack  = "";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();


    public CustomCardStore(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT).resolve("mtgcard");
        this.artDir = root.resolve("art");
        this.json   = root.resolve("custom").resolve("cards.json");
        try { Files.createDirectories(artDir); Files.createDirectories(json.getParent()); } catch (Exception ignored) {}
        load();
    }

    public synchronized int addAllFromClient(List<CustomCardPackets.BatchEntry> entries, ServerPlayerEntity who) {
        int added = 0;

        for (var be : entries) {
            if (be == null) continue;

            String id = sanitizeClientId(be.id());
            if (id == null) continue;

            CardMeta meta = CardMeta.fromBatch(id, be);
            byId.put(id, meta);
            added++;
        }

        // Save once for the whole batch
        save();
        return added;
    }

    private boolean dirty = false;

    public synchronized String addFromClient(CustomCardPackets.BatchEntry be, ServerPlayerEntity who) {
        try {
            String id = sanitizeClientId(be.id());
            if (id == null) return null;

            CardMeta meta = CardMeta.fromBatch(id, be);
            byId.put(id, meta);
            dirty = true;               // ✅ mark dirty, no save here
            return id;
        } catch (Exception e) {
            System.out.println("[MTGCard] addFromClient failed: " + e);
            return null;
        }
    }

    public synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        save();
    }

    private static String sanitizeClientId(String id) {
        if (id == null) return null;
        id = id.trim();
        if (id.isEmpty()) return null;
        // allow only safe chars for filenames/nbt/json; length cap
        if (id.length() > 64) id = id.substring(0, 64);
        if (!id.matches("[a-zA-Z0-9_\\-]+")) return null;
        return id;
    }


    public synchronized Collection<CardMeta> all() { return List.copyOf(byId.values()); }

    // Replace your load() and save() with these:

    @SuppressWarnings("unchecked")
    private void load() {
        byId.clear();
        try {
            if (Files.exists(json)) {
                String s = Files.readString(json, StandardCharsets.UTF_8);
                CardMeta[] arr = GSON.fromJson(s, CardMeta[].class);
                if (arr != null) {
                    for (CardMeta m : arr) {
                        if (m != null && m.id != null && !m.id.isBlank()) {
                            byId.put(m.id, m);
                        }
                    }
                }
            } else {
                // ensure parent dirs exist; file will be created on first save()
                Files.createDirectories(json.getParent());
            }
        } catch (Exception e) {
            // Optional: log once so you know why it failed
            System.out.println("[MTGCard] Failed to load " + json + ": " + e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(json.getParent());
            var list = new ArrayList<>(byId.values());
            String s = GSON.toJson(list);
            Files.writeString(json, s, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            System.out.println("[MTGCard] Failed to save " + json + ": " + e);
        }
    }

    /** Server-side DTO (no networking dependency). */


    private static BufferedImage resize(BufferedImage src, int max) {
        double s = Math.min(1.0, (double)max / Math.max(src.getWidth(), src.getHeight()));
        if (s >= 1.0) return src;
        int w = (int)Math.round(src.getWidth() * s), h = (int)Math.round(src.getHeight() * s);
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.drawImage(src, 0, 0, w, h, null); g.dispose();
        return img;
    }

    private boolean artExists(String artKey) {
        if (artKey == null || artKey.isBlank()) return false;
        try (var s = Files.list(artDir)) {
            final String prefix = artKey + ".";
            return s.anyMatch(p -> p.getFileName().toString().startsWith(prefix));
        } catch (Exception ignored) {
            return false;
        }
    }

    // Minimal meta POJO you can extend later
    public static final class CardMeta {
        public String id, name, manaCost, typeLine, rarity, set, oracleText, power, toughness, loyalty;
        public boolean doubleFaced;
        public String backName, backTypeLine, backOracleText, backPower, backToughness, backLoyalty;
        public String artKeyFront = "";
        public String artKeyBack  = "";

        static CardMeta fromBatch(String id, CustomCardPackets.BatchEntry e) {
            CardMeta m = new CardMeta();
            m.id = id;

            m.name = e.name(); m.manaCost = e.manaCost(); m.typeLine = e.typeLine(); m.rarity = e.rarity();
            m.set = e.set(); m.oracleText = e.oracleText(); m.power = e.power(); m.toughness = e.toughness(); m.loyalty = e.loyalty();

            m.doubleFaced = e.doubleFaced();
            m.backName = e.backName(); m.backTypeLine = e.backTypeLine(); m.backOracleText = e.backOracleText();
            m.backPower = e.backPower(); m.backToughness = e.backToughness(); m.backLoyalty = e.backLoyalty();

            // ✅ Persist art keys
            String canonicalFront = "custom_" + id + "_f0";
            String canonicalBack  = "custom_" + id + "_f1";

            String inFront = e.artKeyFront();
            String inBack  = e.artKeyBack();

            m.artKeyFront = (inFront == null || inFront.isBlank()) ? canonicalFront : inFront.trim();
            m.artKeyBack  = (inBack  == null || inBack.isBlank())  ? canonicalBack  : inBack.trim();

            // ✅ If client sent old-style "<id>_f0", upgrade it
            if (!m.artKeyFront.startsWith("custom_")) m.artKeyFront = canonicalFront;
            if (!m.artKeyBack.startsWith("custom_"))  m.artKeyBack  = canonicalBack;

            return m;
        }
    }
}
