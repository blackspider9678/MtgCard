package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.content.pack.custom.ClientCardIndex;
import com.spider.mtgcard.net.CustomCardPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CustomCardClientPackets {
    private static final Map<UUID, FullSyncAccumulator> FULL_SYNCS = new HashMap<>();

    private static final class FullSyncAccumulator {
        final int total;
        final boolean[] seen;
        final ArrayList<ClientCardIndex.WireMeta> entries = new ArrayList<>();
        int seenCount;

        FullSyncAccumulator(int total) {
            this.total = Math.max(1, total);
            this.seen = new boolean[this.total];
        }
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomSyncFull.ID, (payload, ctx) ->
                ctx.client().execute(() ->
                        ClientCardIndex.applyFull(toClientList(payload.entries()))
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomSyncFullChunk.ID, (payload, ctx) ->
                ctx.client().execute(() -> applyFullChunk(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomSyncDelta.ID, (payload, ctx) ->
                ctx.client().execute(() ->
                        ClientCardIndex.applyDelta(toClient(payload.entry()))
                )
        );
    }

    private static void applyFullChunk(CustomCardPackets.CustomSyncFullChunk payload) {
        if (payload == null || payload.syncId() == null) return;
        int total = Math.max(1, payload.total());
        int index = payload.index();
        if (index < 0 || index >= total) return;

        FullSyncAccumulator acc = FULL_SYNCS.get(payload.syncId());
        if (acc == null || acc.total != total) {
            acc = new FullSyncAccumulator(total);
            FULL_SYNCS.put(payload.syncId(), acc);
        }

        if (acc.seen[index]) return;
        acc.seen[index] = true;
        acc.seenCount++;
        acc.entries.addAll(toClientList(payload.entries()));

        if (acc.seenCount >= acc.total) {
            FULL_SYNCS.remove(payload.syncId());
            ClientCardIndex.applyFull(acc.entries);
        }
    }

    private static List<ClientCardIndex.WireMeta> toClientList(List<CustomCardPackets.WireMeta> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        ArrayList<ClientCardIndex.WireMeta> out = new ArrayList<>(entries.size());
        for (CustomCardPackets.WireMeta entry : entries) {
            ClientCardIndex.WireMeta mapped = toClient(entry);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return out;
    }

    private static ClientCardIndex.WireMeta toClient(CustomCardPackets.WireMeta entry) {
        if (entry == null) {
            return null;
        }

        return new ClientCardIndex.WireMeta(
                entry.id(),
                entry.name(),
                entry.manaCost(),
                entry.typeLine(),
                entry.rarity(),
                entry.set(),
                entry.oracleText(),
                entry.power(),
                entry.toughness(),
                entry.loyalty(),
                entry.doubleFaced(),
                entry.backName(),
                entry.backTypeLine(),
                entry.backOracleText(),
                entry.backPower(),
                entry.backToughness(),
                entry.backLoyalty()
        );
    }

    private CustomCardClientPackets() {}
}
