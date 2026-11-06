// com/spider/mtgcard/net/CustomCardSync.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta;
import com.spider.mtgcard.net.CustomCardPackets.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomCardSync {

    public static void initServerHooks(java.util.function.Function<MinecraftServer, CustomCardStore> storeGetter) {
        // NeoForge registers the player-join hook from the mod entrypoint.
    }

    public static void sendFullTo(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return;
        var state = WorldState.get(player.level().getServer());
        if (state == null || state.customCards() == null) return;

        List<WireMeta> list = toWire(state.customCards().all());
        PacketDistributor.sendToPlayer(player, new CustomSyncFull(list));
    }

    public static void broadcastDeltaAdd(MinecraftServer server, CardMeta m) {
        List<WireMeta> one = new ArrayList<>(1);
        one.add(toWire(m));
        var payload = new CustomSyncDelta(one.get(0));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, payload);
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
