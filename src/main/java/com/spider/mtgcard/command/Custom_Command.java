package com.spider.mtgcard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.config.ImportPerms;
import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.net.CustomCardPackets;
import com.spider.mtgcard.net.CustomCardSync;
import com.spider.mtgcard.net.CustomImportPackets;
import com.spider.mtgcard.net.WorldState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class Custom_Command {

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("custom")
                .then(literal("import")
                        .requires(src -> {
                            var player = src.getPlayer();
                            return player != null && ImportPerms.canImport(player);
                        })
                        .executes(ctx -> openImport(ctx.getSource())))
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
        ServerPlayer player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            player = null;
        }

        if (player == null) {
            src.sendFailure(Component.literal("No player context."));
            return 0;
        }
        if (!ImportPerms.canImport(player) || !CustomImportPackets.openImportGui(player)) {
            src.sendFailure(Component.literal("You do not have permission to import custom cards."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("Opening Custom Import."), false);
        return 1;
    }

    private static int listSets(CommandSourceStack src) {
        Set<String> sets = store(src).all().stream()
                .map(meta -> meta == null ? "" : safe(meta.set).trim())
                .filter(set -> !set.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        src.sendSuccess(() -> Component.literal("Custom sets found: " + sets.size()), false);
        if (sets.isEmpty()) {
            src.sendSuccess(() -> Component.literal("- (none)"), false);
            return 1;
        }

        for (String set : sets) {
            src.sendSuccess(() -> Component.literal("- " + set), false);
        }
        return 1;
    }

    private static int showCard(CommandSourceStack src, String rawName) {
        NameQuery query = parseNameQuery(rawName);
        List<CustomCardStore.CardMeta> matches = store(src).findByName(query.name(), query.setFilter());

        if (matches.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No custom card found named: " + rawName), false);
            return 0;
        }

        if (matches.size() > 1) {
            src.sendSuccess(() -> Component.literal("Multiple custom cards named " + query.name() + " found:"), false);
            for (CustomCardStore.CardMeta meta : matches) {
                src.sendSuccess(() -> Component.literal("- " + safe(meta.name) + " (SET=" + safe(meta.set) + ", ID=" + safe(meta.id) + ")"), false);
            }
            src.sendSuccess(() -> Component.literal("Tip: use \"Name [SET]\" to select a set."), false);
            return 1;
        }

        CustomCardStore.CardMeta card = matches.get(0);
        src.sendSuccess(() -> Component.literal("Custom Card Details:"), false);
        src.sendSuccess(() -> Component.literal("Name: " + safe(card.name)), false);
        src.sendSuccess(() -> Component.literal("ID: " + safe(card.id)), false);
        src.sendSuccess(() -> Component.literal("Set: " + safe(card.set)), false);
        src.sendSuccess(() -> Component.literal("Rarity: " + safe(card.rarity)), false);
        src.sendSuccess(() -> Component.literal("Mana: " + safe(card.manaCost)), false);
        src.sendSuccess(() -> Component.literal("Type: " + safe(card.typeLine)), false);
        src.sendSuccess(() -> Component.literal("Text: " + safe(card.oracleText)), false);
        if (card.doubleFaced) {
            src.sendSuccess(() -> Component.literal("Back face: " + safe(card.backName)), false);
        }
        return 1;
    }

    private static int removeSet(CommandSourceStack src, String set) {
        String needle = safe(set).trim().toLowerCase(Locale.ROOT);
        List<String> ids = store(src).all().stream()
                .filter(meta -> meta != null && meta.id != null && !meta.id.isBlank())
                .filter(meta -> safe(meta.set).trim().toLowerCase(Locale.ROOT).equals(needle))
                .map(meta -> meta.id)
                .toList();

        if (ids.isEmpty()) {
            src.sendSuccess(() -> Component.literal("Set not found: " + set), false);
            return 0;
        }

        return reportRemoval(src, "set " + set, store(src).removeByIds(ids, false));
    }

    private static int removeCard(CommandSourceStack src, String rawName) {
        NameQuery query = parseNameQuery(rawName);
        List<CustomCardStore.CardMeta> matches = store(src).findByName(query.name(), query.setFilter());

        if (matches.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No custom card found named: " + rawName), false);
            return 0;
        }

        Set<String> sets = matches.stream()
                .map(meta -> safe(meta.set).trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));

        if (matches.size() > 1 && query.setFilter() == null && sets.size() > 1) {
            src.sendSuccess(() -> Component.literal("Multiple matches for " + query.name() + ". Nothing removed."), false);
            for (CustomCardStore.CardMeta meta : matches) {
                src.sendSuccess(() -> Component.literal("- " + safe(meta.name) + " (SET=" + safe(meta.set) + ", ID=" + safe(meta.id) + ")"), false);
            }
            src.sendSuccess(() -> Component.literal("Tip: use \"Name [SET]\" to select a set."), false);
            return 0;
        }

        List<String> ids = matches.stream()
                .map(meta -> meta.id)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        return reportRemoval(src, "card " + query.name(), store(src).removeByIds(ids, false));
    }

    private static int reportRemoval(CommandSourceStack src, String label, CustomCardStore.RemoveResult result) {
        if (result == null || result.removed().isEmpty()) {
            src.sendSuccess(() -> Component.literal("Nothing removed."), false);
            return 0;
        }

        if (!result.saved()) {
            src.sendFailure(Component.literal("Card metadata was removed in memory, but saving custom_cards.json failed. Check server logs."));
            return 0;
        }

        CustomCardStore currentStore = store(src);
        CustomCardSync.broadcastFull(src.getServer(), currentStore.all());
        broadcastArtInvalidation(src, result.invalidatedArtKeys());

        src.sendSuccess(() -> Component.literal("Removed custom " + label + " from active card pool."), false);
        src.sendSuccess(() -> Component.literal("Card metadata removed: " + result.removed().size()), false);
        src.sendSuccess(() -> Component.literal("Associated art kept: " + result.artKeysKept() + " key(s)."), false);
        if (result.artFilesDeleted() > 0 || result.artKeysRemoved() > 0) {
            src.sendSuccess(() -> Component.literal("Associated art removed: " + result.artFilesDeleted()
                    + " file(s) from " + result.artKeysRemoved() + " key(s)."), false);
        }
        src.sendSuccess(() -> Component.literal("Cached texture invalidated: " + result.invalidatedArtKeys().size()
                + " key(s); kept art remains available for existing cards."), false);
        src.sendSuccess(() -> Component.literal("Card database synced/reloaded."), false);
        return 1;
    }

    private static void broadcastArtInvalidation(CommandSourceStack src, List<String> artKeys) {
        if (artKeys == null || artKeys.isEmpty()) return;

        CustomCardPackets.CustomArtInvalidate payload = new CustomCardPackets.CustomArtInvalidate(List.copyOf(artKeys));
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static CustomCardStore store(CommandSourceStack src) {
        return WorldState.get(src.getServer()).customCards();
    }

    private static NameQuery parseNameQuery(String rawName) {
        String name = safe(rawName).trim();
        String setFilter = null;

        int lb = name.lastIndexOf('[');
        int rb = name.lastIndexOf(']');
        if (lb >= 0 && rb > lb) {
            setFilter = name.substring(lb + 1, rb).trim();
            if (setFilter.isBlank()) setFilter = null;
            name = name.substring(0, lb).trim();
        }

        return new NameQuery(name, setFilter);
    }

    private static boolean isOp(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            return false;
        }
        return player != null && com.spider.mtgcard.config.Perms.isOp(player);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record NameQuery(String name, String setFilter) {}

    private Custom_Command() {}
}
