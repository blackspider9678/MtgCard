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
        return gameRoot(server, GAME_MTG);
    }

    public static Path gameRoot(MinecraftServer server, String game) {
        return worldRoot(server).resolve("mtgcard").resolve(sanitizeGameFolder(game));
    }

    public static Path mainArtDir(MinecraftServer server) {
        return mainArtDir(server, GAME_MTG);
    }

    public static Path mainArtDir(MinecraftServer server, String game) {
        return gameRoot(server, game).resolve("main_art");
    }

    public static Path customArtRoot(MinecraftServer server) {
        return customArtRoot(server, GAME_MTG);
    }

    public static Path customArtRoot(MinecraftServer server, String game) {
        return gameRoot(server, game).resolve("custom_art");
    }

    public static Path customArtDir(MinecraftServer server, String setCode) {
        return customArtDir(server, GAME_MTG, setCode);
    }

    public static Path customArtDir(MinecraftServer server, String game, String setCode) {
        return customArtRoot(server, game).resolve(sanitizeSetFolder(setCode));
    }

    public static Path artIndexJson(MinecraftServer server) {
        return artIndexJson(server, GAME_MTG);
    }

    public static Path artIndexJson(MinecraftServer server, String game) {
        return gameRoot(server, game).resolve("art_index.json");
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

    public static String sanitizeGameFolder(String game) {
        String value = game == null ? "" : game.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) value = GAME_MTG;
        value = value.replaceAll("[^a-z0-9_\\-]", "_");
        value = value.replaceAll("_+", "_");
        while (value.startsWith("_")) value = value.substring(1);
        while (value.endsWith("_")) value = value.substring(0, value.length() - 1);
        if (value.isBlank()) value = GAME_MTG;
        if (value.length() > 48) value = value.substring(0, 48);
        return value;
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private MtgCardPaths() {
    }
}
