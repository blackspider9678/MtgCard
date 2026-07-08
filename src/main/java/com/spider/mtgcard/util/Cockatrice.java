package com.spider.mtgcard.util;

import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public final class Cockatrice {
    public static final class Meta {
        public String name = "", manaCost = "", typeLine = "", rarity = "", set = "", oracleText = "";
        public String power = "", toughness = "", loyalty = "";
        public String backName = "", backTypeLine = "", backOracleText = "", backPower = "", backToughness = "", backLoyalty = "";
        public String relatedTransform = "";
    }

    /** Returns lookup map by lowercased card name. */
    public static Map<String, Meta> parse(String xml) {
        Map<String, Meta> map = new HashMap<>();
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
                m.oracleText = text(c, "text");

                // props (Cockatrice often stores these under <prop> but getElementsByTagName finds them anyway)
                m.manaCost   = text(c, "manacost");
                m.typeLine   = text(c, "type");

                // --- SET + RARITY FIX ---
                // Cockatrice commonly uses: <set rarity="mythic">JAZZ</set>
                // So set code is the TEXT CONTENT, rarity comes from the attribute.
                Element setEl = first(c, "set");
                if (setEl != null) {
                    String code = setEl.getTextContent() == null ? "" : setEl.getTextContent().trim();
                    if (!code.isEmpty()) m.set = code;

                    String rar = setEl.getAttribute("rarity");
                    if (rar != null && !rar.trim().isEmpty()) m.rarity = rar.trim();
                }

                // Fallbacks (in case of other Cockatrice variants)
                if (m.rarity.isEmpty()) m.rarity = text(c, "rarity");
                if (m.rarity.isEmpty()) m.rarity = text(c, "set", "rarity");
                if (m.rarity.isEmpty()) m.rarity = attr(c, "set", "rarity");

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
                m.loyalty = text(c, "loyalty");
                m.relatedTransform = related(c, "transform");

                // Normalize defaults so your UI doesn't show blank
                if (m.rarity.isEmpty()) m.rarity = "common";
                if (m.set.isEmpty()) m.set = "CSTM";

                if (!m.name.isEmpty()) {
                    map.put(m.name.toLowerCase(Locale.ROOT), m);
                }
            }
        } catch (Exception ignored) {}
        return map;
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
