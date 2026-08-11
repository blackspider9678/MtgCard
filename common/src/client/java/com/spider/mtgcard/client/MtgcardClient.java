package com.spider.mtgcard.client;

import com.spider.mtgcard.client.guidebook.GuideBookClientNet;
import com.spider.mtgcard.client.hud.CardPeekHud;
import com.spider.mtgcard.client.input.ModKeybinds;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.tooltips.CardTooltipHints;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.client.gui.*;
import com.spider.mtgcard.client.display.CardDisplayEntityRenderer;
import com.spider.mtgcard.client.render.CardDatabaseBlockEntityRenderer;

import com.spider.mtgcard.client.life.LifePointClientPackets;
import com.spider.mtgcard.client.net.*;
import com.spider.mtgcard.client.cardstore.CardStoreClientPackets;
import com.spider.mtgcard.life.LifePointRegistry;
import com.spider.mtgcard.net.ModPayloads;
import com.spider.mtgcard.screen.ModScreenHandlers;
import com.spider.mtgcard.util.ArtImageStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class MtgcardClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Mtgcard.LOGGER.info("[MtgcardClient] init");
        ArtImageStorage.ensureWebpCodecsRegistered();

        // Safe early: payload types, networking receivers, screen registration, model loading plugin
        ModPayloads.registerTypes();
        ClientEntrypoints.init();

        CustomImportClientPackets.registerClientReceivers();
        ArtClientPackets.registerClientReceivers();
        CustomCardClientPackets.registerClientReceivers();
        DBClientPackets.registerClientReceivers();
        ModNetworkingClient.initClient();
        LifePointClientPackets.register();
        DeckControlClientNetworking.register();
        CardStoreClientPackets.registerClient();
        CardStoreClientNetworking.register();
        CardDisplayClientPackets.registerClientReceivers();

        MenuScreens.register(ModScreenHandlers.DECKBOX, DeckboxScreen::new);
        MenuScreens.register(ModScreenHandlers.CARD_DB, CardDatabaseScreen::new);
        MenuScreens.register(ModScreenHandlers.DECKCONTROL, DeckControlScreen::new);
        MenuScreens.register(ModScreenHandlers.GRAVEYARD, GraveyardScreen::new);
        MenuScreens.register(ModScreenHandlers.CARD_STORE, CardStoreScreen::new);
        MenuScreens.register(ModScreenHandlers.DICE_CUSTOMIZER, DiceCustomizerScreen::new);

        GuideBookClientNet.init();
        ModKeybinds.init();
        CardPeekHud.init();
        CardTooltipHints.init();

        // Window-dependent and renderer registration after client is started
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            lateClientInit();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CardArtManager.pumpQueue();
        });
    }

    private static void lateClientInit() {
        // Register renderers AFTER the window/device exists
        EntityRenderers.register(ModEntities.CARD_DISPLAY, CardDisplayEntityRenderer::new);

        BlockEntityRenderers.register(ModBlockEntities.DISPLAY_BLOCK,
                com.spider.mtgcard.client.displayblock.DisplayBlockEntityRenderer::new
        );
        BlockEntityRendererRegistry.register(ModBlockEntities.CARD_DB, CardDatabaseBlockEntityRenderer::new);

        BlockEntityRenderers.register(
                ModBlockEntities.DECK_CONTROL,
                com.spider.mtgcard.client.deckcontrol.DeckControlEntityRenderer::new
        );

        CardCounterHoverHud.init();
        UnpackHud.init();
        PackClientEvents.init();

        Mtgcard.LOGGER.info("[MtgcardClient] late init done");
    }
}
