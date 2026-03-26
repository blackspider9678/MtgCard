// src/main/java/com/spider/mtgcard/util/CardRarityUtil.java
package com.spider.mtgcard.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

public final class CardRarityUtil {

    public enum RarityKey { COMMON, UNCOMMON, RARE, MYTHIC, SPECIAL }

    /** Returns null if no mtg_meta exists or rarity missing/unknown. */
    public static RarityKey getRarityKey(ItemStack st) {
        CompoundTag root = StackData.readCustom(st);
        CompoundTag meta = root.getCompound("mtg_meta").orElse(null);
        if (meta == null) return null;

        String raw = meta.getString("rarity").orElse("");
        String r = raw.trim().toLowerCase(Locale.ROOT);

        if (r.contains("mythic")) return RarityKey.MYTHIC;   // "mythic rare"
        if (r.contains("special")) return RarityKey.SPECIAL; // "special"

        return switch (r) {
            case "common" -> RarityKey.COMMON;
            case "uncommon" -> RarityKey.UNCOMMON;
            case "rare" -> RarityKey.RARE;
            default -> null; // reject unknown
        };
    }

    public static int emeraldsFor(RarityKey r) {
        return switch (r) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 2;
            case MYTHIC -> 3;
            case SPECIAL -> 4;
        };
    }

    public static int capFor(RarityKey r) {
        return switch (r) {
            case COMMON -> 16;
            case UNCOMMON -> 8;
            case RARE -> 4;
            case MYTHIC -> 3;
            case SPECIAL -> 1;
        };
    }

    private CardRarityUtil() {}
}
