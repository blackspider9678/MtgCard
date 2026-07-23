package com.spider.mtgcard.fabric;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.item.ModItemGroup;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.life.LifePointRegistry;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.registry.ModParticles;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class FabricRegistries {
    private static boolean registered;

    private FabricRegistries() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        registerDataComponents();
        registerBlocks();
        registerItems();
        registerBlockEntities();
        registerEntities();
        registerMenus();
        registerParticles();
        registerCreativeTabs();
    }

    private static void registerDataComponents() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ModDataComponents.CARD_ART_ID_ID,
                ModDataComponents.configureCardArtId(DataComponentType.<String>builder()).build()
        );
    }

    private static void registerBlocks() {
        ModBlocks.init();

        Registry.register(BuiltInRegistries.BLOCK, id("life_point"), LifePointRegistry.LIFE_POINT_BLOCK);
        for (var entry : ModBlocks.getRegisteredBlocks().entrySet()) {
            Registry.register(BuiltInRegistries.BLOCK, id(entry.getKey()), entry.getValue());
        }
    }

    private static void registerItems() {
        ModItems.initialize();

        Registry.register(BuiltInRegistries.ITEM, id("card"), ModItems.CARD);
        Registry.register(BuiltInRegistries.ITEM, id("mtg_pack"), ModItems.MTG_PACK);
        Registry.register(BuiltInRegistries.ITEM, id("d4_dice"), ModItems.D4_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d6_dice"), ModItems.D6_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d8_dice"), ModItems.D8_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d10_dice"), ModItems.D10_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d12_dice"), ModItems.D12_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d20_dice"), ModItems.D20_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("d100_dice"), ModItems.D100_DICE);
        Registry.register(BuiltInRegistries.ITEM, id("guidebook"), ModItems.GUIDEBOOK);
        Registry.register(BuiltInRegistries.ITEM, id("life_point"), LifePointRegistry.LIFE_POINT_ITEM);

        for (var entry : ModBlocks.getRegisteredBlockItems().entrySet()) {
            Registry.register(BuiltInRegistries.ITEM, id(entry.getKey()), entry.getValue());
        }
    }

    private static void registerBlockEntities() {
        ModBlockEntities.init();

        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("deckbox"), ModBlockEntities.DECKBOX);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("deck_control"), ModBlockEntities.DECK_CONTROL);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("card_store"), ModBlockEntities.CARD_STORE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("graveyard_be"), ModBlockEntities.GRAVEYARD);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("display_block"), ModBlockEntities.DISPLAY_BLOCK);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("card_database_be"), ModBlockEntities.CARD_DB);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("life_point"), LifePointRegistry.LIFE_POINT_BE);
    }

    private static void registerEntities() {
        ModEntities.init();
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ModEntities.CARD_DISPLAY_ID, ModEntities.CARD_DISPLAY);
    }

    private static void registerMenus() {
        ModScreenHandlers.register();

        Registry.register(BuiltInRegistries.MENU, id("deckbox"), ModScreenHandlers.DECKBOX);
        Registry.register(BuiltInRegistries.MENU, id("deck_control"), ModScreenHandlers.DECKCONTROL);
        Registry.register(BuiltInRegistries.MENU, id("graveyard"), ModScreenHandlers.GRAVEYARD);
        Registry.register(BuiltInRegistries.MENU, id("card_store"), ModScreenHandlers.CARD_STORE);
        Registry.register(BuiltInRegistries.MENU, id("card_database_sh"), ModScreenHandlers.CARD_DB);
    }

    private static void registerParticles() {
        ModParticles.init();
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("orbit_glyph"), ModParticles.ORBIT_GLYPH);
    }

    private static void registerCreativeTabs() {
        ModItemGroup.register();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), ModItemGroup.GROUP);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }
}
