package com.spider.mtgcard.content.pack.cache;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal “shape” you need for card NBT. Backed by your DB rows. */
public final class ScryfallModels {
    public static final class Card {
        public String id;                 // scryfall id for this print
        public String oracleId;           // oracle id (rules ref)
        public String name;
        public String set;                // set code
        public String collectorNumber;
        public String rarity;             // "common","uncommon","rare","mythic"
        public String layout;             // normal, split, adventure, transform, modal_dfc, etc.

        public List<String> colors;       // ["W","U","B","R","G"]
        public List<String> colorIdentity;
        public String typeLine;           // "Legendary Creature — …"
        public String power;              // e.g. "3"
        public String toughness;          // e.g. "4"
        public int manaValue;             // CMC
        public Map<String,String> legalities; // {"commander":"legal",...}

        // faces (for DFC, etc.) — each with image uri
        public static final class Face {
            public String name;
            public Map<String,String> imageUris; // { "png": "<url>", "normal": "<url>", ... }
            public String manaCost;
            public String typeLine;
            public String oracleText;
            public String power;
            public String toughness;
        }
        public List<Face> faces;          // null/empty for single-faced
        public Map<String,String> imageUris;   // for single-faced, same as faces[0].imageUris

        // prices
        // prices (match ScryfallInfoManager.Entry + Scryfall JSON: strings or null)
        public static final class Price {
            public String usd = "—";
            public String usdFoil = "—";
            public String usdEtched = "—";
            public String eur = "—";
            public String eurFoil = "—";
            public String tix = "—";
        }
        public Price price = new Price();

        // token/art/extra flags
        public boolean isTokenLike;       // token/emblem/dungeon/artseries etc.

        // “nice to have” links
        public String scryfallUri;        // web page
    }
    private ScryfallModels(){}
}
