// com.spider.mtgcard.content.pack.custom.CustomCardStores.java
package com.spider.mtgcard.content.pack.custom;

import com.spider.mtgcard.net.WorldState;
import net.minecraft.server.MinecraftServer;

public final class CustomCardStores {
    private CustomCardStores() {}

    public static CustomCardStore get(MinecraftServer server) {
        return WorldState.get(server).customCards();
    }
}
