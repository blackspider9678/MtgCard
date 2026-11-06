// com.spider.mtgcard.content.pack.custom.CustomCardStores.java
package com.spider.mtgcard.content.pack.custom;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

public final class CustomCardStores {
    private static final Map<MinecraftServer, CustomCardStore> STORES = new WeakHashMap<>();

    private CustomCardStores() {}

    public static synchronized CustomCardStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, CustomCardStore::new);
    }
}