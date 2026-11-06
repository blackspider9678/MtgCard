package com.spider.mtgcard.screen;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreScreenHandler;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModScreenHandlers {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Mtgcard.MOD_ID);

    public static MenuType<com.spider.mtgcard.dice.DiceCustomizerScreenHandler> DICE_CUSTOMIZER;
    public static MenuType<com.spider.mtgcard.sleeve.SleeveCustomizerMenu> SLEEVE_CUSTOMIZER;
    public static final DeferredHolder<MenuType<?>, MenuType<com.spider.mtgcard.dice.DiceCustomizerScreenHandler>> DICE_CUSTOMIZER_HOLDER =
        MENUS.register("dice_customizer", () -> DICE_CUSTOMIZER = new MenuType<>(com.spider.mtgcard.dice.DiceCustomizerScreenHandler::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<com.spider.mtgcard.sleeve.SleeveCustomizerMenu>> SLEEVE_CUSTOMIZER_HOLDER =
        MENUS.register("sleeve_customizer", () -> SLEEVE_CUSTOMIZER = new MenuType<>(com.spider.mtgcard.sleeve.SleeveCustomizerMenu::new, FeatureFlags.VANILLA_SET));
    public static MenuType<DeckboxScreenHandler> DECKBOX;
    public static MenuType<DeckControlScreenHandler> DECKCONTROL;
    public static MenuType<GraveyardScreenHandler> GRAVEYARD;
    public static MenuType<CardStoreScreenHandler> CARD_STORE;
    public static MenuType<CardDatabaseScreenHandler> CARD_DB;

    public static final DeferredHolder<MenuType<?>, MenuType<DeckboxScreenHandler>> DECKBOX_HOLDER =
            MENUS.register("deckbox", () -> DECKBOX =
                    new MenuType<>(DeckboxScreenHandler::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<DeckControlScreenHandler>> DECKCONTROL_HOLDER =
            MENUS.register("deck_control", () -> DECKCONTROL =
                    new MenuType<>((IContainerFactory<DeckControlScreenHandler>)
                            (syncId, inv, buf) -> new DeckControlScreenHandler(syncId, inv, buf.readBlockPos()),
                            FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<GraveyardScreenHandler>> GRAVEYARD_HOLDER =
            MENUS.register("graveyard", () -> GRAVEYARD =
                    new MenuType<>((IContainerFactory<GraveyardScreenHandler>)
                            (syncId, inv, buf) -> new GraveyardScreenHandler(syncId, inv, buf.readBlockPos()),
                            FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<CardStoreScreenHandler>> CARD_STORE_HOLDER =
            MENUS.register("card_store", () -> CARD_STORE =
                    new MenuType<>((IContainerFactory<CardStoreScreenHandler>)
                            (syncId, inv, buf) -> new CardStoreScreenHandler(syncId, inv, CardStoreScreenHandler.OpenData.STREAM_CODEC.decode(buf)),
                            FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<CardDatabaseScreenHandler>> CARD_DB_HOLDER =
            MENUS.register("card_database_sh", () -> CARD_DB =
                    new MenuType<>((IContainerFactory<CardDatabaseScreenHandler>)
                            (syncId, inv, buf) -> new CardDatabaseScreenHandler(syncId, inv, buf.readBlockPos()),
                            FeatureFlags.VANILLA_SET));

    private ModScreenHandlers() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
