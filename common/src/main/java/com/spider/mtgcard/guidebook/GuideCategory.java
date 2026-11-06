package com.spider.mtgcard.guidebook;

public enum GuideCategory {
    HOME("Home"),
    BLOCKS("Blocks"),
    ITEMS("Items"),
    GENERAL("General");

    public final String displayName;

    GuideCategory(String displayName) {
        this.displayName = displayName;
    }
}
