package com.spider.mtgcard.screen;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModScreenHandlers {

    private static boolean registered = false;

    public static MenuType<DeckboxScreenHandler> DECKBOX;
    public static ExtendedScreenHandlerType<DeckControlScreenHandler, BlockPos> DECKCONTROL;
    public static MenuType<GraveyardScreenHandler> GRAVEYARD;
    public static ExtendedScreenHandlerType<CardStoreScreenHandler, CardStoreScreenHandler.OpenData> CARD_STORE;
    public static ExtendedScreenHandlerType<CardDatabaseScreenHandler, BlockPos> CARD_DB;

    public static void register() {
        if (registered) return;
        registered = true;

        DECKBOX = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deckbox"),
                new MenuType<>(DeckboxScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        DECKCONTROL = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control"),
                new ExtendedScreenHandlerType<>(DeckControlScreenHandler::new, BlockPos.STREAM_CODEC)
        );

        GRAVEYARD = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "graveyard"),
                new MenuType<>(GraveyardScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        CARD_STORE = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_store"),
                new ExtendedScreenHandlerType<>(CardStoreScreenHandler::new, CardStoreScreenHandler.OpenData.STREAM_CODEC)
        );

        CARD_DB = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_database_sh"),
                new ExtendedScreenHandlerType<>(CardDatabaseScreenHandler::new, BlockPos.STREAM_CODEC)
        );
    }

    private ModScreenHandlers() {}
}
