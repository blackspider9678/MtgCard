package com.spider.mtgcard.client.command;

import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DeckClientIO {

    private static final String MTG_CARD_ID = "mtgcard:card";
    private static final String SEC = "\u00A7";

    public static void handleExport(String rawName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String name = sanitizeFilename(rawName);
        Path dir = decksDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}

        ItemStack hand = mc.player.getMainHandItem();
        if (hand.isEmpty() || !(hand.getItem() instanceof DeckboxBlockItem)) {
            mc.player.displayClientMessage(Component.literal(SEC + "cHold a Deckbox in your main hand to export."), false);
            return;
        }

        List<ExportRow> rows = readDeckboxExportRows(hand);
        if (rows.isEmpty()) {
            mc.player.displayClientMessage(Component.literal(SEC + "eDeckbox has no MTG cards to export."), false);
            return;
        }

        Path txt = uniquePath(dir.resolve(name + ".txt"));
        Path csv = uniquePath(dir.resolve(name + ".csv"));

        StringBuilder txtOut = new StringBuilder();
        for (ExportRow r : rows) {
            if (!r.name().isEmpty()) txtOut.append(r.count()).append(' ').append(r.name()).append('\n');
        }

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
            mc.player.displayClientMessage(Component.literal(SEC + "cExport failed: " + SEC + "7" + e.getMessage()), false);
            return;
        }

        mc.player.displayClientMessage(Component.literal(SEC + "aExported deck:"), false);
        mc.player.displayClientMessage(
                Component.literal(SEC + "7- ").append(openFileLink(SEC + "f" + txt.getFileName(), txt)), false);
        mc.player.displayClientMessage(
                Component.literal(SEC + "7- ").append(openFileLink(SEC + "f" + csv.getFileName(), csv)), false);
        mc.player.displayClientMessage(
                Component.literal(SEC + "7Folder: ").append(openFileLink(SEC + "eopen decks folder", dir)), false);
        mc.player.displayClientMessage(Component.literal(SEC + "7Saved to: " + SEC + "e" + dir.toAbsolutePath()), false);
    }

    public static void handleList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Path dir = decksDir();
        if (!Files.exists(dir)) {
            mc.player.displayClientMessage(Component.literal(SEC + "aDecks (Found " + SEC + "e0" + SEC + "a)."), false);
            mc.player.displayClientMessage(Component.literal(SEC + "7Folder: " + SEC + "e" + dir.toAbsolutePath()), false);
            return;
        }

        List<String> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(fn -> {
                        String low = fn.toLowerCase(Locale.ROOT);
                        return low.endsWith(".txt") || low.endsWith(".csv");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            mc.player.displayClientMessage(Component.literal(SEC + "cFailed to list decks: " + SEC + "7" + e.getMessage()), false);
            return;
        }

        mc.player.displayClientMessage(Component.literal(SEC + "aDecks (Found " + SEC + "e" + files.size() + SEC + "a):"), false);
        if (files.isEmpty()) {
            mc.player.displayClientMessage(Component.literal(SEC + "7- (none)"), false);
        } else {
            for (String fn : files) {
                mc.player.displayClientMessage(Component.literal(SEC + "7- " + SEC + "f" + fn), false);
            }
        }
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
        CustomData comp = deckbox.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return List.of();

        CompoundTag root = comp.copyTag();
        if (root == null) return List.of();

        var beTagOpt = root.getCompound("BlockEntityTag");
        if (beTagOpt.isEmpty()) return List.of();

        var itemsOpt = beTagOpt.get().getList("Items");
        if (itemsOpt.isEmpty()) return List.of();

        ListTag items = itemsOpt.get();
        Map<String, MutableAgg> agg = new LinkedHashMap<>();

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof CompoundTag entry)) continue;

            var stackOpt = entry.getCompound("Stack");
            if (stackOpt.isEmpty()) continue;

            CompoundTag st = stackOpt.get();
            String itemId = st.getString("id").orElse("");
            if (!MTG_CARD_ID.equals(itemId)) continue;

            int count = st.getInt("count").orElse(1);

            var compsOpt = st.getCompound("components");
            if (compsOpt.isEmpty()) continue;
            CompoundTag comps = compsOpt.get();

            var customDataOpt = comps.getCompound("minecraft:custom_data");
            if (customDataOpt.isEmpty()) continue;
            CompoundTag customData = customDataOpt.get();

            var metaOpt = customData.getCompound("mtg_meta");
            if (metaOpt.isEmpty()) continue;
            CompoundTag meta = metaOpt.get();

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
        String name;
        String set;
        String collectorNumber;
        String id;
        String rarity;
        String manaCost;
        String source;
        int count;
    }

    private static Path decksDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("mtgcard").resolve("decks");
    }

    private static String sanitizeFilename(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1).trim();
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
        String ext = dot >= 0 ? file.substring(dot) : "";
        Path dir = path.getParent();
        for (int i = 2; i < 10_000; i++) {
            Path p = dir.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
        return dir.resolve(base + "_" + System.currentTimeMillis() + ext);
    }

    private static String pickNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return a;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean q = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String out = s.replace("\"", "\"\"");
        return q ? ("\"" + out + "\"") : out;
    }

    private static MutableComponent openFileLink(String label, Path path) {
        String abs = path.toAbsolutePath().toString();

        return Component.literal(label)
                .setStyle(Style.EMPTY
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenFile(abs))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(abs))));
    }

    private DeckClientIO() {}
}
