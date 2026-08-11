package com.spider.mtgcard.neoforge;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.api.CardItemRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.item.ModItemGroup;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.life.LifePointRegistry;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.registry.ModParticles;
import com.spider.mtgcard.screen.ModScreenHandlers;
import com.spider.mtgcard.trade.ModLootFunctions;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class NeoForgeRegistries {
    private NeoForgeRegistries() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(NeoForgeRegistries::registerEntries);
    }

    private static void registerEntries(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) {
            event.register(
                    Registries.DATA_COMPONENT_TYPE,
                    ModDataComponents.CARD_ART_ID_ID,
                    () -> ModDataComponents.configureCardArtId(DataComponentType.<String>builder()).build()
            );
            event.register(
                    Registries.DATA_COMPONENT_TYPE,
                    ModDataComponents.DICE_APPEARANCE_ID,
                    () -> ModDataComponents.DICE_APPEARANCE
            );
            return;
        }

        if (event.getRegistryKey().equals(Registries.LOOT_FUNCTION_TYPE)) {
            event.register(
                    Registries.LOOT_FUNCTION_TYPE,
                    ModLootFunctions.RANDOMIZE_DICE_APPEARANCE_ID,
                    () -> ModLootFunctions.RANDOMIZE_DICE_APPEARANCE
            );
            return;
        }

        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            ModBlocks.init();
            event.register(Registries.BLOCK, id("life_point"), () -> LifePointRegistry.LIFE_POINT_BLOCK);
            for (var entry : ModBlocks.getRegisteredBlocks().entrySet()) {
                registerBlock(event, entry.getKey(), entry.getValue());
            }
            return;
        }

        if (event.getRegistryKey().equals(Registries.ITEM)) {
            ModItems.initialize();
            CardItemRegistry.register(TcgGameRegistry.MTG, ModItems.CARD);
            registerItem(event, "card", ModItems.CARD);
            registerItem(event, "mtg_pack", ModItems.MTG_PACK);
            registerItem(event, "d4_dice", ModItems.D4_DICE);
            registerItem(event, "d6_dice", ModItems.D6_DICE);
            registerItem(event, "d8_dice", ModItems.D8_DICE);
            registerItem(event, "d10_dice", ModItems.D10_DICE);
            registerItem(event, "d12_dice", ModItems.D12_DICE);
            registerItem(event, "d20_dice", ModItems.D20_DICE);
            registerItem(event, "d100_dice", ModItems.D100_DICE);
            registerItem(event, "guidebook", ModItems.GUIDEBOOK);
            registerItem(event, "life_point", LifePointRegistry.LIFE_POINT_ITEM);
            for (var entry : ModBlocks.getRegisteredBlockItems().entrySet()) {
                registerItem(event, entry.getKey(), entry.getValue());
            }
            return;
        }

        if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            ModBlockEntities.init();
            event.register(Registries.BLOCK_ENTITY_TYPE, id("deckbox"), () -> ModBlockEntities.DECKBOX);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("deck_control"), () -> ModBlockEntities.DECK_CONTROL);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("card_store"), () -> ModBlockEntities.CARD_STORE);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("graveyard_be"), () -> ModBlockEntities.GRAVEYARD);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("display_block"), () -> ModBlockEntities.DISPLAY_BLOCK);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("card_database_be"), () -> ModBlockEntities.CARD_DB);
            event.register(Registries.BLOCK_ENTITY_TYPE, id("life_point"), () -> LifePointRegistry.LIFE_POINT_BE);
            return;
        }

        if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            ModEntities.init();
            event.register(Registries.ENTITY_TYPE, ModEntities.CARD_DISPLAY_ID, () -> ModEntities.CARD_DISPLAY);
            return;
        }

        if (event.getRegistryKey().equals(Registries.MENU)) {
            ModScreenHandlers.register();
            event.register(Registries.MENU, id("deckbox"), () -> ModScreenHandlers.DECKBOX);
            event.register(Registries.MENU, id("deck_control"), () -> ModScreenHandlers.DECKCONTROL);
            event.register(Registries.MENU, id("graveyard"), () -> ModScreenHandlers.GRAVEYARD);
            event.register(Registries.MENU, id("card_store"), () -> ModScreenHandlers.CARD_STORE);
            event.register(Registries.MENU, id("card_database_sh"), () -> ModScreenHandlers.CARD_DB);
            event.register(Registries.MENU, id("dice_customizer"), () -> ModScreenHandlers.DICE_CUSTOMIZER);
            return;
        }

        if (event.getRegistryKey().equals(Registries.PARTICLE_TYPE)) {
            ModParticles.init();
            event.register(Registries.PARTICLE_TYPE, id("orbit_glyph"), () -> ModParticles.ORBIT_GLYPH);
            return;
        }

        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            ModItemGroup.register();
            event.register(Registries.CREATIVE_MODE_TAB, id("main"), () -> ModItemGroup.GROUP);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    private static void registerBlock(RegisterEvent event, String path, Block block) {
        event.register(Registries.BLOCK, id(path), () -> block);
    }

    private static void registerItem(RegisterEvent event, String path, Item item) {
        event.register(Registries.ITEM, id(path), () -> item);
    }
}
