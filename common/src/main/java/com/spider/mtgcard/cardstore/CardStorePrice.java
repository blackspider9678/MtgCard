package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.config.MtgcardConfig;

import java.util.Locale;

public final class CardStorePrice {

    private enum PriceTier { NORMAL, FOIL, ETCHED }
    private record PriceResult(String value, PriceTier tier) {}

    /** Convert using config basis, from already-extracted Scryfall price strings. */
    public static long toCurrencyItemsFromStrings(
            boolean preferFoil,
            String usd, String usdFoil, String usdEtched,
            String eur, String eurFoil,
            String tix
    ) {
        String basis = MtgcardConfig.get().Price_Basis;
        if (basis == null || basis.isBlank()) basis = "USD";
        basis = basis.trim().toUpperCase(Locale.ROOT);

        PriceResult pr = switch (basis) {
            case "EUR" -> pickBestPrice(preferFoil, eur, eurFoil, null);
            case "TIX" -> new PriceResult(isValidPrice(tix) ? tix : "0", PriceTier.NORMAL);
            case "USD" -> pickBestPrice(preferFoil, usd, usdFoil, usdEtched);
            default    -> pickBestPrice(preferFoil, usd, usdFoil, usdEtched);
        };

        long items = roundPriceToWhole(pr.value());
        return Math.max(1L, items);
    }

    private static PriceResult pickBestPrice(boolean preferFoil, String normal, String foil, String etched) {
        if (preferFoil) {
            if (isValidPrice(foil))   return new PriceResult(foil, PriceTier.FOIL);
            if (isValidPrice(etched)) return new PriceResult(etched, PriceTier.ETCHED);
            if (isValidPrice(normal)) return new PriceResult(normal, PriceTier.NORMAL);
        }
        if (isValidPrice(normal)) return new PriceResult(normal, PriceTier.NORMAL);
        if (isValidPrice(foil))   return new PriceResult(foil, PriceTier.FOIL);
        if (isValidPrice(etched)) return new PriceResult(etched, PriceTier.ETCHED);
        return new PriceResult("0", PriceTier.NORMAL);
    }

    private static boolean isValidPrice(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !(t.isEmpty() || t.equals("-") || t.equals("—"));
    }

    private static int roundPriceToWhole(String price) {
        if (price == null) return 0;
        String s = price.trim();
        if (s.isEmpty() || s.equals("—") || s.equals("-")) return 0;

        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
            long r = Math.round(v);
            if (r < 0) r = 0;
            if (r > Integer.MAX_VALUE) r = Integer.MAX_VALUE;
            return (int) r;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private CardStorePrice() {}
}
