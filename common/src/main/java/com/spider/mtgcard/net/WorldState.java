// com.spider.mtgcard.net.WorldState.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

public final class WorldState {
    private final CustomCardStore customCards;
    private static final Map<MinecraftServer, WorldState> STATES = new WeakHashMap<>();

    private WorldState(CustomCardStore c) { this.customCards = c; }
    public CustomCardStore customCards() { return customCards; }

    public static synchronized WorldState get(MinecraftServer server) {
        return STATES.computeIfAbsent(server, key -> new WorldState(new CustomCardStore(key)));
    }
}
