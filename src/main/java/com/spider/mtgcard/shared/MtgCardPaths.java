package com.spider.mtgcard.shared;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class MtgCardPaths {
    public static final String GAME_MTG = "mtg";
    private static final String DEFAULT_CUSTOM_SET = "cstm";

    public static Path gameRoot(MinecraftServer server) {
        return worldRoot(server).resolve("mtgcard").resolve(GAME_MTG);
    }

    public static Path mainArtDir(MinecraftServer server) {
        return gameRoot(server).resolve("main_art");
    }

    public static Path customArtRoot(MinecraftServer server) {
        return gameRoot(server).resolve("custom_art");
    }

    public static Path customArtDir(MinecraftServer server, String setCode) {
        return customArtRoot(server).resolve(sanitizeSetFolder(setCode));
    }

    public static Path artIndexJson(MinecraftServer server) {
        return gameRoot(server).resolve("art_index.json");
    }

    public static Path customCardsJson(MinecraftServer server) {
        return gameRoot(server).resolve("custom_cards.json");
    }

    public static Path legacyArtDir(MinecraftServer server) {
        return worldRoot(server).resolve("mtgcard").resolve("art");
    }

    public static Path legacyArtIndexJson(MinecraftServer server) {
        return worldRoot(server).resolve("mtgcard").resolve("art-index.json");
    }

    public static Path legacyCustomCardsJson(MinecraftServer server) {
        return worldRoot(server).resolve("mtgcard").resolve("custom").resolve("cards.json");
    }

    public static void ensureMtgDirs(MinecraftServer server) {
        try {
            Files.createDirectories(mainArtDir(server));
            Files.createDirectories(customArtRoot(server));
            Files.createDirectories(gameRoot(server));
        } catch (Exception ignored) {
        }
    }

    public static String sanitizeSetFolder(String setCode) {
        String set = setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT);
        if (set.isBlank()) set = DEFAULT_CUSTOM_SET;
        set = set.replaceAll("[^a-z0-9_\\-]", "_");
        set = set.replaceAll("_+", "_");
        while (set.startsWith("_")) set = set.substring(1);
        while (set.endsWith("_")) set = set.substring(0, set.length() - 1);
        if (set.isBlank()) set = DEFAULT_CUSTOM_SET;
        if (set.length() > 48) set = set.substring(0, 48);
        return set;
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private MtgCardPaths() {
    }
}
