package com.spider.mtgcard.db.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared Scryfall-style query parser/evaluator for in-mod card metadata.
 *
 * This intentionally implements the subset we can evaluate from stored card
 * metadata. Official store searches are still sent to Scryfall unchanged.
 */
public final class ScryfallSyntax {
    public interface CardView {
        default String name() { return ""; }
        default String otherNames() { return ""; }
        default String set() { return ""; }
        default String collectorNumber() { return ""; }
        default String rarity() { return ""; }
        default String manaCost() { return ""; }
        default String typeLine() { return ""; }
        default String oracleText() { return ""; }
        default String power() { return ""; }
        default String toughness() { return ""; }
        default String loyalty() { return ""; }
        default String layout() { return ""; }
        default int manaValue() { return 0; }
        default Set<String> colors() { return Set.of(); }
        default Set<String> colorIdentity() { return Set.of(); }
        default boolean foil() { return false; }
        default boolean tokenLike() { return false; }
        default boolean legendary() { return false; }
        default boolean doubleFaced() { return false; }
        default String commanderLegality() { return ""; }
        default double priceUsd() { return Double.NaN; }
    }

    public static final class Parsed {
        private final Node root;

        private Parsed(Node root) {
            this.root = root == null ? TRUE : root;
        }

        public boolean matches(CardView card) {
            return root.matches(card == null ? EMPTY_CARD : card);
        }

        public Predicate<CardView> asPredicate() {
            return this::matches;
        }
    }

    public static Parsed parse(String raw) {
        return new Parsed(new Parser(lex(raw)).parse());
    }

    public static Predicate<CardView> predicate(String raw) {
        return parse(raw).asPredicate();
    }

    public static Map<String, String> options(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (SearchToken token : lex(raw)) {
            if (token.type != TokenType.WORD) continue;

            FieldTerm term = parseFieldTerm(token);
            if (term == null || term.field == null) continue;

            String field = normalizeField(term.field);
            if (!isOptionField(field)) continue;

            if ("sort".equals(field)) field = "order";
            out.put(field, term.value == null ? "" : term.value.trim());
        }
        return out;
    }

    public static boolean looksLikeSyntax(String raw) {
        if (raw == null || raw.isBlank()) return false;
        for (SearchToken token : lex(raw)) {
            if (token.type == TokenType.OR || token.type == TokenType.AND
                    || token.type == TokenType.LPAREN || token.type == TokenType.RPAREN) {
                return true;
            }
            if (token.type != TokenType.WORD) continue;
            String text = token.text == null ? "" : token.text.trim();
            if (text.startsWith("-") || text.startsWith("!")) return true;
            if (parseFieldTerm(token).field != null) return true;
        }
        return false;
    }

