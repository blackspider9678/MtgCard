package com.spider.mtgcard.client;

import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.model.CardModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public final class ClientEntrypoints {
    public static void init() {
        // all client-only setup lives here
        CardArtManager.init();
        ModelLoadingPlugin.register(new CardModelLoadingPlugin());
    }
    private ClientEntrypoints() {}
}
