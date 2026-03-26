package com.spider.mtgcard.screen;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModScreenHandlers {

    private static boolean registered = false;

    public static MenuType<DeckboxScreenHandler> DECKBOX;
    public static MenuType<DeckControlScreenHandler> DECKCONTROL;
    public static MenuType<GraveyardScreenHandler> GRAVEYARD;
    public static MenuType<CardStoreScreenHandler> CARD_STORE;
    public static MenuType<CardDatabaseScreenHandler> CARD_DB;

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
                new MenuType<>(DeckControlScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        GRAVEYARD = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "graveyard"),
                new MenuType<>(GraveyardScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        CARD_STORE = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_store"),
                new MenuType<>(CardStoreScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        CARD_DB = Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_database_sh"),
                new MenuType<>(CardDatabaseScreenHandler::new, FeatureFlags.VANILLA_SET)
        );
    }

    private ModScreenHandlers() {}
}