package com.spider.mtgcard.client.net;

import com.spider.mtgcard.net.payload.SearchPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class DBClientPackets {
    private DBClientPackets() {}

    public static void sendSearch(String q, String order, String dir, boolean rememberSort) {
        ClientPacketDistributor.sendToServer(new SearchPayload(
                q == null ? "" : q,
                order == null ? "name" : order,
                dir == null ? "asc" : dir,
                rememberSort
        ));
    }
}
