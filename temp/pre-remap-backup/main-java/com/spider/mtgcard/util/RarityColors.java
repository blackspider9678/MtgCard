package com.spider.mtgcard.util;

import net.minecraft.network.chat.TextColor;

import java.util.Locale;

public final class RarityColors {
    private RarityColors() {}

    public static TextColor colorForRarity(String rarity) {
        if (rarity == null) return TextColor.fromRgb(0xFFFFFF);

        return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
            case "common", "c"    -> TextColor.fromRgb(0xFFFFFF); // white
            case "uncommon", "u"  -> TextColor.fromRgb(0x55FFFF); // light blue/aqua
            case "rare", "r"      -> TextColor.fromRgb(0xFFD700); // gold
            case "mythic", "m"    -> TextColor.fromRgb(0xE5A4A4); // rose gold
            case "special", "s"   -> TextColor.fromRgb(0xFF55FF); // magenta
            default               -> TextColor.fromRgb(0xFFFFFF);
        };
    }
}
