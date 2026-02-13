// com/spider/mtgcard/net/CustomCardServer.java
package com.spider.mtgcard.client;

import com.spider.mtgcard.config.ImportPerms;
import com.spider.mtgcard.content.pack.custom.CustomCardStore;
import com.spider.mtgcard.net.CustomCardPackets;
import com.spider.mtgcard.net.CustomCardSync;
import com.spider.mtgcard.net.WorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class CustomCardServer {

    public static void handleBatch(CustomCardPackets.CustomBatchCreate payload, ServerPlayerEntity who) {
        if (payload == null || who == null) return;

        MinecraftServer server = who.getEntityWorld().getServer();
        if (server == null) return;

        var state = WorldState.get(server);
        if (state == null) return;

        CustomCardStore store = state.customCards();
        if (store == null) return;

        int added = store.addAllFromClient(payload.entries(), who);
        System.out.println("[MTGCard] Batch create: " + added + " cards");
    }

    private CustomCardServer() {}
}
