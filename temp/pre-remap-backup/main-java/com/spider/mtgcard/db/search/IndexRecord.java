package com.spider.mtgcard.db.search;


import java.util.List;


public class IndexRecord {
    public String id, name, set, rarity, typeLine; public int mv; public List<String> colors;
    public String imageSmall;
    // Tokens for advanced search
    public List<String> nameTokens; public List<String> oracleTokens; public List<String> typeTokens;
    public long timestamp;
}