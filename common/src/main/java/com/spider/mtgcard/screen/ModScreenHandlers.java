package com.spider.mtgcard.screen;

import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import com.spider.mtgcard.dice.DiceCustomizerScreenHandler;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import com.spider.mtgcard.sleeve.SleeveCustomizerMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModScreenHandlers {

    private static boolean registered = false;

    public static MenuType<DeckboxScreenHandler> DECKBOX;
    public static ExtendedMenuType<DeckControlScreenHandler, BlockPos> DECKCONTROL;
    public static ExtendedMenuType<GraveyardScreenHandler, BlockPos> GRAVEYARD;
    public static ExtendedMenuType<CardStoreScreenHandler, CardStoreScreenHandler.OpenData> CARD_STORE;
    public static ExtendedMenuType<CardDatabaseScreenHandler, BlockPos> CARD_DB;
    public static MenuType<DiceCustomizerScreenHandler> DICE_CUSTOMIZER;
    public static MenuType<SleeveCustomizerMenu> SLEEVE_CUSTOMIZER;

    public static void register() {
        if (registered) return;
        registered = true;

        DECKBOX = new MenuType<>(DeckboxScreenHandler::new, FeatureFlags.VANILLA_SET);

        DECKCONTROL = new ExtendedMenuType<>(DeckControlScreenHandler::new, BlockPos.STREAM_CODEC);

        GRAVEYARD = new ExtendedMenuType<>(GraveyardScreenHandler::new, BlockPos.STREAM_CODEC);

        CARD_STORE = new ExtendedMenuType<>(CardStoreScreenHandler::new, CardStoreScreenHandler.OpenData.STREAM_CODEC);

        CARD_DB = new ExtendedMenuType<>(CardDatabaseScreenHandler::new, BlockPos.STREAM_CODEC);

        DICE_CUSTOMIZER = new MenuType<>(DiceCustomizerScreenHandler::new, FeatureFlags.VANILLA_SET);
        SLEEVE_CUSTOMIZER = new MenuType<>(SleeveCustomizerMenu::new, FeatureFlags.VANILLA_SET);
    }

    private ModScreenHandlers() {}
}
