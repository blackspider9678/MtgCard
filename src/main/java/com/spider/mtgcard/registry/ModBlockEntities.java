package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreBlockEntity;
import com.spider.mtgcard.db.CardDatabaseBlockEntity;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.life.LifePointRegistry;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

public final class ModBlockEntities {

    private static boolean inited = false;

    public static BlockEntityType<DeckboxBlockEntity> DECKBOX;
    public static BlockEntityType<DeckControlBlockEntity> DECK_CONTROL;
    public static BlockEntityType<CardStoreBlockEntity> CARD_STORE;
    public static BlockEntityType<GraveyardBlockEntity> GRAVEYARD;
    public static BlockEntityType<DisplayBlockEntity> DISPLAY_BLOCK;
    public static BlockEntityType<CardDatabaseBlockEntity> CARD_DB;

    public static final BlockEntityType<com.spider.mtgcard.life.LifePointBlockEntity> LIFE_POINT =
            LifePointRegistry.LIFE_POINT_BE;

    private ModBlockEntities() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    public static void init() {
        if (inited) return;
        inited = true;

        // Must have blocks registered first
        // (so call ModBlocks.init() BEFORE ModBlockEntities.init())

        DECKBOX = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("deckbox"),
                FabricBlockEntityTypeBuilder.create(
                        DeckboxBlockEntity::new,
                        ModBlocks.getDeckboxBlocks().toArray(Block[]::new)
                ).build()
        );

        DECK_CONTROL = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("deck_control"),
                FabricBlockEntityTypeBuilder.create(
                        DeckControlBlockEntity::new,
                        ModBlocks.DECK_CONTROL_STONE,
                        ModBlocks.DECK_CONTROL_POLISHED_GRANITE,
                        ModBlocks.DECK_CONTROL_POLISHED_DIORITE,
                        ModBlocks.DECK_CONTROL_POLISHED_ANDESITE,
                        ModBlocks.DECK_CONTROL_POLISHED_TUFF,
                        ModBlocks.DECK_CONTROL_POLISHED_DEEPSLATE,
                        ModBlocks.DECK_CONTROL_POLISHED_BLACKSTONE,
                        ModBlocks.DECK_CONTROL_PRISMARINE
                ).build()
        );

        CARD_STORE = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("card_store"),
                FabricBlockEntityTypeBuilder.create(CardStoreBlockEntity::new, ModBlocks.CARD_STORE).build()
        );

        GRAVEYARD = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("graveyard_be"),
                FabricBlockEntityTypeBuilder.create(GraveyardBlockEntity::new, ModBlocks.GRAVEYARD).build()
        );

        DISPLAY_BLOCK = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("display_block"),
                FabricBlockEntityTypeBuilder.create(DisplayBlockEntity::new, ModBlocks.DISPLAY_BLOCK).build()
        );

        CARD_DB = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("card_database_be"),
                FabricBlockEntityTypeBuilder.create(CardDatabaseBlockEntity::new, ModBlocks.CARD_DB).build()
        );
    }
}
