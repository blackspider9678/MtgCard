// com/spider/mtgcard/db/search/CardMeta.java
package com.spider.mtgcard.db.search;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

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
        NbtCompound root = com.spider.mtgcard.util.StackData.readCustom(st);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        String name  = meta.getString("name").orElse("");
        String set   = meta.getString("set").orElse("");
        String rar   = normRarity(meta.getString("rarity").orElse(""));
        int mv       = meta.getInt("mana_value").orElseGet(() -> meta.getInt("cmc").orElse(0));


        // type / oracle: prefer top-level; fall back to first face if needed
        String type  = meta.getString("type_line").orElseGet(() -> firstFace(meta, "type_line"));
        String text  = meta.getString("oracle_text").orElseGet(() -> firstFace(meta, "oracle_text"));

        Set<Character> colors = readColorList(meta.getList("colors").orElse(null));
        Set<Character> ci     = readColorList(meta.getList("color_identity").orElse(null));

        boolean foil  = root.getBoolean("mtg_foil").orElse(false);
        boolean token = meta.getBoolean("is_token_like").orElseGet(() -> meta.getBoolean("is_token").orElse(false));


        return new Info(name, set, rar, mv, colors, ci, type, text, foil, token);
    }

    private static Set<Character> readColorList(NbtList lst) {
        Set<Character> out = new HashSet<>();
        if (lst == null) return out;
        for (int i = 0; i < lst.size(); i++) {
            var s = lst.getString(i).orElse("").toUpperCase(Locale.ROOT);
            if (!s.isEmpty()) {
                char c = s.charAt(0);
                if ("WUBRG".indexOf(c) >= 0) out.add(c);
            }
        }
        return out;
    }

    private static String firstFace(NbtCompound meta, String key) {
        var facesOpt = meta.getList("card_faces");
        if (facesOpt.isEmpty()) return "";
        var faces = facesOpt.get();
        for (int i = 0; i < faces.size(); i++) {
            var f = faces.getCompound(i).orElse(null);
            if (f == null) continue;
            String v = f.getString(key).orElse("");
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
