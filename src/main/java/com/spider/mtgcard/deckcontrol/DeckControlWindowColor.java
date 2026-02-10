package com.spider.mtgcard.deckcontrol;

import net.minecraft.util.StringIdentifiable;

public enum DeckControlWindowColor implements StringIdentifiable {
    DEFAULT("default"),
    SOULFIRE("soulfire"),
    REDSTONE("redstone"),
    COPPER("copper");

    private final String id;
    DeckControlWindowColor(String id) { this.id = id; }

    @Override public String asString() { return id; }
}
