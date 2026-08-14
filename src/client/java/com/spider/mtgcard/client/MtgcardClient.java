package com.spider.mtgcard.client;

import com.spider.mtgcard.client.guidebook.GuideBookClientNet;
import com.spider.mtgcard.client.compat.flashback.FlashbackArtBridge;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MtgcardClient implements ClientModInitializer {

    private static GLFWDropCallback DROP_CB;

    @Override
    public void onInitializeClient() {
        Mtgcard.LOGGER.info("[MtgcardClient] init");
        ArtImageStorage.ensureWebpCodecsRegistered();
        FlashbackArtBridge.init();

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
            installDropCallback(client);
            lateClientInit();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CardArtManager.pumpQueue();
            FlashbackArtBridge.pumpQueue();
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

    private static void installDropCallback(Minecraft client) {
        long handle = client.getWindow().handle();

        // Free old one if reloading
        if (DROP_CB != null) DROP_CB.free();

        DROP_CB = GLFWDropCallback.create((window, count, names) -> {
            List<Path> paths = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = GLFWDropCallback.getName(names, i);
                if (name != null && !name.isBlank()) {
                    paths.add(Path.of(name));
                }
            }
            if (paths.isEmpty()) return;

            client.execute(() -> {
                Screen s = client.screen;
                if (s instanceof CustomImportScreen cis) {
                    cis.handleFileDrop(paths);
                } else if (s != null) {
                    s.onFilesDrop(paths);
                }
            });
        });

        GLFW.glfwSetDropCallback(handle, DROP_CB);
    }
}
