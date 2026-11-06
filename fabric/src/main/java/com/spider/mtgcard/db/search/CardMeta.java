// com/spider/mtgcard/db/search/CardMeta.java
package com.spider.mtgcard.db.search;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CardMeta {
    public static record Info(
            String name,
            String set,
            String rarity,         // normalized: c/u/r/m if possible
            int mv,                // from meta.cmc
            Set<Character> colors, // W U B R G
            Set<Character> colorIdentity,
            String typeLine,
            String oracleText,
            boolean foil,
            boolean token
    ) {}

    public static Info read(ItemStack st) {
        if (st == null || st.isEmpty()) return empty();

        // Your helper already normalizes CUSTOM_DATA:
        CompoundTag root = com.spider.mtgcard.util.StackData.readCustom(st);
        CompoundTag meta = root.getCompound("mtg_meta");

        String name  = meta.getString("name");
        String set   = meta.getString("set");
        String rar   = normRarity(meta.getString("rarity"));
        int mv       = meta.contains("mana_value") ? meta.getInt("mana_value") : meta.getInt("cmc");


        // type / oracle: prefer top-level; fall back to first face if needed
        String type  = meta.getString("type_line");
        if (type.isEmpty()) type = firstFace(meta, "type_line");
        String text  = meta.getString("oracle_text");
        if (text.isEmpty()) text = firstFace(meta, "oracle_text");

        Set<Character> colors = readColorList(meta.getList("colors", Tag.TAG_STRING));
        Set<Character> ci     = readColorList(meta.getList("color_identity", Tag.TAG_STRING));

        boolean foil  = root.getBoolean("mtg_foil");
        boolean token = meta.contains("is_token_like") ? meta.getBoolean("is_token_like") : meta.getBoolean("is_token");


        return new Info(name, set, rar, mv, colors, ci, type, text, foil, token);
    }

    private static Set<Character> readColorList(ListTag lst) {
        Set<Character> out = new HashSet<>();
        if (lst == null) return out;
        for (int i = 0; i < lst.size(); i++) {
            var s = lst.getString(i).toUpperCase(Locale.ROOT);
            if (!s.isEmpty()) {
                char c = s.charAt(0);
                if ("WUBRG".indexOf(c) >= 0) out.add(c);
            }
        }
        return out;
    }

    private static String firstFace(CompoundTag meta, String key) {
        var faces = meta.getList("card_faces", Tag.TAG_COMPOUND);
        if (faces.isEmpty()) return "";
        for (int i = 0; i < faces.size(); i++) {
            var f = faces.getCompound(i);
            if (f == null) continue;
            String v = f.getString(key);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static String normRarity(String r) {
        String x = r.toLowerCase(Locale.ROOT);
        return switch (x) {
            case "c","common" -> "c";
            case "u","uncommon" -> "u";
            case "r","rare" -> "r";
            case "m","mythic","mythic rare" -> "m";
            default -> x;
        };
    }

    private static Info empty() {
        return new Info("", "", "", 0, Set.of(), Set.of(), "", "", false, false);
    }

    private CardMeta() {}
}
