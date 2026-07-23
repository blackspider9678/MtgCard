// src/client/java/com/spider/mtgcard/content/pack/custom/ClientCardIndex.java
package com.spider.mtgcard.client.content.pack.custom;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCardIndex {

    /** Client-side meta snapshot (no dependency on networking package names). */
    public record WireMeta(
            String id, String name, String manaCost, String typeLine, String rarity, String set,
            String oracleText, String power, String toughness, String loyalty,
            boolean doubleFaced,
            String backName, String backTypeLine, String backOracleText, String backPower, String backToughness, String backLoyalty
    ) {}

    private static final Map<String, WireMeta> BY_ID = new ConcurrentHashMap<>();

    public static void applyFull(List<WireMeta> list) {
        BY_ID.clear();
        if (list == null) return;
        for (var m : list) {
            if (m != null && m.id() != null) BY_ID.put(m.id(), m);
        }
    }

    public static void applyDelta(WireMeta m) {
        if (m != null && m.id() != null) BY_ID.put(m.id(), m);
    }

    public static Collection<WireMeta> all() {
        return List.copyOf(BY_ID.values());
    }

    public static WireMeta get(String id) {
        return BY_ID.get(id);
    }

    private ClientCardIndex() {}
}
