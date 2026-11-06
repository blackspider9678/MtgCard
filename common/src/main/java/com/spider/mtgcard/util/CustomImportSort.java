package com.spider.mtgcard.util;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/** Sort keys for the custom-import view; source entries and their link indexes stay untouched. */
public final class CustomImportSort {
    public enum Mode {
        CARDS_ADDED("Cards Added"), FILE_NAME("File Name"), IMAGE_NAME("Image Name"),
        MANA_VALUE("Mana Value"), TYPE("Type"), COLOR("Color"),
        COLLECTOR_NUMBER("Collection Number"), SET("Set");
        public final String label;
        Mode(String label) { this.label = label; }
    }

    public record Key(int added, String fileName, String imageName, String manaCost,
                      String type, String collectorNumber, String set) {}

    private static final Pattern MANA = Pattern.compile("\\{([^}]+)}|(?:[0-9]+|[WUBRGCSXYZP])(?:/(?:[0-9]+|[WUBRGCSXYZP]))*", Pattern.CASE_INSENSITIVE);

    private CustomImportSort() {}

    public static Comparator<Key> comparator(Mode mode) {
        return (a, b) -> {
            int result = switch (mode) {
                case CARDS_ADDED -> Integer.compare(a.added(), b.added());
                case FILE_NAME -> text(a.fileName(), b.fileName());
                case IMAGE_NAME -> text(a.imageName(), b.imageName());
                case MANA_VALUE -> Integer.compare(manaValue(a.manaCost()), manaValue(b.manaCost()));
                case TYPE -> text(a.type(), b.type());
                case COLOR -> Integer.compare(colorOrder(a.manaCost()), colorOrder(b.manaCost()));
                case COLLECTOR_NUMBER -> natural(a.collectorNumber(), b.collectorNumber());
                case SET -> text(a.set(), b.set());
            };
            // Ties retain original insertion order, including blank/unset metadata.
            return result != 0 ? result : Integer.compare(a.added(), b.added());
        };
    }

    private static int text(String a, String b) {
        a = clean(a); b = clean(b);
        if (a.isEmpty() != b.isEmpty()) return a.isEmpty() ? 1 : -1;
        return String.CASE_INSENSITIVE_ORDER.compare(a, b);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static int natural(String a, String b) {
        a = clean(a); b = clean(b);
        if (a.isEmpty() || b.isEmpty()) return text(a, b);
        int ai = 0, bi = 0;
        while (ai < a.length() && bi < b.length()) {
            int ae = ai + 1, be = bi + 1;
            boolean ad = a.charAt(ai) >= '0' && a.charAt(ai) <= '9';
            boolean bd = b.charAt(bi) >= '0' && b.charAt(bi) <= '9';
            int result;
            if (ad && bd) {
                while (ae < a.length() && a.charAt(ae) >= '0' && a.charAt(ae) <= '9') ae++;
                while (be < b.length() && b.charAt(be) >= '0' && b.charAt(be) <= '9') be++;
                result = new BigInteger(a.substring(ai, ae)).compareTo(new BigInteger(b.substring(bi, be)));
            } else {
                result = Character.compare(Character.toLowerCase(a.charAt(ai)), Character.toLowerCase(b.charAt(bi)));
            }
            if (result != 0) return result;
            ai = ae; bi = be;
        }
        return Integer.compare(a.length() - ai, b.length() - bi);
    }

    public static int manaValue(String cost) {
        int total = 0;
        var matcher = MANA.matcher(clean(cost));
        while (matcher.find()) {
            String symbol = matcher.group(1) == null ? matcher.group() : matcher.group(1);
            int value = 0;
            for (String part : symbol.toUpperCase(Locale.ROOT).split("/")) {
                if (part.matches("[0-9]+")) {
                    try { value = Math.max(value, Integer.parseInt(part)); }
                    catch (NumberFormatException ignored) { value = Integer.MAX_VALUE; }
                } else if (part.matches("[WUBRGCSP]")) value = Math.max(value, 1);
            }
            total = (int) Math.min(Integer.MAX_VALUE, (long) total + value);
        }
        return total;
    }

    private static int colorOrder(String cost) {
        String text = clean(cost).toUpperCase(Locale.ROOT);
        int mask = 0;
        for (int i = 0; i < 5; i++) if (text.indexOf("WUBRG".charAt(i)) >= 0) mask |= 1 << i;
        if (mask == 0) return 0;
        if (Integer.bitCount(mask) == 1) return 1 + Integer.numberOfTrailingZeros(mask);
        return 6 + mask;
    }
}
