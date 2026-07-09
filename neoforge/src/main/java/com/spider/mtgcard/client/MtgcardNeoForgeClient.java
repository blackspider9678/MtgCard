package com.spider.mtgcard.client;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.client.deckcontrol.DeckControlEntityRenderer;
import com.spider.mtgcard.client.display.CardDisplayEntityRenderer;
import com.spider.mtgcard.client.displayblock.DisplayBlockEntityRenderer;
import com.spider.mtgcard.client.compat.GuiGraphics;
import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.client.gui.CardStoreScreen;
import com.spider.mtgcard.client.gui.DeckControlScreen;
import com.spider.mtgcard.client.gui.DeckboxScreen;
import com.spider.mtgcard.client.gui.GraveyardScreen;
import com.spider.mtgcard.client.hud.CardPeekHud;
import com.spider.mtgcard.client.input.ModKeybinds;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.life.LifePointFrontTextRenderer;
import com.spider.mtgcard.client.render.CardDatabaseBlockEntityRenderer;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.screen.ModScreenHandlers;
import com.spider.mtgcard.util.ArtImageStorage;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MtgcardNeoForgeClient {
    private static final CardPeekHud CARD_PEEK_HUD = new CardPeekHud();

    private MtgcardNeoForgeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MtgcardNeoForgeClient::onClientSetup);
        modBus.addListener(MtgcardNeoForgeClient::registerKeyMappings);
        modBus.addListener(MtgcardNeoForgeClient::registerMenuScreens);
        modBus.addListener(MtgcardNeoForgeClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForgeClient::renderCardPeekHud);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ArtImageStorage.ensureWebpCodecsRegistered();
            CardArtManager.init();
        });
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModScreenHandlers.DECKBOX, DeckboxScreen::new);
        event.register(ModScreenHandlers.CARD_DB, CardDatabaseScreen::new);
        event.register(ModScreenHandlers.DECKCONTROL, DeckControlScreen::new);
        event.register(ModScreenHandlers.GRAVEYARD, GraveyardScreen::new);
        event.register(ModScreenHandlers.CARD_STORE, CardStoreScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CARD_DISPLAY, CardDisplayEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISPLAY_BLOCK, DisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CARD_DB, CardDatabaseBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECK_CONTROL, DeckControlEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LIFE_POINT, LifePointFrontTextRenderer::new);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeybinds.createMappings();
        event.register(ModKeybinds.TOGGLE_CARD_PEEK);
        event.register(ModKeybinds.FLIP_CARD_FACE);
    }

    private static void renderCardPeekHud(RenderGuiEvent.Post event) {
        CARD_PEEK_HUD.onHudRender(new GuiGraphics(event.getGuiGraphics()), event.getPartialTick());
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ModKeybinds.tick(net.minecraft.client.Minecraft.getInstance());
        CardArtManager.pumpQueue();
    }
}
