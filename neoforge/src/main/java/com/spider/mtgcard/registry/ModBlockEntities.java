package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreBlockEntity;
import com.spider.mtgcard.db.CardDatabaseBlockEntity;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Mtgcard.MOD_ID);

    public static BlockEntityType<DeckboxBlockEntity> DECKBOX;
    public static BlockEntityType<DeckControlBlockEntity> DECK_CONTROL;
    public static BlockEntityType<CardStoreBlockEntity> CARD_STORE;
    public static BlockEntityType<GraveyardBlockEntity> GRAVEYARD;
    public static BlockEntityType<DisplayBlockEntity> DISPLAY_BLOCK;
    public static BlockEntityType<CardDatabaseBlockEntity> CARD_DB;
    public static BlockEntityType<LifePointBlockEntity> LIFE_POINT;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckboxBlockEntity>> DECKBOX_HOLDER =
            BLOCK_ENTITIES.register("deckbox", () -> assignDeckbox(BlockEntityType.Builder.of(
                    DeckboxBlockEntity::new,
                    ModBlocks.getDeckboxBlocks().toArray(Block[]::new)
            ).build(null)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckControlBlockEntity>> DECK_CONTROL_HOLDER =
            BLOCK_ENTITIES.register("deck_control", () -> DECK_CONTROL = BlockEntityType.Builder.of(
                    DeckControlBlockEntity::new,
                    ModBlocks.DECK_CONTROL_STONE,
                    ModBlocks.DECK_CONTROL_POLISHED_GRANITE,
                    ModBlocks.DECK_CONTROL_POLISHED_DIORITE,
                    ModBlocks.DECK_CONTROL_POLISHED_ANDESITE,
                    ModBlocks.DECK_CONTROL_POLISHED_TUFF,
                    ModBlocks.DECK_CONTROL_POLISHED_DEEPSLATE,
                    ModBlocks.DECK_CONTROL_POLISHED_BLACKSTONE,
                    ModBlocks.DECK_CONTROL_PRISMARINE,
                    ModBlocks.DECK_CONTROL_CUT_SANDSTONE,
                    ModBlocks.DECK_CONTROL_CUT_RED_SANDSTONE
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CardStoreBlockEntity>> CARD_STORE_HOLDER =
            BLOCK_ENTITIES.register("card_store", () -> CARD_STORE =
                    BlockEntityType.Builder.of(CardStoreBlockEntity::new, ModBlocks.CARD_STORE).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GraveyardBlockEntity>> GRAVEYARD_HOLDER =
            BLOCK_ENTITIES.register("graveyard_be", () -> GRAVEYARD =
                    BlockEntityType.Builder.of(GraveyardBlockEntity::new, ModBlocks.GRAVEYARD).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayBlockEntity>> DISPLAY_BLOCK_HOLDER =
            BLOCK_ENTITIES.register("display_block", () -> DISPLAY_BLOCK =
                    BlockEntityType.Builder.of(DisplayBlockEntity::new, ModBlocks.DISPLAY_BLOCK).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CardDatabaseBlockEntity>> CARD_DB_HOLDER =
            BLOCK_ENTITIES.register("card_database_be", () -> CARD_DB =
                    BlockEntityType.Builder.of(CardDatabaseBlockEntity::new, ModBlocks.CARD_DB).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LifePointBlockEntity>> LIFE_POINT_HOLDER =
            BLOCK_ENTITIES.register("life_point", () -> LIFE_POINT =
                    BlockEntityType.Builder.of(LifePointBlockEntity::new, ModBlocks.LIFE_POINT).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    private static BlockEntityType<DeckboxBlockEntity> assignDeckbox(BlockEntityType<DeckboxBlockEntity> type) {
        DECKBOX = type;
        return type;
    }
}
