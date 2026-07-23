package com.spider.mtgcard.life;

import java.util.Locale;

public enum LifeFormat {
    COMMANDER("commander", "Commander", 40, true, false),
    STANDARD("standard", "Standard", 20, false, false),
    BRAWL("brawl", "Brawl", 25, false, false),
    TWO_HEADED("twoheaded", "Two Headed Giant", 30, false, true);

    public static final LifeFormat DEFAULT = COMMANDER;

    private final String key;
    private final String displayName;
    private final int startingLife;
    private final boolean commanderDamage;
    private final boolean sharedTeams;

    LifeFormat(String key, String displayName, int startingLife, boolean commanderDamage, boolean sharedTeams) {
        this.key = key;
        this.displayName = displayName;
        this.startingLife = startingLife;
        this.commanderDamage = commanderDamage;
        this.sharedTeams = sharedTeams;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int startingLife() {
        return startingLife;
    }

    public boolean hasCommanderDamage() {
        return commanderDamage;
    }

    public boolean hasSharedTeams() {
        return sharedTeams;
    }

    public static LifeFormat fromKey(String key) {
        String normalized = normalizeKey(key);
        for (LifeFormat format : values()) {
            if (format.key.equals(normalized)) return format;
        }
        return DEFAULT;
    }

    public static String normalizeKey(String key) {
        if (key == null) return DEFAULT.key;
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return DEFAULT.key;
        k = k.replace("-", "").replace("_", "").replace(" ", "");

        return switch (k) {
            case "edh", "cmd", "commander" -> COMMANDER.key;
            case "std", "standard" -> STANDARD.key;
            case "brawl" -> BRAWL.key;
            case "2hg", "thg", "twoheaded", "twoheadedgiant" -> TWO_HEADED.key;
            default -> DEFAULT.key;
        };
    }

    public static String displayName(String key) {
        return fromKey(key).displayName();
    }

    public static int startingLife(String key) {
        return fromKey(key).startingLife();
    }

    public static boolean hasCommanderDamage(String key) {
        return fromKey(key).hasCommanderDamage();
    }

    public static boolean hasSharedTeams(String key) {
        return fromKey(key).hasSharedTeams();
    }
}
