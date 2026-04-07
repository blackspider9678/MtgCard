// com/spider/mtgcard/command/Custom_Command.java
package com.spider.mtgcard.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spider.mtgcard.net.CustomImportPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class Custom_Command {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<CustomCardRow>>(){}.getType();

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("custom")
                .then(literal("import").executes(ctx -> openImport(ctx.getSource())))
                .then(literal("sets").executes(ctx -> listSets(ctx.getSource())))
                .then(literal("card")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> showCard(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("remove")
                        .then(literal("set")
                                .requires(Custom_Command::isOp)
                                .then(argument("set", StringArgumentType.word())
                                        .executes(ctx -> removeSet(ctx.getSource(), StringArgumentType.getString(ctx, "set")))))
                        .then(literal("card")
                                .requires(Custom_Command::isOp)
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> removeCard(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                );
    }

    private static int openImport(CommandSourceStack src) {
        ServerPlayer p;
        try { p = src.getPlayer(); } catch (Exception e) { p = null; }
        if (p == null) {
            src.sendSuccess(() -> Component.literal("§cNo player context."), false);
            return 0;
        }
        ServerPlayNetworking.send(p, new CustomImportPackets.OpenImportGui());
        src.sendSuccess(() -> Component.literal("§aOpening Custom Import…"), false);
        return 1;
    }

    private static int listSets(CommandSourceStack src) {
        List<CustomCardRow> rows = load(src);
        if (rows == null) return 0;

        Set<String> sets = rows.stream()
                .map(r -> r.set == null ? "" : r.set.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        src.sendSuccess(() -> Component.literal("§aCustom sets (Found §e" + sets.size() + "§a):"), false);
        if (sets.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7- (none)"), false);
            return 1;
        }
        for (String s : sets) {
            src.sendSuccess(() -> Component.literal("§7- §f" + s), false);
        }
        return 1;
    }

    private static int showCard(CommandSourceStack src, String rawName) {
        List<CustomCardRow> rows = load(src);
        if (rows == null) return 0;

        String name = rawName.trim();
        String setFilter = null;

        // Optional bracket filter: "Card Name [SET]"
        int lb = name.lastIndexOf('[');
        int rb = name.lastIndexOf(']');
        if (lb >= 0 && rb > lb) {
            setFilter = name.substring(lb + 1, rb).trim();
            name = name.substring(0, lb).trim();
        }

        String nameLower = name.toLowerCase(Locale.ROOT);
        String setLower = setFilter == null ? null : setFilter.toLowerCase(Locale.ROOT);

        List<CustomCardRow> matches = rows.stream()
                .filter(r -> r.name != null && r.name.trim().toLowerCase(Locale.ROOT).equals(nameLower))
                .filter(r -> setLower == null || (r.set != null && r.set.trim().toLowerCase(Locale.ROOT).equals(setLower)))
                .toList();

        if (matches.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cNo custom card found named: §e" + rawName), false);
            return 0;
        }

        if (matches.size() > 1) {
            final String displayName = name; // <- capture final for lambdas
            src.sendSuccess(() -> Component.literal("§eMultiple custom cards named §f" + displayName + "§e found:"), false);

            for (CustomCardRow r : matches) {
                src.sendSuccess(() -> Component.literal("§7- §f" + r.name + " §7(SET=" + safe(r.set) + ")"), false);
            }

            final String firstSet = safe(matches.get(0).set);
            src.sendSuccess(() -> Component.literal(
                    "§7Tip: use §e\"Name [SET]\"§7 e.g. §e/mtg custom card \"" + displayName + " [" + firstSet + "]\""
            ), false);
            return 1;
        }

        CustomCardRow c = matches.get(0);

        // Minimal details (expand as your schema supports)
        src.sendSuccess(() -> Component.literal("§aCustom Card Details:"), false);
        src.sendSuccess(() -> Component.literal("§7Name: §f" + safe(c.name)), false);
        src.sendSuccess(() -> Component.literal("§7Set: §f" + safe(c.set)), false);
        if (c.rarity != null) src.sendSuccess(() -> Component.literal("§7Rarity: §f" + c.rarity), false);
        if (c.manaCost != null) src.sendSuccess(() -> Component.literal("§7Mana: §f" + c.manaCost), false);
        if (c.typeLine != null) src.sendSuccess(() -> Component.literal("§7Type: §f" + c.typeLine), false);
        if (c.oracleText != null) src.sendSuccess(() -> Component.literal("§7Text: §f" + c.oracleText), false);
        if (c.power != null || c.toughness != null) src.sendSuccess(() -> Component.literal("§7P/T: §f" + safe(c.power) + "/" + safe(c.toughness)), false);
        if (c.loyalty != null) src.sendSuccess(() -> Component.literal("§7Loyalty: §f" + c.loyalty), false);

        return 1;
    }

    private static int removeSet(CommandSourceStack src, String set) {
        List<CustomCardRow> rows = load(src);
        if (rows == null) return 0;

        String needle = set.trim().toLowerCase(Locale.ROOT);
        int before = rows.size();

        List<CustomCardRow> kept = rows.stream()
                .filter(r -> r.set == null || !r.set.trim().toLowerCase(Locale.ROOT).equals(needle))
                .toList();

        int removed = before - kept.size();
        if (removed == 0) {
            src.sendSuccess(() -> Component.literal("§cSet not found: §e" + set), false);
            return 0;
        }

        if (!saveWithBackup(src, kept)) return 0;
        src.sendSuccess(() -> Component.literal("§aRemoved set §e" + set + "§a (deleted §e" + removed + "§a card(s))."), false);
        return 1;
    }

    private static int removeCard(CommandSourceStack src, String rawName) {
        List<CustomCardRow> rows = load(src);
        if (rows == null) return 0;

        String name = rawName.trim();
        String setFilter = null;

        int lb = name.lastIndexOf('[');
        int rb = name.lastIndexOf(']');
        if (lb >= 0 && rb > lb) {
            setFilter = name.substring(lb + 1, rb).trim();
            name = name.substring(0, lb).trim();
        }

        String nameLower = name.toLowerCase(Locale.ROOT);
        String setLower = setFilter == null ? null : setFilter.toLowerCase(Locale.ROOT);

        List<CustomCardRow> matches = rows.stream()
                .filter(r -> r.name != null && r.name.trim().toLowerCase(Locale.ROOT).equals(nameLower))
                .filter(r -> setLower == null || (r.set != null && r.set.trim().toLowerCase(Locale.ROOT).equals(setLower)))
                .toList();

        if (matches.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cNo custom card found named: §e" + rawName), false);
            return 0;
        }

        if (matches.size() > 1) {
            final String displayName = name; // <- capture final for lambdas
            src.sendSuccess(() -> Component.literal("§cMultiple matches for §e" + displayName + "§c. Nothing removed."), false);

            for (CustomCardRow r : matches) {
                src.sendSuccess(() -> Component.literal("§7- §f" + r.name + " §7(SET=" + safe(r.set) + ")"), false);
            }

            final String firstSet = safe(matches.get(0).set);
            src.sendSuccess(() -> Component.literal(
                    "§7Tip: use §e\"Name [SET]\"§7 e.g. §e/mtg custom remove card \"" + displayName + " [" + firstSet + "]\""
            ), false);
            return 0;
        }

        CustomCardRow target = matches.get(0);

        List<CustomCardRow> kept = rows.stream()
                .filter(r -> r != target)
                .toList();

        if (!saveWithBackup(src, kept)) return 0;

        src.sendSuccess(() -> Component.literal("§aRemoved custom card: §f" + safe(target.name) + " §7(SET=" + safe(target.set) + ")"), false);
        return 1;
    }

    private static List<CustomCardRow> load(CommandSourceStack src) {
        Path path = cardsJson(src);
        if (!Files.exists(path)) {
            src.sendSuccess(() -> Component.literal("§7No custom cards file found: §e" + path), false);
            src.sendSuccess(() -> Component.literal("§7(Found 0 custom sets / cards)"), false);
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<CustomCardRow> rows = GSON.fromJson(json, LIST_TYPE);
            return rows != null ? new ArrayList<>(rows) : new ArrayList<>();
        } catch (Exception e) {
            src.sendSuccess(() -> Component.literal("§cFailed to read cards.json: §7" + e.getMessage()), false);
            return null;
        }
    }

    private static boolean saveWithBackup(CommandSourceStack src, List<CustomCardRow> rows) {
        Path path = cardsJson(src);
        try {
            Files.createDirectories(path.getParent());

            // backup
            if (Files.exists(path)) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path bak = path.getParent().resolve("cards.json.bak-" + ts);
                Files.copy(path, bak, StandardCopyOption.REPLACE_EXISTING);
            }

            // atomic write
            Path tmp = path.getParent().resolve("cards.json.tmp");
            Files.writeString(tmp, GSON.toJson(rows), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            return true;
        } catch (IOException e) {
            src.sendSuccess(() -> Component.literal("§cFailed to write cards.json: §7" + e.getMessage()), false);
            return false;
        }
    }

    private static Path cardsJson(CommandSourceStack src) {
        // <world>/mtgcard/custom/cards.json
        return src.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("mtgcard")
                .resolve("custom")
                .resolve("cards.json");
    }

    private static boolean isOp(CommandSourceStack src) {
        ServerPlayer p;
        try { p = src.getPlayer(); } catch (Exception e) { return false; }
        return p != null && com.spider.mtgcard.config.Perms.isOp(p);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * Minimal schema adapter. Add fields to match your real JSON as needed.
     */
    private static final class CustomCardRow {
        String name;
        String set;

        // Optional fields (match your importer fields if present in JSON)
        String rarity;
        String manaCost;
        String typeLine;
        String oracleText;
        String power;
        String toughness;
        String loyalty;
    }

    private Custom_Command() {}
}
