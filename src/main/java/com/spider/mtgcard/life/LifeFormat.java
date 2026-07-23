package com.spider.mtgcard.life;

import com.spider.mtgcard.api.LifeFormatRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LifeFormat {
    public static final String DEFAULT_KEY = "commander";
    public static final LifeFormat DEFAULT = new LifeFormat(
            TcgGameRegistry.MTG,
            DEFAULT_KEY,
            Component.literal("Commander"),
            40,
            true,
            1,
            10,
            List.of("edh", "cmd")
    );

    private final String game;
    private final String key;
    private final Component label;
    private final int startingLife;
    private final boolean commanderDamage;
    private final int turnGroupSize;
    private final int sortOrder;
    private final List<String> aliases;

    public LifeFormat(String key, String displayName, int startingLife, boolean commanderDamage, boolean sharedTeams) {
        this(
                TcgGameRegistry.MTG,
                key,
                Component.literal(displayName == null ? "" : displayName),
                startingLife,
                commanderDamage,
                sharedTeams ? 2 : 1,
                0,
                List.of()
        );
    }

    public LifeFormat(String game, String key, Component label, int startingLife, boolean commanderDamage,
                      int turnGroupSize, int sortOrder) {
        this(game, key, label, startingLife, commanderDamage, turnGroupSize, sortOrder, List.of());
    }

    public LifeFormat(String game, String key, Component label, int startingLife, boolean commanderDamage,
                      int turnGroupSize, int sortOrder, List<String> aliases) {
        this.game = sanitizeGame(game);
        this.key = sanitizeKey(key);
        this.label = label == null ? Component.literal(prettyName(this.key)) : label;
        this.startingLife = startingLife;
        this.commanderDamage = commanderDamage;
        this.turnGroupSize = Math.max(1, turnGroupSize);
        this.sortOrder = sortOrder;
        this.aliases = sanitizeAliases(aliases);
    }

    public String game() {
        return game;
    }

    public String key() {
        return key;
    }

    public Component label() {
        return label;
    }

    public String displayName() {
        String s = label.getString();
        return (s == null || s.isBlank()) ? prettyName(key) : s;
    }

    public int startingLife() {
        return startingLife;
    }

    public boolean hasCommanderDamage() {
        return commanderDamage;
    }

    public boolean hasSharedTeams() {
        return turnGroupSize > 1;
    }

    public int turnGroupSize() {
        return turnGroupSize;
    }

    public int sharedTeamSize() {
        return turnGroupSize;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public List<String> aliases() {
        return aliases;
    }

    public static LifeFormat fromKey(String key) {
        return LifeFormatRegistry.getOrDefault(key);
    }

    public static String normalizeKey(String key) {
        return LifeFormatRegistry.normalizeKey(key);
    }

    public static String displayName(String key) {
        return fromKey(key).displayName();
    }

    public static Component label(String key) {
        return fromKey(key).label();
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

    private static String sanitizeGame(String game) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        return normalized.isBlank() ? TcgGameRegistry.MTG : normalized;
    }

    public static String sanitizeKey(String key) {
        if (key == null) throw new IllegalArgumentException("Life format key must not be null");
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.isBlank()) throw new IllegalArgumentException("Life format key must not be blank");
        if (!k.matches("[a-z0-9_.:-]+")) {
            throw new IllegalArgumentException("Invalid life format key: " + key);
        }
        return k;
    }

    public static String prettyName(String key) {
        String normalized;
        try {
            normalized = sanitizeKey(key);
        } catch (IllegalArgumentException ignored) {
            normalized = DEFAULT_KEY;
        }

        String tail = normalized;
        int colon = tail.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < tail.length()) tail = tail.substring(colon + 1);

        String[] parts = tail.replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.isEmpty() ? normalized : out.toString();
    }

    private static List<String> sanitizeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) return List.of();
        ArrayList<String> out = new ArrayList<>();
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;
            String compact = compactAlias(alias);
            if (compact.isBlank() || Objects.equals(compact, DEFAULT_KEY)) continue;
            if (!out.contains(compact)) out.add(compact);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static String compactAlias(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }
}
