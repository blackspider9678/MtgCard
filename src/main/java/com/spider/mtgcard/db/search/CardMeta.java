// com/spider/mtgcard/db/search/CardMeta.java
package com.spider.mtgcard.db.search;

import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.world.item.ItemStack;

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

        TcgCardMeta.Info meta = TcgCardMeta.read(st);
        return new Info(
                meta.name(),
                meta.set(),
                normRarity(meta.rarity()),
                meta.manaValue(),
                readColorSet(meta.colors()),
                readColorSet(meta.colorIdentity()),
                meta.typeLine(),
                meta.oracleText(),
                meta.foil(),
                meta.tokenLike()
        );
    }

    private static Set<Character> readColorSet(Set<String> colors) {
        Set<Character> out = new HashSet<>();
        if (colors == null) return out;
        for (String s : colors) {
            String value = s == null ? "" : s.toUpperCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            char c = value.charAt(0);
            if ("WUBRG".indexOf(c) >= 0) out.add(c);
        }
        return out;
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
