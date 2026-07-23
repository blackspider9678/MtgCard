package com.spider.mtgcard.deckcontrol;

import net.minecraft.util.StringRepresentable;

public enum DeckControlWindowColor implements StringRepresentable {
    DEFAULT("default"),
    SOULFIRE("soulfire"),
    REDSTONE("redstone"),
    COPPER("copper");

    private final String id;
    DeckControlWindowColor(String id) { this.id = id; }

    @Override public String getSerializedName() { return id; }
}
