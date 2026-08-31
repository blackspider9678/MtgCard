package com.spider.mtgcard.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class Cockatrice {
    public static final class Meta {
        public String name = "", manaCost = "", typeLine = "", rarity = "", set = "", oracleText = "";
        public String collectorNumber = "", imageFileName = "";
        public String power = "", toughness = "", loyalty = "";
        public String backName = "", backTypeLine = "", backOracleText = "", backPower = "", backToughness = "", backLoyalty = "";
        public String relatedTransform = "";
    }

    /** Returns lookup map by lowercased card name. */
    public static Map<String, Meta> parse(String xml) {
        Map<String, Meta> map = new HashMap<>();
        for (Meta m : parseCards(xml)) {
            if (!m.name.isEmpty()) {
                map.put(m.name.toLowerCase(Locale.ROOT), m);
            }
        }
        return map;
    }

    /** Returns cards in XML order, preserving duplicate names. */
    public static List<Meta> parseCards(String xml) {
        List<Meta> out = new ArrayList<>();
        String source = xml == null ? "" : xml.stripLeading();
        if (source.startsWith("{") || source.startsWith("[")) {
            return parseJsonCards(source);
        }
        try {
            var db = DocumentBuilderFactory.newInstance();
            db.setNamespaceAware(false);
            db.setExpandEntityReferences(false);

            var doc = db.newDocumentBuilder().parse(
                    new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            NodeList cards = doc.getElementsByTagName("card");
            for (int i = 0; i < cards.getLength(); i++) {
                Element c = (Element) cards.item(i);
                Meta m = new Meta();

                // Name + oracle
                m.name       = text(c, "name");
                m.oracleText = firstText(c, "text", "rules", "rule_text", "rules_text");
                m.imageFileName = text(c, "image");

                // props (Cockatrice often stores these under <prop> but getElementsByTagName finds them anyway)
                m.manaCost   = firstText(c, "manacost", "cost", "casting_cost");
                Element typeElement = first(c, "type");
                String superType = typeElement == null ? "" : text(typeElement, "supertype");
                String subType = typeElement == null ? "" : text(typeElement, "subtype");
                m.typeLine = !superType.isEmpty() || !subType.isEmpty()
                        ? superType + (superType.isEmpty() || subType.isEmpty() ? "" : " — ") + subType
                        : text(c, "type");

                // --- SET + RARITY FIX ---
                // Cockatrice commonly uses: <set rarity="mythic">JAZZ</set>
                // So set code is the TEXT CONTENT, rarity comes from the attribute.
                Element setEl = first(c, "set");
                if (setEl != null) {
                    String code = setEl.getTextContent() == null ? "" : setEl.getTextContent().trim();
                    if (!code.isEmpty()) m.set = code;

                    String rar = setEl.getAttribute("rarity");
                    if (rar != null && !rar.trim().isEmpty()) m.rarity = rar.trim();

                    String collector = setEl.getAttribute("collectorNumber");
                    if (collector != null && !collector.trim().isEmpty()) m.collectorNumber = collector.trim();
                    // Cockatrice XML v4 / MSE 2.6 uses `num` for the collector number.
                    if (m.collectorNumber.isEmpty()) {
                        collector = setEl.getAttribute("num");
                        if (collector != null && !collector.trim().isEmpty()) m.collectorNumber = collector.trim();
                    }
                }

                // Fallbacks (in case of other Cockatrice variants)
                if (m.rarity.isEmpty()) m.rarity = text(c, "rarity");
                if (m.rarity.isEmpty()) m.rarity = text(c, "set", "rarity");
                if (m.rarity.isEmpty()) m.rarity = attr(c, "set", "rarity");
                if (m.collectorNumber.isEmpty()) m.collectorNumber = text(c, "collectorNumber");
                if (m.collectorNumber.isEmpty()) m.collectorNumber = firstText(c, "number", "card_number", "cardID");

                // Some people export <set name="JAZZ"/> style; keep as backup
                if (m.set.isEmpty()) m.set = attr(c, "set", "name");
                if (m.set.isEmpty()) m.set = text(c, "set"); // last resort: if no attributes, try text()

                // PT + loyalty
                String pt = text(c, "pt");
                if (!pt.isEmpty() && pt.contains("/")) {
                    String[] split = pt.split("/");
                    if (split.length == 2) {
                        m.power = split[0].trim();
                        m.toughness = split[1].trim();
                    }
                }
                if (m.power.isEmpty()) m.power = text(c, "power");
                if (m.toughness.isEmpty()) m.toughness = text(c, "toughness");
                m.loyalty = text(c, "loyalty");
                m.relatedTransform = related(c, "transform");

                // Normalize defaults so your UI doesn't show blank
                if (m.rarity.isEmpty()) m.rarity = "common";
                if (m.set.isEmpty()) m.set = "CSTM";

                if (!m.name.isEmpty()) {
                    out.add(m);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Parses JSON produced by MSE exporters such as Egg's All-in-One and Field Test. */
    private static List<Meta> parseJsonCards(String json) {
        List<Meta> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray cards = root.isJsonArray() ? root.getAsJsonArray()
                    : array(root.getAsJsonObject(), "cards");
            if (cards == null) return out;
            String defaultSet = root.isJsonObject() && root.getAsJsonObject().has("meta")
                    ? value(root.getAsJsonObject().getAsJsonObject("meta"), "setID", "set", "code") : "";
            for (int i = 0; i < cards.size(); i++) {
                if (!cards.get(i).isJsonObject()) continue;
                JsonObject c = cards.get(i).getAsJsonObject();
                if (bool(c, "skip")) continue;
                Meta m = new Meta();
                m.name = value(c, "name", "cardName", "fullName");
                m.manaCost = value(c, "casting_cost", "manaCost", "cost");
                m.typeLine = value(c, "type", "typeLine");
                m.rarity = value(c, "rarity", "rarityLine");
                m.oracleText = value(c, "rules_text", "rulesText", "text");
                m.collectorNumber = value(c, "card_number", "cardID", "number");
                m.set = value(c, "set", "setID");
                if (m.set.isEmpty()) m.set = defaultSet;
                m.power = value(c, "power");
                m.toughness = value(c, "toughness");
                String pt = value(c, "p/t", "pt");
                if ((!pt.isEmpty()) && pt.contains("/")) {
                    String[] halves = pt.split("/", 2);
                    if (m.power.isEmpty()) m.power = halves[0].trim();
                    if (m.toughness.isEmpty()) m.toughness = halves[1].trim();
                }
                m.loyalty = value(c, "loyalty");
                m.backName = value(c, "name2", "cardName2");
                m.backTypeLine = value(c, "type2", "typeLine2");
                m.backOracleText = value(c, "rules_text2", "rulesText2");
                m.backPower = value(c, "power2");
                m.backToughness = value(c, "toughness2");
                m.backLoyalty = value(c, "loyalty2");
                m.imageFileName = value(c, "image", "imageFile", "image_file");
                // Egg's exporter writes card renders as img/<1-based set position>[_front].ext.
                if (m.imageFileName.isEmpty()) m.imageFileName = "img/" + (i + 1) + (m.backName.isEmpty() ? "" : "_front");
                if (m.rarity.isEmpty()) m.rarity = "common";
                if (m.set.isEmpty()) m.set = "CSTM";
                if (!m.name.isEmpty()) out.add(m);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static String value(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                String text = value.getAsString().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            return value != null && value.isJsonPrimitive() && value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String firstText(Element parent, String... tags) {
        for (String tag : tags) {
            String value = text(parent, tag);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static Element first(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return (n instanceof Element e) ? e : null;
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        Node n = nl.item(0);
        return n != null && n.getTextContent() != null ? n.getTextContent().trim() : "";
    }

    private static String text(Element parent, String tag, String attr) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        Node n = nl.item(0);
        if (n instanceof Element e && e.hasAttribute(attr)) return e.getAttribute(attr).trim();
        return "";
    }

    private static String attr(Element parent, String tag, String attr) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        Node n = nl.item(0);
        if (n instanceof Element e && e.hasAttribute(attr)) return e.getAttribute(attr).trim();
        return "";
    }

    private static String related(Element parent, String attach) {
        NodeList nl = parent.getElementsByTagName("related");
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (!(n instanceof Element e)) continue;
            String a = e.getAttribute("attach");
            if (a == null || !a.trim().equalsIgnoreCase(attach)) continue;
            String text = e.getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return "";
    }

    private Cockatrice() {}
}
