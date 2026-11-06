package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.content.pack.custom.ClientCardIndex;
import com.spider.mtgcard.net.CustomCardPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

public final class CustomCardClientPackets {

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomSyncFull.ID, (payload, ctx) ->
                ctx.client().execute(() ->
                        ClientCardIndex.applyFull(toClientList(payload.entries()))
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomSyncDelta.ID, (payload, ctx) ->
                ctx.client().execute(() ->
                        ClientCardIndex.applyDelta(toClient(payload.entry()))
                )
        );
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