    public static Set<String> colorsFromManaCost(String manaCost) {
        if (manaCost == null || manaCost.isBlank()) return Set.of();
        HashSet<String> out = new HashSet<>();
        for (int i = 0; i < manaCost.length(); i++) {
            char ch = Character.toUpperCase(manaCost.charAt(i));
            if ("WUBRG".indexOf(ch) >= 0) out.add(String.valueOf(ch));
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    private static final CardView EMPTY_CARD = new CardView() {};

    private interface Node {
        boolean matches(CardView card);
    }

    private static final Node TRUE = card -> true;
    private static final Node FALSE = card -> false;

    private enum TokenType {
        WORD, OR, AND, LPAREN, RPAREN
    }

    private record SearchToken(TokenType type, String text, boolean quoted) {}

    private static final class Parser {
        private final List<SearchToken> tokens;
        private int pos;

        Parser(List<SearchToken> tokens) {
            this.tokens = tokens == null ? List.of() : tokens;
        }

        Node parse() {
            if (tokens.isEmpty()) return TRUE;
            Node node = parseOr();
            return node == null ? TRUE : node;
        }

        private Node parseOr() {
            ArrayList<Node> nodes = new ArrayList<>();
            nodes.add(parseAnd());

            while (peek(TokenType.OR)) {
                pos++;
                nodes.add(parseAnd());
            }

            return combineOr(nodes);
        }

        private Node parseAnd() {
            ArrayList<Node> nodes = new ArrayList<>();

            while (canStartUnary()) {
                if (peek(TokenType.AND)) {
                    pos++;
                    continue;
                }
                nodes.add(parseUnary());
                if (peek(TokenType.AND)) pos++;
            }

            return nodes.isEmpty() ? TRUE : combineAnd(nodes);
        }

        private Node parseUnary() {
            if (peek(TokenType.WORD) && "-".equals(current().text)) {
                pos++;
                return not(parseUnary());
            }

            if (peek(TokenType.LPAREN)) {
                pos++;
                Node inner = parseOr();
                if (peek(TokenType.RPAREN)) pos++;
                return inner == null ? TRUE : inner;
            }

            if (peek(TokenType.WORD)) {
                SearchToken token = current();
                pos++;
                return new TermNode(parseFieldTerm(token));
            }

            return TRUE;
        }

        private boolean canStartUnary() {
            if (pos >= tokens.size()) return false;
            TokenType type = tokens.get(pos).type;
            return type == TokenType.WORD || type == TokenType.LPAREN || type == TokenType.AND;
        }

        private boolean peek(TokenType type) {
            return pos < tokens.size() && tokens.get(pos).type == type;
        }

        private SearchToken current() {
            return tokens.get(pos);
        }
    }

    private static Node combineAnd(List<Node> nodes) {
        ArrayList<Node> copy = new ArrayList<>();
        if (nodes != null) {
            for (Node node : nodes) if (node != null && node != TRUE) copy.add(node);
        }
        if (copy.isEmpty()) return TRUE;
        return card -> {
            for (Node node : copy) {
                if (!node.matches(card)) return false;
            }
            return true;
        };
    }

    private static Node combineOr(List<Node> nodes) {
        ArrayList<Node> copy = new ArrayList<>();
        if (nodes != null) {
            for (Node node : nodes) if (node != null && node != FALSE) copy.add(node);
        }
        if (copy.isEmpty()) return FALSE;
        if (copy.size() == 1) return copy.getFirst();
        return card -> {
            for (Node node : copy) {
                if (node.matches(card)) return true;
            }
            return false;
        };
    }

    private static Node not(Node node) {
        Node inner = node == null ? TRUE : node;
        return card -> !inner.matches(card);
    }

    private static final class FieldTerm {
        final String field;
        final String op;
        final String value;
        final boolean negated;
        final boolean quoted;

        FieldTerm(String field, String op, String value, boolean negated, boolean quoted) {
            this.field = field;
            this.op = op == null || op.isBlank() ? ":" : op;
            this.value = value == null ? "" : value;
            this.negated = negated;
            this.quoted = quoted;
        }
    }

    private static final class TermNode implements Node {
        private final FieldTerm term;

        TermNode(FieldTerm term) {
            this.term = term;
        }

        @Override
        public boolean matches(CardView card) {
            if (term == null) return true;
            String field = normalizeField(term.field);
            if (isOptionField(field)) return true;

            boolean positive = matchesPositive(card, field, term.op, term.value, term.quoted);
            return term.negated ? !positive : positive;
        }
    }

    private static boolean matchesPositive(CardView card, String field, String op, String value, boolean quoted) {
        String f = field == null ? "" : field;
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return true;

        return switch (f) {
            case "", "free" -> matchText(nameText(card), v, op, card, false);
            case "name" -> matchText(nameText(card), v, op, card, true);
            case "oracle" -> matchText(card.oracleText(), v, op, card, false);
            case "type" -> matchText(card.typeLine(), v, op, card, false);
            case "mana" -> matchMana(card.manaCost(), v, op);
            case "set" -> matchCode(card.set(), v, op);
            case "rarity" -> matchRarity(card.rarity(), v, op);
            case "collector" -> matchText(card.collectorNumber(), v, op, card, false);
            case "color" -> matchColorSet(card.colors(), parseColorQuery(v), op);
            case "color_id" -> matchColorSet(card.colorIdentity(), parseColorQuery(v), op);
            case "commander" -> matchCommander(card, v, op);
            case "mv" -> matchNumber(card.manaValue(), v, op);
            case "power" -> matchStat(card.power(), v, op);
            case "toughness" -> matchStat(card.toughness(), v, op);
            case "loyalty" -> matchStat(card.loyalty(), v, op);
            case "usd", "price" -> matchNumber(card.priceUsd(), v, op);
            case "legal", "format" -> matchFormat(card, v, op);
            case "banned" -> matchLegalityStatus(card, v, "banned");
            case "restricted" -> matchLegalityStatus(card, v, "restricted");
            case "is" -> matchIsFlag(card, v);
            case "not" -> !matchIsFlag(card, v);
            case "game", "lang" -> true;
            default -> false;
        };
    }

    private static List<SearchToken> lex(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return List.of();

        ArrayList<SearchToken> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            if (i >= s.length()) break;

            char first = s.charAt(i);
            if (first == '(') {
                out.add(new SearchToken(TokenType.LPAREN, "(", false));
                i++;
                continue;
            }
            if (first == ')') {
                out.add(new SearchToken(TokenType.RPAREN, ")", false));
                i++;
                continue;
            }

            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == '"' || ch == '\'') {
                    quoted = true;
                    char quote = ch;
                    i++;
                    while (i < s.length()) {
                        char qch = s.charAt(i);
                        if (qch == quote) {
                            i++;
                            break;
                        }
                        if (qch == '\\' && i + 1 < s.length()) {
                            i++;
                            cur.append(s.charAt(i));
                            i++;
                            continue;
                        }
                        cur.append(qch);
                        i++;
                    }
                    continue;
                }

                if (Character.isWhitespace(ch) || ch == '(' || ch == ')') break;
                cur.append(ch);
                i++;
            }

            String text = cur.toString().trim();
            if (text.isEmpty()) {
                i++;
                continue;
            }

            if (!quoted && text.equalsIgnoreCase("or")) {
                out.add(new SearchToken(TokenType.OR, text, false));
            } else if (!quoted && text.equalsIgnoreCase("and")) {
                out.add(new SearchToken(TokenType.AND, text, false));
            } else {
                out.add(new SearchToken(TokenType.WORD, text, quoted));
            }
        }
        return out;
    }

    private static FieldTerm parseFieldTerm(SearchToken token) {
        if (token == null || token.type != TokenType.WORD) {
            return new FieldTerm(null, ":", "", false, false);
        }

        String text = token.text == null ? "" : token.text.trim();
        boolean negated = false;
        while (text.length() > 1 && text.startsWith("-")) {
            negated = !negated;
            text = text.substring(1).trim();
        }

        if (text.startsWith("!") && text.length() > 1 && !text.startsWith("!=")) {
            return new FieldTerm("name", "!", text.substring(1).trim(), negated, token.quoted);
        }

        OperatorHit op = firstOperator(text);
        if (op == null) {
            return new FieldTerm(null, ":", text, negated, token.quoted);
        }

        String field = text.substring(0, op.index).trim();
        String value = text.substring(op.index + op.operator.length()).trim();
        String operator = op.operator;

        if (isComparatorPrefix(value)) {
            String prefix = comparatorPrefix(value);
            if (prefix != null && !prefix.isBlank()) {
                operator = prefix;
                value = value.substring(prefix.length()).trim();
            }
        }

        return new FieldTerm(field, operator, value, negated, token.quoted);
    }

    private record OperatorHit(int index, String operator) {}

    private static OperatorHit firstOperator(String text) {
        if (text == null || text.isEmpty()) return null;
        int best = -1;
        String bestOp = null;
        for (String op : List.of("!=", ">=", "<=", ":", "=", ">", "<")) {
            int idx = text.indexOf(op);
            if (idx <= 0) continue;
            if (best < 0 || idx < best || (idx == best && op.length() > bestOp.length())) {
                best = idx;
                bestOp = op;
            }
        }
        return best < 0 ? null : new OperatorHit(best, bestOp);
    }

    private static boolean isComparatorPrefix(String value) {
        return comparatorPrefix(value) != null;
    }

    private static String comparatorPrefix(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith(">=") || v.startsWith("<=") || v.startsWith("!=")) return v.substring(0, 2);
        if (v.startsWith(">") || v.startsWith("<") || v.startsWith("=")) return v.substring(0, 1);
        return null;
    }

    private static String normalizeField(String rawField) {
        if (rawField == null || rawField.isBlank()) return "free";
        String f = rawField.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (f) {
            case "n" -> "name";
            case "o", "oracle_text", "oracletext", "text", "rules" -> "oracle";
            case "fo", "full_oracle", "fulloracle" -> "oracle";
            case "t", "type_line", "typeline" -> "type";
            case "m", "mana_cost", "manacost", "cost" -> "mana";
            case "s", "e", "edition" -> "set";
            case "r", "rar", "rare" -> "rarity";
            case "c", "colors" -> "color";
            case "ci", "id", "identity", "colorid", "color_identity", "commander_id" -> "color_id";
            case "cn", "number", "collector", "collector_number", "custom_id" -> "collector";
            case "cmc", "manavalue", "mana_value" -> "mv";
            case "pow", "p" -> "power";
            case "tou", "defense" -> "toughness";
            case "loy" -> "loyalty";
            case "f", "legal", "legal_in" -> "legal";
            case "banned_in" -> "banned";
            case "restricted_in" -> "restricted";
            case "order", "sort", "dir", "direction", "unique", "include", "as", "display", "prefer" -> f;
            default -> f;
        };
    }

    private static boolean isOptionField(String field) {
        return switch (field == null ? "" : field) {
            case "order", "sort", "dir", "direction", "unique", "include", "as", "display", "prefer" -> true;
            default -> false;
        };
    }

    private static String nameText(CardView card) {
        return join(card.name(), card.otherNames());
    }

    private static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private static boolean matchText(String haystack, String rawNeedle, String op, CardView card, boolean allowExact) {
        String needle = rawNeedle == null ? "" : rawNeedle.trim();
        if (needle.isEmpty()) return true;

        boolean exact = allowExact && ("=".equals(op) || "!".equals(op));
        boolean notExact = allowExact && "!=".equals(op);
        boolean contains = !exact && !notExact;

        List<String> needles = expandedNeedles(needle, card);
        if (exact) {
            for (String n : needles) {
                if (textKey(haystack).equals(textKey(n))) return true;
            }
            return false;
        }
        if (notExact) {
            for (String n : needles) {
                if (textKey(haystack).equals(textKey(n))) return false;
            }
            return true;
        }
        if (!contains) return false;

        String h = textKey(haystack);
        for (String n : needles) {
            if (h.contains(textKey(n))) return true;
        }
        return false;
    }

    private static List<String> expandedNeedles(String needle, CardView card) {
        if (needle == null) return List.of("");
        if (!needle.contains("~")) return List.of(needle);

        ArrayList<String> out = new ArrayList<>();
        String name = card == null ? "" : card.name();
        if (name != null && !name.isBlank()) out.add(needle.replace("~", name));
        out.add(needle);
        return out;
    }

    private static String textKey(String value) {
        if (value == null) return "";
        String s = value.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return s.replaceAll("\\s+", " ");
    }

    private static boolean matchMana(String manaCost, String rawNeedle, String op) {
        if ("!=".equals(op)) return !matchMana(manaCost, rawNeedle, ":");
        String haystack = textKey(manaCost);
        String needle = textKey(rawNeedle);
        if (needle.isEmpty()) return true;
        if ("=".equals(op) || "!".equals(op)) return haystack.equals(needle);

        String compactHaystack = manaKey(manaCost);
        String compactNeedle = manaKey(rawNeedle);
        return haystack.contains(needle) || (!compactNeedle.isEmpty() && compactHaystack.contains(compactNeedle));
    }

    private static String manaKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace("{", "")
                .replace("}", "")
                .replace("/", "")
                .replace(" ", "")
                .trim();
    }

    private static boolean matchCode(String actual, String expected, String op) {
        String a = textKey(actual);
        String e = textKey(expected);
        if (e.isEmpty()) return true;
        if ("!=".equals(op)) return !a.equals(e);
        if (":".equals(op)) return a.equals(e);
        return a.equals(e);
    }

    private static boolean matchRarity(String actual, String expected, String op) {
        String a = normalizeRarity(actual);
        String e = normalizeRarity(expected);
        if (e.isEmpty()) return true;
        if ("!=".equals(op)) return !a.equals(e);
        return a.equals(e);
    }

    private static String normalizeRarity(String value) {
        String r = textKey(value);
        return switch (r) {
            case "c", "common" -> "common";
            case "u", "uncommon" -> "uncommon";
            case "r", "rare" -> "rare";
            case "m", "mythic", "mythic rare" -> "mythic";
            default -> r;
        };
    }

    private record ColorQuery(Set<String> colors, boolean valid, boolean colorless) {}

    private static ColorQuery parseColorQuery(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return new ColorQuery(Set.of(), false, false);

        String alpha = value.replaceAll("[^a-z0-9]+", "");
        Set<String> combo = COLOR_WORDS.get(alpha);
        if (combo != null) return new ColorQuery(combo, true, combo.isEmpty());

        if ("0".equals(alpha) || "c".equals(alpha) || "colorless".equals(alpha)) {
            return new ColorQuery(Set.of(), true, true);
        }

        HashSet<String> out = new HashSet<>();
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toUpperCase(value.charAt(i));
            if ("WUBRG".indexOf(ch) >= 0) out.add(String.valueOf(ch));
        }
        return out.isEmpty()
                ? new ColorQuery(Set.of(), false, false)
                : new ColorQuery(Set.copyOf(out), true, false);
    }

    private static final Map<String, Set<String>> COLOR_WORDS = colorWords();

    private static Map<String, Set<String>> colorWords() {
        Map<String, Set<String>> out = new HashMap<>();
        putColors(out, "white", "W");
        putColors(out, "blue", "U");
        putColors(out, "black", "B");
        putColors(out, "red", "R");
        putColors(out, "green", "G");
        putColors(out, "colorless", "");
        putColors(out, "azorius", "WU");
        putColors(out, "dimir", "UB");
        putColors(out, "rakdos", "BR");
        putColors(out, "gruul", "RG");
        putColors(out, "selesnya", "GW");
        putColors(out, "orzhov", "WB");
        putColors(out, "izzet", "UR");
        putColors(out, "golgari", "BG");
        putColors(out, "boros", "RW");
        putColors(out, "simic", "GU");
        putColors(out, "bant", "GWU");
        putColors(out, "esper", "WUB");
        putColors(out, "grixis", "UBR");
        putColors(out, "jund", "BRG");
        putColors(out, "naya", "RGW");
        putColors(out, "abzan", "WBG");
        putColors(out, "jeskai", "URW");
        putColors(out, "sultai", "BGU");
        putColors(out, "mardu", "RWB");
        putColors(out, "temur", "GUR");
        putColors(out, "fivecolor", "WUBRG");
        putColors(out, "fivecolors", "WUBRG");
        putColors(out, "all", "WUBRG");
        return out;
    }

    private static void putColors(Map<String, Set<String>> map, String key, String letters) {
        HashSet<String> out = new HashSet<>();
        if (letters != null) {
            for (int i = 0; i < letters.length(); i++) {
                char ch = Character.toUpperCase(letters.charAt(i));
                if ("WUBRG".indexOf(ch) >= 0) out.add(String.valueOf(ch));
            }
        }
        map.put(key, Set.copyOf(out));
    }

    private static boolean matchColorSet(Set<String> actualRaw, ColorQuery query, String op) {
        if (query == null || !query.valid) return false;
        Set<String> actual = normalizeColors(actualRaw);
        Set<String> need = query.colors;
        String operator = op == null || op.isBlank() ? ":" : op;

        if (query.colorless) {
            return switch (operator) {
                case "!=", ">" -> !actual.isEmpty();
                default -> actual.isEmpty();
            };
        }

        return switch (operator) {
            case ":", ">=" -> actual.containsAll(need);
            case "=" -> actual.equals(need);
            case "!=" -> !actual.equals(need);
            case "<=" -> need.containsAll(actual);
            case ">" -> actual.containsAll(need) && !actual.equals(need);
            case "<" -> need.containsAll(actual) && !actual.equals(need);
            default -> false;
        };
    }

    private static Set<String> normalizeColors(Set<String> colors) {
        if (colors == null || colors.isEmpty()) return Set.of();
        HashSet<String> out = new HashSet<>();
        for (String color : colors) {
            if (color == null || color.isBlank()) continue;
            char ch = Character.toUpperCase(color.trim().charAt(0));
            if ("WUBRG".indexOf(ch) >= 0) out.add(String.valueOf(ch));
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    private static boolean matchCommander(CardView card, String value, String op) {
        ColorQuery colors = parseColorQuery(value);
        if (colors.valid) {
            String operator = (op == null || op.isBlank() || ":".equals(op)) ? "<=" : op;
            return matchColorSet(card.colorIdentity(), colors, operator) && commanderLegalOrUnknown(card);
        }
        return matchFormat(card, value, op);
    }

    private static boolean matchFormat(CardView card, String value, String op) {
        String format = textKey(value);
        if (format.equals("edh")) format = "commander";
        if (format.equals("commander")) return commanderLegalOrUnknown(card);
        return true;
    }

    private static boolean matchLegalityStatus(CardView card, String value, String expectedStatus) {
        String format = textKey(value);
        if (format.equals("edh")) format = "commander";
        if (!format.equals("commander")) return false;
        String status = textKey(card.commanderLegality());
        return status.equals(textKey(expectedStatus));
    }

    private static boolean commanderLegalOrUnknown(CardView card) {
        String status = textKey(card.commanderLegality());
        return status.isEmpty() || status.equals("legal");
    }

    private static boolean matchNumber(double actual, String rawNeedle, String op) {
        double expected = parseDouble(rawNeedle);
        if (Double.isNaN(expected)) return false;
        if (Double.isNaN(actual)) return false;
        String operator = op == null || op.isBlank() || ":".equals(op) ? "=" : op;
        return switch (operator) {
            case ">" -> actual > expected;
            case ">=" -> actual >= expected;
            case "<" -> actual < expected;
            case "<=" -> actual <= expected;
            case "!=" -> Double.compare(actual, expected) != 0;
            default -> Double.compare(actual, expected) == 0;
        };
    }

    private static boolean matchStat(String actualRaw, String expectedRaw, String op) {
        double actual = parseDouble(actualRaw);
        double expected = parseDouble(expectedRaw);
        if (!Double.isNaN(actual) && !Double.isNaN(expected)) {
            return matchNumber(actual, expectedRaw, op);
        }

        String operator = op == null || op.isBlank() ? ":" : op;
        if (List.of(">", ">=", "<", "<=").contains(operator)) return false;
        return matchText(actualRaw, expectedRaw, operator, EMPTY_CARD, true);
    }

    private static double parseDouble(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-") || s.equals("\u2014")) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static boolean matchIsFlag(CardView card, String rawFlag) {
        String flag = textKey(rawFlag).replace("_", "-");
        if (flag.isEmpty()) return true;

        return switch (flag) {
            case "foil" -> card.foil();
            case "nonfoil", "non-foil" -> !card.foil();
            case "token", "extra", "extras" -> card.tokenLike();
            case "legendary" -> card.legendary() || hasTypeWord(card.typeLine(), "legendary");
            case "creature", "artifact", "enchantment", "instant", "sorcery", "land",
                    "planeswalker", "battle" -> hasTypeWord(card.typeLine(), flag);
            case "spell" -> !hasTypeWord(card.typeLine(), "land");
            case "permanent" -> hasAnyType(card.typeLine(), Set.of("artifact", "battle", "creature",
                    "enchantment", "land", "planeswalker"));
            case "multicolor", "multicolored" -> normalizeColors(card.colors()).size() > 1;
            case "monocolor", "monocolored" -> normalizeColors(card.colors()).size() == 1;
            case "colorless" -> normalizeColors(card.colors()).isEmpty();
            case "commander" -> commanderLegalOrUnknown(card)
                    && (card.legendary() || hasTypeWord(card.typeLine(), "legendary"))
                    && hasTypeWord(card.typeLine(), "creature");
            case "dfc", "double-faced", "doublefaced" -> card.doubleFaced()
                    || containsAny(textKey(card.layout()), "transform", "modal_dfc", "double_faced");
            case "mdfc", "modal" -> textKey(card.layout()).contains("modal");
            case "transform" -> textKey(card.layout()).contains("transform");
            case "split" -> textKey(card.layout()).contains("split");
            case "adventure" -> textKey(card.layout()).contains("adventure");
            default -> false;
        };
    }

    private static boolean hasAnyType(String typeLine, Set<String> words) {
        for (String word : words) {
            if (hasTypeWord(typeLine, word)) return true;
        }
        return false;
    }

    private static boolean hasTypeWord(String typeLine, String word) {
        if (typeLine == null || word == null || word.isBlank()) return false;
        String target = word.toLowerCase(Locale.ROOT);
        for (String part : typeLine.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (part.equals(target)) return true;
        }
        return false;
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || needles == null) return false;
        for (String needle : needles) {
            if (needle != null && value.contains(needle)) return true;
        }
        return false;
    }

    private ScryfallSyntax() {
    }
}
