package com.spider.mtgcard.util;

import java.util.Locale;

public final class ManaValues {
    private ManaValues() {}

    /** Mana value outside the stack; variable symbols contribute zero. */
    public static int manaValueFromCost(String cost) {
        if (cost == null || cost.isBlank()) return 0;
        int total = 0;
        String text = cost.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);
            if (ch == '{') {
                int end = text.indexOf('}', i + 1);
                if (end < 0) break;
                int symbolValue = 0;
                for (String part : text.substring(i + 1, end).split("/")) {
                    symbolValue = Math.max(symbolValue, manaComponentValue(part));
                }
                total += symbolValue;
                i = end + 1;
            } else if (Character.isDigit(ch)) {
                int start = i++;
                while (i < text.length() && Character.isDigit(text.charAt(i))) i++;
                total += manaComponentValue(text.substring(start, i));
            } else {
                total += manaComponentValue(String.valueOf(ch));
                i++;
            }
        }
        return total;
    }

    private static int manaComponentValue(String component) {
        String value = component.trim();
        if (value.length() == 1 && "WUBRGCS".contains(value)) return 1;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

}
