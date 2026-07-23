package com.spider.mtgcard.content.pack.cache;

import java.util.List;
import java.util.Map;

/** Minimal Scryfall card shape used for card item metadata. */
public final class ScryfallModels {
    public static final class Card {
        public String id;
        public String oracleId;
        public String name;
        public String set;
        public String collectorNumber;
        public String rarity;
        public String layout;

        public List<String> colors;
        public List<String> colorIdentity;
        public String manaCost;
        public String typeLine;
        public String oracleText;
        public String power;
        public String toughness;
        public String loyalty;
        public int manaValue;
        public Map<String, String> legalities;

        public static final class Face {
            public String name;
            public Map<String, String> imageUris;
            public String manaCost;
            public String typeLine;
            public String oracleText;
            public String power;
            public String toughness;
            public String loyalty;
        }

        public List<Face> faces;
        public Map<String, String> imageUris;

        public static final class Price {
            public String usd = "-";
            public String usdFoil = "-";
            public String usdEtched = "-";
            public String eur = "-";
            public String eurFoil = "-";
            public String tix = "-";
        }

        public Price price = new Price();
        public boolean isTokenLike;
        public String scryfallUri;
    }

    private ScryfallModels() {
    }
}
