// com.spider.mtgcard.util.CustomCardPricing.java
package com.spider.mtgcard.util;

import java.util.Locale;

public final class CustomCardPricing {
    private CustomCardPricing() {}

    public static long priceItemsForRarity(String rarity) {
        if (rarity == null) return 1;
        String r = rarity.toLowerCase(Locale.ROOT).trim();
        return switch (r) {
            case "uncommon" -> 2;
            case "rare" -> 3;
            case "mythic", "mythic rare", "mythic_rare" -> 4;
            default -> 1; // common/unknown
        };
    }
}
