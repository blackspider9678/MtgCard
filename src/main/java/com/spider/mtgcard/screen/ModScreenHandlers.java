package com.spider.mtgcard.screen;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ModScreenHandlers {

    private static boolean registered = false;

    public static ScreenHandlerType<DeckboxScreenHandler> DECKBOX;
    public static ExtendedScreenHandlerType<DeckControlScreenHandler, BlockPos> DECKCONTROL;
    public static ExtendedScreenHandlerType<GraveyardScreenHandler, BlockPos> GRAVEYARD;
    public static ExtendedScreenHandlerType<CardStoreScreenHandler, BlockPos> CARD_STORE;
    public static ExtendedScreenHandlerType<CardDatabaseScreenHandler, BlockPos> CARD_DB;


    public static void register() {
        if (registered) return;
        registered = true;

        DECKBOX = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Mtgcard.MOD_ID, "deckbox"),
                new ScreenHandlerType<>(DeckboxScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        DECKCONTROL = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Mtgcard.MOD_ID, "deck_control"),
                new ExtendedScreenHandlerType<>(DeckControlScreenHandler::new, BlockPos.PACKET_CODEC)
        );

        GRAVEYARD = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Mtgcard.MOD_ID, "graveyard"),
                new ExtendedScreenHandlerType<>(GraveyardScreenHandler::new, BlockPos.PACKET_CODEC)
        );

        CARD_STORE = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Mtgcard.MOD_ID, "card_store"),
                new ExtendedScreenHandlerType<>(CardStoreScreenHandler::new, BlockPos.PACKET_CODEC)
        );

        CARD_DB = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(Mtgcard.MOD_ID, "card_database_sh"),
                new ExtendedScreenHandlerType<>(CardDatabaseScreenHandler::new, BlockPos.PACKET_CODEC)
        );
    }

    private ModScreenHandlers() {}
}
