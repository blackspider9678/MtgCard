package com.spider.mtgcard.client.command;

import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.core.NonNullList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

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

    private static final String SEC = "\u00A7";
    private static final String LEGACY_BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String LEGACY_ITEMS_TAG = "Items";
    private static final String LEGACY_STACK_TAG = "Stack";

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
            if (!r.name().isEmpty()) txtOut.append(formatTxtRow(r)).append('\n');
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
        Map<String, MutableAgg> agg = new LinkedHashMap<>();

        for (ItemStack stack : readStoredDeckboxStacks(deckbox)) {
            collectStack(stack, agg);
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

    private static List<ItemStack> readStoredDeckboxStacks(ItemStack deckbox) {
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();

        ItemContainerContents contents = deckbox.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> items = NonNullList.withSize(DeckboxBlockEntity.INVENTORY_SIZE, ItemStack.EMPTY);
            contents.copyInto(items);
            for (ItemStack stack : items) {
                if (stack != null && !stack.isEmpty()) stacks.add(stack.copy());
            }
        }

        if (stacks.isEmpty()) {
            stacks.addAll(readLegacyStoredDeckboxStacks(deckbox));
        }

        return stacks;
    }

    private static List<ItemStack> readLegacyStoredDeckboxStacks(ItemStack deckbox) {
        CustomData comp = deckbox.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return List.of();

        CompoundTag root = comp.copyTag();
        if (root == null) return List.of();

        var beTagOpt = root.getCompound(LEGACY_BLOCK_ENTITY_TAG);
        if (beTagOpt.isEmpty()) return List.of();

        var itemsOpt = beTagOpt.get().getList(LEGACY_ITEMS_TAG);
        if (itemsOpt.isEmpty()) return List.of();

        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        ListTag items = itemsOpt.get();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof CompoundTag entry)) continue;

            ItemStack stack = readLegacyStoredStack(entry);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        return stacks;
    }

    private static ItemStack readLegacyStoredStack(CompoundTag entry) {
        var storedStack = entry.getCompound(LEGACY_STACK_TAG);
        if (storedStack.isPresent()) {
            return ItemStack.CODEC.parse(NbtOps.INSTANCE, storedStack.get()).result().orElse(ItemStack.EMPTY);
        }

        return ItemStack.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(ItemStack.EMPTY);
    }

    private static void collectStack(ItemStack stack, Map<String, MutableAgg> agg) {
        if (stack == null || stack.isEmpty()) return;

        if (stack.is(Items.BUNDLE)) {
            collectBundleContents(stack, agg);
            return;
        }

        if (!stack.is(ModItemTags.TCG_CARD)) return;

        TcgCardMeta.Info info = TcgCardMeta.read(stack);
        if (!info.isMtg()) return;

        String name = info.name().isBlank() ? stack.getHoverName().getString() : info.name();
        if (name == null || name.isBlank()) return;

        String set = info.set();
        String collector = info.collectorNumber();
        String rarity = info.rarity();
        String manaCost = info.manaCost();

        CompoundTag root = customData(stack);
        CompoundTag mtg = root.getCompound(TcgCardMeta.MTG_META).orElseGet(CompoundTag::new);
        String customId = mtg.getString("custom_id").orElse("");
        boolean isCustom = mtg.getBoolean("is_custom").orElse(false) || !customId.isBlank();
        String id = info.id();
        if (isCustom && !customId.isBlank()) id = customId;

        String source = isCustom ? "custom" : "scryfall";
        String key = source + ":" + (!id.isBlank() ? id : (name + "|" + set + "|" + collector));

        MutableAgg a = agg.computeIfAbsent(key, k -> new MutableAgg());
        a.name = pickNonEmpty(a.name, name);
        a.set = pickNonEmpty(a.set, set);
        a.collectorNumber = pickNonEmpty(a.collectorNumber, collector);
        a.id = pickNonEmpty(a.id, id);
        a.rarity = pickNonEmpty(a.rarity, rarity);
        a.manaCost = pickNonEmpty(a.manaCost, manaCost);
        a.source = pickNonEmpty(a.source, source);
        a.count += Math.max(1, stack.getCount());
    }

    private static void collectBundleContents(ItemStack bundle, Map<String, MutableAgg> agg) {
        BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        for (ItemStack bundled : contents.items()) {
            if (bundled != null && !bundled.isEmpty()) collectStack(bundled.copy(), agg);
        }
    }

    private static CompoundTag customData(ItemStack stack) {
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        return comp == null ? new CompoundTag() : comp.copyTag();
    }

    private static String formatTxtRow(ExportRow row) {
        StringBuilder out = new StringBuilder();
        out.append(row.count()).append(' ').append(row.name());
        if (!row.set().isBlank()) {
            out.append(" (").append(row.set()).append(')');
        }
        if (!row.collectorNumber().isBlank()) {
            out.append(' ').append(row.collectorNumber());
        }
        return out.toString();
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
