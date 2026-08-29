package com.spider.mtgcard.client;

import com.spider.mtgcard.client.dice.DiceClientHooks;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.model.CardModelLoadingPlugin;
import com.spider.mtgcard.client.render.CardItemRenderer;
import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public final class ClientEntrypoints {
    public static void init() {
        // all client-only setup lives here
        CardArtManager.init();
        SpecialModelRenderers.ID_MAPPER.put(ModRegistry.id("card"), CardItemRenderer.Unbaked.MAP_CODEC);
        DiceClientHooks.init();
        ModelLoadingPlugin.register(new CardModelLoadingPlugin());
    }
    private ClientEntrypoints() {}
}
