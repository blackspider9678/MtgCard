// com/spider/mtgcard/net/CustomCardSync.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta;
import com.spider.mtgcard.net.CustomCardPackets.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomCardSync {

    public static void initServerHooks(java.util.function.Function<MinecraftServer, CustomCardStore> storeGetter) {
        // 1) Send FULL snapshot on player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var store = storeGetter.apply(server);
            if (store == null) return;

            List<WireMeta> list = toWire(store.all());
            ServerPlayNetworking.send(handler.player, new CustomSyncFull(list));
        });
    }

    public static void broadcastDeltaAdd(MinecraftServer server, CardMeta m) {
        List<WireMeta> one = new ArrayList<>(1);
        one.add(toWire(m));
        var payload = new CustomSyncDelta(one.get(0));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static List<WireMeta> toWire(java.util.Collection<CardMeta> metas) {
        var out = new ArrayList<WireMeta>(metas.size());
        for (var m : metas) out.add(toWire(m));
        return out;
    }
    private static WireMeta toWire(CardMeta m) {
        return new WireMeta(
                nz(m.id), nz(m.name), nz(m.manaCost), nz(m.typeLine), nz(m.rarity), nz(m.set),
                nz(m.oracleText), nz(m.power), nz(m.toughness), nz(m.loyalty),
                m.doubleFaced,
                nz(m.backName), nz(m.backTypeLine), nz(m.backOracleText), nz(m.backPower), nz(m.backToughness), nz(m.backLoyalty)
        );
    }

    // inside class CustomCardSync
    private static final ConcurrentHashMap<MinecraftServer, CustomCardStore> STORES =
            new ConcurrentHashMap<>();

    public static CustomCardStore getStore(MinecraftServer server) {
        return STORES.computeIfAbsent(server, CustomCardStore::new);
    }

    // NEW overload: keep your existing initServerHooks(Function<...>) as-is.
// Add this no-arg version so Mtgcard.java can call initServerHooks() with no args.
    public static void initServerHooks() {
        initServerHooks(CustomCardSync::getStore);
    }

    private static String nz(String s) { return (s == null) ? "" : s; }

    private CustomCardSync() {}
}
