// com.spider.mtgcard.net.WorldState.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import net.minecraft.server.MinecraftServer;

public final class WorldState {
    private final CustomCardStore customCards;
    private static WorldState INSTANCE;

    private WorldState(CustomCardStore c) { this.customCards = c; }
    public CustomCardStore customCards() { return customCards; }

    public static WorldState get(MinecraftServer server) {
        if (INSTANCE == null) INSTANCE = new WorldState(new CustomCardStore(server));
        return INSTANCE;
    }
}
