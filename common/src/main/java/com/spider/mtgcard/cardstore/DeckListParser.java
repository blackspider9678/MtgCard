package com.spider.mtgcard.cardstore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Format-based parsing for plain lists, Arena/MTGO text, and header-based CSV exports. */
public final class DeckListParser {
    public record Entry(String name, String set, String collectorNumber, int quantity) {}
    private static final Pattern QUANTITY = Pattern.compile("^(\\d+)(?:[xX]\\s*|\\s+)(.+)$");
    private static final Pattern PRINTING = Pattern.compile("^(.*?)\\s+\\(([A-Za-z0-9]{2,12})\\)(?:\\s+([0-9A-Za-z★-]+))?(?:\\s+.*)?$");
    private static final Pattern BRACKET_PRINTING = Pattern.compile("^\\[([A-Za-z0-9]{2,12})(?::([^] ]+))?]\\s*(.+)$");
    private static final Set<String> SECTIONS = Set.of("deck", "main", "mainboard", "main deck", "maindeck",
            "sideboard", "side deck", "commander", "commanders", "companion", "companions", "maybeboard",
            "considering", "tokens", "creatures", "lands", "artifacts", "enchantments", "instants", "sorceries", "planeswalkers");

    private DeckListParser() {}

    public static List<Entry> parseTxt(String text) {
        var out = new ArrayList<Entry>();
        if (text == null) return out;
        for (String raw : text.split("\\R")) {
            String line = clean(raw);
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")
                    || line.toLowerCase(Locale.ROOT).contains("{nodeck}")) continue;
            String heading = line.replaceAll("^[\\[\\s]+|[\\]:\\s]+$", "").toLowerCase(Locale.ROOT);
            if (SECTIONS.contains(heading.replaceFirst("\\s*\\(\\d+\\)$", "").trim()) || line.matches("[-=]{3,}")) continue;
            line = line.replaceFirst("(?i)^(?:SB|sideboard|commander|companion|mainboard):\\s*", "");
            int qty = 1;
            var quantity = QUANTITY.matcher(line);
            if (quantity.matches()) {
                qty = quantity(quantity.group(1));
                line = quantity.group(2).trim();
            }
            if (qty <= 0) continue;
            // Site category/finish annotations are not part of a card's name.
            line = line.replaceFirst("\\s+#.*$", "")
                    .replaceFirst("(?i)\\s+\\*(?:F|E)\\*.*$", "")
                    .replaceFirst("(?i)\\s+\\((?:foil|nonfoil|etched)\\)\\s*$", "")
                    .replaceFirst("\\s+\\[.*]\\s*$", "").trim();
            String name = line, set = "", cn = "";
            var bracket = BRACKET_PRINTING.matcher(line);
            var printing = PRINTING.matcher(line);
            if (bracket.matches()) {
                set = bracket.group(1);
                cn = clean(bracket.group(2));
                name = bracket.group(3);
            } else if (printing.matches()) {
                name = printing.group(1);
                set = printing.group(2);
                cn = clean(printing.group(3));
            }
            if (!name.isBlank() || (!set.isBlank() && !cn.isBlank())) {
                out.add(new Entry(name.trim(), set, cn, qty));
            }
        }
        return out;
    }

    public static List<Entry> parseCsv(String csv) {
        var out = new ArrayList<Entry>();
        if (csv == null || csv.isBlank()) return out;
        var rows = csvRows(csv);
        if (rows.isEmpty()) return out;
        var header = rows.getFirst().stream().map(DeckListParser::header).toList();
        int nameCol = column(header, "name", "cardname", "card", "productname");
        int qtyCol = column(header, "quantity", "qty", "count", "amount", "copies", "totalquantity");
        int setCol = column(header, "setcode", "editioncode", "set", "edition", "setname", "editionname");
        int cnCol = column(header, "collectornumber", "collectorno", "cardnumber", "collector", "cn", "number");
        boolean hasHeader = nameCol >= 0 || qtyCol >= 0 || setCol >= 0 || cnCol >= 0;
        for (int i = hasHeader ? 1 : 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (row.stream().allMatch(String::isBlank)) continue;
            String name, set, cn;
            int qty;
            if (hasHeader) {
                name = cell(row, nameCol);
                set = cell(row, setCol);
                cn = cell(row, cnCol);
                qty = quantity(cell(row, qtyCol));
            } else if (row.size() == 1) {
                out.addAll(parseTxt(row.getFirst()));
                continue;
            } else {
                qty = quantity(cell(row, 0));
                name = cell(row, 1);
                set = cell(row, row.size() >= 9 ? 3 : 2);
                cn = cell(row, row.size() >= 9 ? 8 : 3);
            }
            if (qty > 0 && (!name.isBlank() || (!set.isBlank() && !cn.isBlank()))) {
                out.add(new Entry(name, set, cn, qty));
            }
        }
        return out;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("\uFEFF", "").strip();
    }

    private static String header(String text) {
        return clean(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int column(List<String> header, String... aliases) {
        for (String alias : aliases) {
            int index = header.indexOf(alias);
            if (index >= 0) return index;
        }
        return -1;
    }

    private static String cell(List<String> row, int column) {
        return column < 0 || column >= row.size() ? "" : clean(row.get(column));
    }

    private static int quantity(String text) {
        if (text.isBlank()) return 1;
        try { return Integer.parseInt(text.trim().replaceFirst("(?i)x$", "")); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static List<List<String>> csvRows(String csv) {
        // Select separators outside quoted fields, supporting spreadsheet TSV/semicolon exports too.
        char delimiter = ',';
        int commas = 0, tabs = 0, semicolons = 0;
        boolean quoted = false;
        for (char c : csv.toCharArray()) {
            if (c == '"') quoted = !quoted;
            if (!quoted) {
                if (c == '\n' || c == '\r') break;
                if (c == ',') commas++;
                if (c == '\t') tabs++;
                if (c == ';') semicolons++;
            }
        }
        if (tabs > commas && tabs > semicolons) delimiter = '\t';
        else if (semicolons > commas) delimiter = ';';
        var rows = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { field.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == delimiter || c == '\n' || c == '\r')) {
                row.add(field.toString());
                field.setLength(0);
                if (c != delimiter) {
                    rows.add(row);
                    row = new ArrayList<>();
                    if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                }
            } else field.append(c);
        }
        row.add(field.toString());
        if (row.stream().anyMatch(s -> !s.isBlank())) rows.add(row);
        return rows;
    }
}
