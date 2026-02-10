package com.spider.mtgcard.client;

import com.spider.mtgcard.client.guidebook.GuideBookClientNet;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.registry.ModParticles;
import com.spider.mtgcard.registry.ModRegistry;
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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRendererFactories;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
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

        // Safe early: payload types, networking receivers, screen registration, model loading plugin
        ModPayloads.registerTypes();
        ClientEntrypoints.init();

        CustomImportClientPackets.registerClientReceivers();
        ArtClientPackets.registerClientReceivers();
        DBClientPackets.registerClientReceivers();
        ModNetworkingClient.initClient();
        LifePointClientPackets.register();
        DeckControlClientNetworking.register();
        CardStoreClientPackets.registerClient();
        CardStoreClientNetworking.register();
        CardDisplayClientPackets.registerClientReceivers();

        HandledScreens.register(ModScreenHandlers.DECKBOX, DeckboxScreen::new);
        HandledScreens.register(ModScreenHandlers.CARD_DB, CardDatabaseScreen::new);
        HandledScreens.register(ModScreenHandlers.DECKCONTROL, DeckControlScreen::new);
        HandledScreens.register(ModScreenHandlers.GRAVEYARD, GraveyardScreen::new);
        HandledScreens.register(ModScreenHandlers.CARD_STORE, CardStoreScreen::new);

        GuideBookClientNet.init();

        // Window-dependent and renderer registration after client is started
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            installDropCallback(client);
            lateClientInit();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CardArtManager.pumpQueue();
        });
    }

    private static void lateClientInit() {
        // Register renderers AFTER the window/device exists
        EntityRendererFactories.register(ModEntities.CARD_DISPLAY, CardDisplayEntityRenderer::new);

        BlockEntityRendererFactories.register(ModBlockEntities.DISPLAY_BLOCK,
                com.spider.mtgcard.client.displayblock.DisplayBlockEntityRenderer::new
        );
        BlockEntityRendererRegistry.register(ModBlockEntities.CARD_DB, CardDatabaseBlockEntityRenderer::new);

        // Render layers (safe either place, but fine here too)
        BlockRenderLayerMap.putBlock(LifePointRegistry.LIFE_POINT_BLOCK, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECKBOX, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_STONE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_GRANITE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_DIORITE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_ANDESITE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_TUFF, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_DEEPSLATE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_POLISHED_BLACKSTONE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModBlocks.DECK_CONTROL_PRISMARINE, BlockRenderLayer.CUTOUT);

        BlockEntityRendererFactories.register(
                ModBlockEntities.DECK_CONTROL,
                com.spider.mtgcard.client.deckcontrol.DeckControlEntityRenderer::new
        );

        CardCounterHoverHud.init();
        UnpackHud.init();
        PackClientEvents.init();

        Mtgcard.LOGGER.info("[MtgcardClient] late init done");
    }

    private static void installDropCallback(MinecraftClient client) {
        long handle = client.getWindow().getHandle();

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
                Screen s = client.currentScreen;
                if (s instanceof CustomImportScreen cis) {
                    cis.handleFileDrop(paths);
                } else if (s != null) {
                    s.onFilesDropped(paths);
                }
            });
        });

        GLFW.glfwSetDropCallback(handle, DROP_CB);
    }
}
