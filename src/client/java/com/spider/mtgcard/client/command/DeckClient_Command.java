// src/client/java/com/spider/mtgcard/command/DeckClient_Command.java
package com.spider.mtgcard.client.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DeckClient_Command {

    private static final String MTG_CARD_ID = "mtgcard:card";

    private static int export(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, String rawName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;

        String name = sanitizeFilename(rawName);
        if (name.isEmpty()) {
            src.sendFeedback(Text.literal("§cInvalid deck name."));
            return 0;
        }

        Path dir = decksDir();
        try { Files.createDirectories(dir); } catch (IOException ignored) {}

        Path txt = uniquePath(dir.resolve(name + ".txt"));
        Path csv = uniquePath(dir.resolve(name + ".csv"));

        ItemStack hand = mc.player.getMainHandStack();
        if (hand.isEmpty()) {
            src.sendFeedback(Text.literal("§cHold a Deckbox to export."));
            return 0;
        }

        // If you prefer a stricter check, swap this to your deckbox item instance check.
        if (!(hand.getItem() instanceof com.spider.mtgcard.deckbox.DeckboxBlockItem)) {
            src.sendFeedback(Text.literal("§cHold a Deckbox to export."));
            return 0;
        }

        List<ExportRow> rows = readDeckboxExportRows(hand);
        if (rows.isEmpty()) {
            src.sendFeedback(Text.literal("§eDeckbox has no MTG cards to export."));
            return 0;
        }

        // TXT: "count name"
        StringBuilder txtOut = new StringBuilder();
        for (ExportRow r : rows) {
            if (r.name().isEmpty()) continue;
            txtOut.append(r.count()).append(' ').append(r.name()).append('\n');
        }

        // CSV: count,name,set,collector_number,id,rarity,mana_cost,source
        StringBuilder csvOut = new StringBuilder();
        csvOut.append("count,name,set,collector_number,id,rarity,mana_cost,source\n");
        for (ExportRow r : rows) {
            csvOut.append(r.count()).append(',')
                    .append(csvEscape(r.name())).append(',')
                    .append(csvEscape(r.set())).append(',')
                    .append(csvEscape(r.collectorNumber())).append(',')
                    .append(csvEscape(r.id())).append(',')
                    .append(csvEscape(r.rarity())).append(',')
                    .append(csvEscape(r.manaCost())).append(',')
                    .append(csvEscape(r.source()))
                    .append('\n');
        }

        try {
            Files.writeString(txt, txtOut.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(csv, csvOut.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            src.sendFeedback(Text.literal("§cFailed to export: §7" + e.getMessage()));
            return 0;
        }

        src.sendFeedback(Text.literal("§aExported deck:\n§7- §f" + txt.getFileName() + "\n§7- §f" + csv.getFileName()));
        src.sendFeedback(Text.literal("§7Saved to: §e" + dir.toAbsolutePath()));
        return 1;
    }

    private static int list(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        Path dir = decksDir();
        if (!Files.exists(dir)) {
            src.sendFeedback(Text.literal("§aDecks (Found §e0§a)."));
            src.sendFeedback(Text.literal("§7Folder will be created on first export: §e" + dir.toAbsolutePath()));
            return 1;
        }

        try (Stream<Path> s = Files.list(dir)) {
            var names = s
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(fn -> {
                        String low = fn.toLowerCase(Locale.ROOT);
                        return low.endsWith(".txt") || low.endsWith(".csv");
                    })
                    .sorted()
                    .toList();

            src.sendFeedback(Text.literal("§aDecks (Found §e" + names.size() + "§a):"));
            if (names.isEmpty()) {
                src.sendFeedback(Text.literal("§7- (none)"));
            } else {
                for (String fn : names) src.sendFeedback(Text.literal("§7- §f" + fn));
            }
            return 1;
        } catch (IOException e) {
            src.sendFeedback(Text.literal("§cFailed to list decks: §7" + e.getMessage()));
            return 0;
        }
    }

    private static Path decksDir() {
        // <minecraft>/config/mtgcard/decks
        return FabricLoader.getInstance().getConfigDir().resolve("mtgcard").resolve("decks");
    }

    private static String sanitizeFilename(String raw) {
        String s = raw == null ? "" : raw.trim();

        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        s = s.replace("\\", "").replace("/", "").replace("..", "");
        s = s.replaceAll("[\\\\/:*?\"<>|]", "");
        s = s.replaceAll("\\s+", " ").trim();

        if (s.isEmpty()) {
            s = "Deckbox_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        }

        return s;
    }

    private static Path uniquePath(Path path) {
        if (!Files.exists(path)) return path;

        String file = path.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot >= 0 ? file.substring(0, dot) : file;
        String ext  = dot >= 0 ? file.substring(dot) : "";

        Path dir = path.getParent();
        for (int i = 2; i < 10_000; i++) {
            Path p = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
        return dir.resolve(base + "_" + System.currentTimeMillis() + ext);
    }

    private record ExportRow(
            String key,
            String name,
            String set,
            String collectorNumber,
            String id,
            String rarity,
            String manaCost,
            String source,
            int count
    ) {}

    private static List<ExportRow> readDeckboxExportRows(ItemStack deckbox) {
        NbtComponent comp = deckbox.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return List.of();

        NbtCompound root = comp.copyNbt();
        if (root == null) return List.of();

        var beTagOpt = root.getCompound("BlockEntityTag");
        if (beTagOpt.isEmpty()) return List.of();

        var itemsOpt = beTagOpt.get().getList("Items");
        if (itemsOpt.isEmpty()) return List.of();

        NbtList items = itemsOpt.get();
        Map<String, MutableAgg> agg = new LinkedHashMap<>();

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof NbtCompound entry)) continue;

            var stackOpt = entry.getCompound("Stack");
            if (stackOpt.isEmpty()) continue;

            NbtCompound st = stackOpt.get();

            String itemId = st.getString("id").orElse("");
            if (!MTG_CARD_ID.equals(itemId)) continue;

            int count = st.getInt("count").orElse(1);

            var compsOpt = st.getCompound("components");
            if (compsOpt.isEmpty()) continue;
            NbtCompound comps = compsOpt.get();

            var customDataOpt = comps.getCompound("minecraft:custom_data");
            if (customDataOpt.isEmpty()) continue;
            NbtCompound customData = customDataOpt.get();

            var metaOpt = customData.getCompound("mtg_meta");
            if (metaOpt.isEmpty()) continue;
            NbtCompound meta = metaOpt.get();

            String name = meta.getString("name").orElse("");
            String set = meta.getString("set").orElse("");
            String collector = meta.getString("collector_number").orElse("");

            String rarity = meta.getString("rarity").orElse("");
            String manaCost = meta.getString("mana_cost").orElse("");

            boolean isCustom = meta.getByte("is_custom").orElse((byte) 0) != 0;

            String mtgUid = meta.getString("mtg_uid").orElse("");
            String customId = meta.getString("custom_id").orElse("");
            String idFallback = meta.getString("id").orElse("");

            String id = isCustom
                    ? (!customId.isEmpty() ? customId : idFallback)
                    : (!mtgUid.isEmpty() ? mtgUid : idFallback);

            String source = isCustom ? "custom" : "scryfall";
            String key = source + ":" + (!id.isEmpty() ? id : (name + "|" + set + "|" + collector));

            MutableAgg a = agg.computeIfAbsent(key, k -> new MutableAgg());
            a.name = pickNonEmpty(a.name, name);
            a.set = pickNonEmpty(a.set, set);
            a.collectorNumber = pickNonEmpty(a.collectorNumber, collector);
            a.id = pickNonEmpty(a.id, id);
            a.rarity = pickNonEmpty(a.rarity, rarity);
            a.manaCost = pickNonEmpty(a.manaCost, manaCost);
            a.source = pickNonEmpty(a.source, source);
            a.count += Math.max(1, count);
        }

        return agg.entrySet().stream()
                .map(e -> {
                    MutableAgg a = e.getValue();
                    return new ExportRow(
                            e.getKey(),
                            nullToEmpty(a.name),
                            nullToEmpty(a.set),
                            nullToEmpty(a.collectorNumber),
                            nullToEmpty(a.id),
                            nullToEmpty(a.rarity),
                            nullToEmpty(a.manaCost),
                            nullToEmpty(a.source),
                            a.count
                    );
                })
                .sorted(Comparator.comparing(ExportRow::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private static final class MutableAgg {
        String name, set, collectorNumber, id, rarity, manaCost, source;
        int count = 0;
    }

    private static String pickNonEmpty(String existing, String incoming) {
        if (existing != null && !existing.isEmpty()) return existing;
        if (incoming != null && !incoming.isEmpty()) return incoming;
        return existing;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String out = s.replace("\"", "\"\"");
        return needsQuotes ? ("\"" + out + "\"") : out;
    }

    private DeckClient_Command() {}
}
