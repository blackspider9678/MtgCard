package com.spider.mtgcard.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TcgCardMeta {
    public static final String TCG_META = "tcg_meta";
    public static final String TCG_FLAGS = "tcg_flags";
    public static final String MTG_META = "mtg_meta";

    public static record Info(
            String game,
            String id,
            String name,
            String set,
            String collectorNumber,
            String rarity,
            String manaCost,
            String typeLine,
            String oracleText,
            String power,
            String toughness,
            String loyalty,
            String layout,
            int manaValue,
            Set<String> colors,
            Set<String> colorIdentity,
            boolean foil,
            boolean tokenLike,
            boolean legendary,
            String commanderLegality,
            double priceUsd,
            int face
    ) {
        public boolean isMtg() {
            return "mtg".equalsIgnoreCase(game);
        }
    }

    public static Info read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return empty();

        CompoundTag root = StackData.readCustom(stack);
        CompoundTag tcg = root.getCompound(TCG_META).orElseGet(CompoundTag::new);
        CompoundTag mtg = root.getCompound(MTG_META).orElseGet(CompoundTag::new);
        boolean hasMtg = root.getCompound(MTG_META).isPresent();

        String game = firstString(tcg, null, "game");
        if (game.isBlank() && hasMtg) game = "mtg";

        String typeLine = firstString(tcg, mtg, "type_line");
        if (typeLine.isBlank()) typeLine = firstFaceString(tcg, mtg, "type_line");

        String oracleText = firstString(tcg, mtg, "oracle_text");
        if (oracleText.isBlank()) oracleText = firstFaceString(tcg, mtg, "oracle_text");

        String manaCost = firstString(tcg, mtg, "mana_cost");
        if (manaCost.isBlank()) manaCost = firstFaceString(tcg, mtg, "mana_cost");

        String power = firstString(tcg, mtg, "power");
        if (power.isBlank()) power = firstFaceString(tcg, mtg, "power");

        String toughness = firstString(tcg, mtg, "toughness");
        if (toughness.isBlank()) toughness = firstFaceString(tcg, mtg, "toughness");

        String loyalty = firstString(tcg, mtg, "loyalty");
        if (loyalty.isBlank()) loyalty = firstFaceString(tcg, mtg, "loyalty");

        int manaValue = firstInt(tcg, mtg, "mana_value", "mv", "cmc");
        boolean tokenLike = firstBoolean(tcg, mtg, "is_token_like", "token_like", "is_token", "token");
        boolean legendary = firstBoolean(tcg, mtg, "is_legendary", "legendary");

        return new Info(
                game,
                firstString(tcg, mtg, "id", "card_id", "scryfall_id", "custom_id"),
                firstString(tcg, mtg, "name"),
                firstString(tcg, mtg, "set", "set_code"),
                firstString(tcg, mtg, "collector_number", "number"),
                firstString(tcg, mtg, "rarity"),
                manaCost,
                typeLine,
                oracleText,
                power,
                toughness,
                loyalty,
                firstString(tcg, mtg, "layout"),
                manaValue,
                readStringSet(tcg, mtg, "colors"),
                readStringSet(tcg, mtg, "color_identity", "colorIdentity", "color_id"),
                readFoil(root),
                tokenLike,
                legendary,
                readLegality(tcg, mtg, "commander"),
                readPriceUsd(tcg, mtg),
                firstInt(tcg, mtg, "tcg_face", "face", "mtg_face")
        );
    }

    public static String displayName(ItemStack stack) {
        Info info = read(stack);
        return info.name().isBlank() ? stack.getHoverName().getString() : info.name();
    }

    public static int faceCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 1;
        CompoundTag root = StackData.readCustom(stack);
        CompoundTag tcg = root.getCompound(TCG_META).orElseGet(CompoundTag::new);
        CompoundTag mtg = root.getCompound(MTG_META).orElseGet(CompoundTag::new);

        int count = faceCount(tcg);
        if (count > 1) return count;
        return Math.max(1, faceCount(mtg));
    }

    public static void writeFace(CompoundTag root, int face) {
        if (root == null) return;
        int safeFace = Math.max(0, face);

        CompoundTag mtg = root.getCompound(MTG_META).orElse(null);
        if (mtg != null) {
            mtg.putInt("mtg_face", safeFace);
            root.put(MTG_META, mtg);
            // Native MTG cards use mtg_meta only. tcg_meta is reserved for
            // addon-owned generic cards such as Pokemon or Rift.
            root.remove(TCG_META);
            return;
        }

        CompoundTag tcg = root.getCompound(TCG_META).orElseGet(CompoundTag::new);
        tcg.putInt("tcg_face", safeFace);
        root.put(TCG_META, tcg);
    }

    public static void normalizeMtgMetadata(CompoundTag root) {
        if (root == null) return;

        CompoundTag flags = root.getCompound(TCG_FLAGS).orElseGet(CompoundTag::new);
        if (!flags.getBoolean("foil").isPresent()) {
            root.getBoolean("mtg_foil").ifPresent(v -> flags.putBoolean("foil", v));
        }

        root.remove(TCG_META);
        root.put(TCG_FLAGS, flags);
    }

    /** @deprecated Native MTG cards are no longer mirrored into tcg_meta. */
    @Deprecated(forRemoval = false)
    public static void mirrorMtgToTcg(CompoundTag root) {
        normalizeMtgMetadata(root);
    }

    private static int faceCount(CompoundTag meta) {
        if (meta == null) return 1;
        ListTag faces = meta.getList("faces").orElse(null);
        if (faces != null && faces.size() > 1) return faces.size();

        faces = meta.getList("card_faces").orElse(null);
        if (faces != null && faces.size() > 1) return faces.size();

        return 1;
    }

    private static Info empty() {
        return new Info("", "", "", "", "", "", "", "", "", "", "", "", "", 0,
                Set.of(), Set.of(), false, false, false, "", Double.NaN, 0);
    }

    private static String firstString(CompoundTag primary, CompoundTag fallback, String... keys) {
        String value = firstStringFrom(primary, keys);
        if (!value.isBlank()) return value;
        return firstStringFrom(fallback, keys);
    }

    private static String firstStringFrom(CompoundTag tag, String... keys) {
        if (tag == null || keys == null) return "";
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String value = tag.getString(key).orElse("");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static int firstInt(CompoundTag primary, CompoundTag fallback, String... keys) {
        Integer value = firstIntFrom(primary, keys);
        if (value != null) return value;
        value = firstIntFrom(fallback, keys);
        return value == null ? 0 : value;
    }

    private static Integer firstIntFrom(CompoundTag tag, String... keys) {
        if (tag == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            var value = tag.getInt(key);
            if (value.isPresent()) return value.get();
        }
        return null;
    }

    private static boolean firstBoolean(CompoundTag primary, CompoundTag fallback, String... keys) {
        Boolean value = firstBooleanFrom(primary, keys);
        if (value != null) return value;
        value = firstBooleanFrom(fallback, keys);
        return value != null && value;
    }

    private static Boolean firstBooleanFrom(CompoundTag tag, String... keys) {
        if (tag == null || keys == null) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            var value = tag.getBoolean(key);
            if (value.isPresent()) return value.get();
        }
        return null;
    }

    private static String firstFaceString(CompoundTag primary, CompoundTag fallback, String key) {
        String value = firstFaceStringFrom(primary, key);
        if (!value.isBlank()) return value;
        return firstFaceStringFrom(fallback, key);
    }

    private static String firstFaceStringFrom(CompoundTag meta, String key) {
        if (meta == null || key == null || key.isBlank()) return "";

        String value = firstFaceStringFromList(meta.getList("faces").orElse(null), key);
        if (!value.isBlank()) return value;

        return firstFaceStringFromList(meta.getList("card_faces").orElse(null), key);
    }

    private static String firstFaceStringFromList(ListTag faces, String key) {
        if (faces == null) return "";
        for (int i = 0; i < faces.size(); i++) {
            CompoundTag face = faces.getCompound(i).orElse(null);
            if (face == null) continue;
            String value = face.getString(key).orElse("");
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static Set<String> readStringSet(CompoundTag primary, CompoundTag fallback, String... keys) {
        Set<String> out = readStringSetFrom(primary, keys);
        if (!out.isEmpty()) return out;
        return readStringSetFrom(fallback, keys);
    }

    private static Set<String> readStringSetFrom(CompoundTag tag, String... keys) {
        Set<String> out = new HashSet<>();
        if (tag == null || keys == null) return out;
        for (String key : keys) {
            ListTag list = tag.getList(key).orElse(null);
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                String value = list.getString(i).orElse("").trim();
                if (!value.isEmpty()) out.add(value.toUpperCase(Locale.ROOT));
            }
            if (!out.isEmpty()) return Set.copyOf(out);
        }
        return Set.of();
    }

    private static boolean readFoil(CompoundTag root) {
        if (root == null) return false;
        boolean foil = root.getCompound(TCG_FLAGS)
                .flatMap(flags -> flags.getBoolean("foil"))
                .orElse(false);
        return foil
                || root.getBoolean("tcg_foil").orElse(false)
                || root.getBoolean("mtg_foil").orElse(false)
                || root.getCompound("mtg_flags")
                .flatMap(flags -> flags.getBoolean("mtg_foil"))
                .orElse(false);
    }

    private static String readLegality(CompoundTag primary, CompoundTag fallback, String format) {
        String value = readLegalityFrom(primary, format);
        if (!value.isBlank()) return value;
        return readLegalityFrom(fallback, format);
    }

    private static String readLegalityFrom(CompoundTag meta, String format) {
        if (meta == null || format == null || format.isBlank()) return "";
        return meta.getCompound("legalities")
                .flatMap(legalities -> legalities.getString(format))
                .orElse("");
    }

    private static double readPriceUsd(CompoundTag primary, CompoundTag fallback) {
        double value = readPriceUsdFrom(primary);
        if (!Double.isNaN(value)) return value;
        return readPriceUsdFrom(fallback);
    }

    private static double readPriceUsdFrom(CompoundTag meta) {
        if (meta == null) return Double.NaN;
        double direct = readDouble(meta, "usd", Double.NaN);
        if (Double.isNaN(direct)) direct = readDouble(meta, "price_usd", Double.NaN);
        if (Double.isNaN(direct)) direct = readDouble(meta, "price", Double.NaN);
        if (!Double.isNaN(direct)) return direct;

        for (String pricesKey : new String[]{"prices", "price"}) {
            CompoundTag prices = meta.getCompound(pricesKey).orElse(null);
            if (prices == null) continue;
            double nested = readDouble(prices, "usd", Double.NaN);
            if (Double.isNaN(nested)) nested = readDouble(prices, "usd_foil", Double.NaN);
            if (Double.isNaN(nested)) nested = readDouble(prices, "usdFoil", Double.NaN);
            if (!Double.isNaN(nested)) return nested;
        }

        return Double.NaN;
    }

    private static double readDouble(CompoundTag tag, String key, double def) {
        if (tag == null || key == null || key.isBlank()) return def;

        String s = tag.getString(key).orElse("").trim();
        if (!s.isEmpty()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }

        var d = tag.getDouble(key);
        if (d.isPresent()) return d.get();

        var i = tag.getInt(key);
        if (i.isPresent()) return i.get();

        return def;
    }

    private static void putIfBlank(CompoundTag tag, String key, String value) {
        if (tag == null || key == null || key.isBlank() || value == null) return;
        if (tag.getString(key).orElse("").isBlank()) tag.putString(key, value);
    }

    private static void copyString(CompoundTag from, CompoundTag to, String key) {
        if (to.getString(key).orElse("").isBlank()) {
            from.getString(key).ifPresent(value -> {
                if (!value.isBlank()) to.putString(key, value);
            });
        }
    }

    private static void copyInt(CompoundTag from, CompoundTag to, String key) {
        if (!to.getInt(key).isPresent()) {
            from.getInt(key).ifPresent(value -> to.putInt(key, value));
        }
    }

    private static void copyIntAs(CompoundTag from, CompoundTag to, String fromKey, String toKey) {
        if (!to.getInt(toKey).isPresent()) {
            from.getInt(fromKey).ifPresent(value -> to.putInt(toKey, value));
        }
    }

    private static void copyBoolean(CompoundTag from, CompoundTag to, String key) {
        if (!to.getBoolean(key).isPresent()) {
            from.getBoolean(key).ifPresent(value -> to.putBoolean(key, value));
        }
    }

    private static void copyStringList(CompoundTag from, CompoundTag to, String key) {
        if (to.getList(key).isPresent()) return;
        ListTag src = from.getList(key).orElse(null);
        if (src == null) return;

        ListTag copy = new ListTag();
        for (int i = 0; i < src.size(); i++) {
            String value = src.getString(i).orElse("");
            copy.add(StringTag.valueOf(value));
        }
        to.put(key, copy);
    }

    private TcgCardMeta() {
    }
}
