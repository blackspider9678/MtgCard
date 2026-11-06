package com.spider.mtgcard.client;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.client.deckcontrol.DeckControlEntityRenderer;
import com.spider.mtgcard.client.display.CardDisplayEntityRenderer;
import com.spider.mtgcard.client.displayblock.DisplayBlockEntityRenderer;
import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.client.gui.CardStoreScreen;
import com.spider.mtgcard.client.gui.CustomImportScreen;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MtgcardNeoForgeClient {
    private static GLFWDropCallback dropCallback;
    private static long dropCallbackWindow;

    private MtgcardNeoForgeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MtgcardNeoForgeClient::onClientSetup);
        modBus.addListener(ModKeybinds::register);
        modBus.addListener(MtgcardNeoForgeClient::registerMenuScreens);
        modBus.addListener(MtgcardNeoForgeClient::registerRenderers);
        modBus.addListener(MtgcardNeoForgeClient::registerDiceExtensions);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForgeClient::addLoomButtons);
        NeoForge.EVENT_BUS.addListener(CardPeekHud::render);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> {
            CardArtManager.clearRequestState();
            CardArtManager.clearMemoryTextures();
        });
    }

    private static void addLoomButtons(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.LoomScreen)) return;
        int left = (screen.width-176)/2, top=(screen.height-166)/2;
        int x=Math.min(screen.width-98,left+180), y=Math.max(4,top+4);
        event.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.translatable("screen.mtgcard.dice_customizer.button"),
            b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.spider.mtgcard.dice.DiceCustomizerPackets.OpenDiceCustomizerC2S())).bounds(x,y,94,20).build());
        event.addListener(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Card Sleeves"),
            b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.spider.mtgcard.sleeve.SleeveCustomizerPackets.Open())).bounds(x,y+22,94,20).build());
    }

    private static void registerDiceExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        var extension = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.spider.mtgcard.client.render.CustomizedDiceRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(renderer==null)renderer=new com.spider.mtgcard.client.render.CustomizedDiceRenderer();
                return renderer;
            }
        };
        for(var type:com.spider.mtgcard.dice.DiceType.values()) event.registerItem(extension,type.item());
        event.registerItem(extension,com.spider.mtgcard.item.ModItems.CARD);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ArtImageStorage.ensureWebpCodecsRegistered();
            CardArtManager.init();
            net.minecraft.client.renderer.item.ItemProperties.register(com.spider.mtgcard.item.ModItems.CARD,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mtgcard","sleeved_card"),
                (stack,level,entity,seed)->com.spider.mtgcard.api.CardSleeves.get(stack).isPresent()?1f:0f);

            for(var type:com.spider.mtgcard.dice.DiceType.values())
                net.minecraft.client.renderer.item.ItemProperties.register(type.item(), net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mtgcard", "customized_dice"),
                    (stack,level,entity,seed)->stack.has(com.spider.mtgcard.data.ModDataComponents.DICE_APPEARANCE)?1f:0f);
        });
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModScreenHandlers.DICE_CUSTOMIZER_HOLDER.get(), com.spider.mtgcard.client.gui.DiceCustomizerScreen::new);
        event.register(ModScreenHandlers.SLEEVE_CUSTOMIZER_HOLDER.get(), com.spider.mtgcard.client.gui.SleeveCustomizerScreen::new);

        event.register(ModScreenHandlers.DECKBOX_HOLDER.get(), DeckboxScreen::new);
        event.register(ModScreenHandlers.CARD_DB_HOLDER.get(), CardDatabaseScreen::new);
        event.register(ModScreenHandlers.DECKCONTROL_HOLDER.get(), DeckControlScreen::new);
        event.register(ModScreenHandlers.GRAVEYARD_HOLDER.get(), GraveyardScreen::new);
        event.register(ModScreenHandlers.CARD_STORE_HOLDER.get(), CardStoreScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.SHULKER,
                com.spider.mtgcard.client.life.LifePointMarkerShulkerRenderer::new);
        event.registerEntityRenderer(ModEntities.CARD_DISPLAY_HOLDER.get(), CardDisplayEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISPLAY_BLOCK_HOLDER.get(), DisplayBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CARD_DB_HOLDER.get(), CardDatabaseBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DECK_CONTROL_HOLDER.get(), DeckControlEntityRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LIFE_POINT_HOLDER.get(), LifePointFrontTextRenderer::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        installDropCallback(client);
        ModKeybinds.tick(client);
        CardArtManager.pumpQueue();
    }

    private static void installDropCallback(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            return;
        }

        long handle = client.getWindow().getWindow();
        if (handle == 0L || dropCallbackWindow == handle) {
            return;
        }

        if (dropCallback != null) {
            dropCallback.free();
        }

        dropCallback = GLFWDropCallback.create((window, count, names) -> {
            List<Path> paths = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String name = GLFWDropCallback.getName(names, i);
                if (name != null && !name.isBlank()) {
                    paths.add(Path.of(name));
                }
            }
            if (paths.isEmpty()) {
                return;
            }

            client.execute(() -> {
                Screen screen = client.screen;
                if (screen instanceof CustomImportScreen customImportScreen) {
                    customImportScreen.handleFileDrop(paths);
                } else if (screen != null) {
                    screen.onFilesDrop(paths);
                }
            });
        });

        GLFW.glfwSetDropCallback(handle, dropCallback);
        dropCallbackWindow = handle;
    }
}
