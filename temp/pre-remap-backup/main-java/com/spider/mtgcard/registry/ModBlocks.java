package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreBlock;
import com.spider.mtgcard.db.CardDatabaseBlock;
import com.spider.mtgcard.deckbox.DeckboxBlock;
import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.deckcontrol.DeckControlBlock;
import com.spider.mtgcard.displayblock.DisplayBlock;
import com.spider.mtgcard.displayblock.DisplayBlockItem;
import com.spider.mtgcard.graveyard.GraveyardBlock;
import com.spider.mtgcard.life.LifePointRegistry;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class ModBlocks {

    private static boolean inited = false;

    // ---- Blocks (assigned in init) ----
    public static Block DECKBOX;

    public static Block DECK_CONTROL_STONE;
    public static Block DECK_CONTROL_POLISHED_GRANITE;
    public static Block DECK_CONTROL_POLISHED_DIORITE;
    public static Block DECK_CONTROL_POLISHED_ANDESITE;
    public static Block DECK_CONTROL_POLISHED_TUFF;
    public static Block DECK_CONTROL_POLISHED_DEEPSLATE;
    public static Block DECK_CONTROL_POLISHED_BLACKSTONE;
    public static Block DECK_CONTROL_PRISMARINE;

    public static Block CARD_STORE;
    public static Block GRAVEYARD;
    public static Block DISPLAY_BLOCK;
    public static Block CARD_DB;

    public static Item DECKBOX_ITEM;

    // life block lives in its own registry (keep it there)
    public static final Block LIFE_POINT = LifePointRegistry.LIFE_POINT_BLOCK;

    private ModBlocks() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    public static void init() {
        if (inited) return;
        inited = true;

        // ---- Deckbox ----
        var deckbox = registerBlockWithItem(
                "deckbox",
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.WOOD)
                        .strength(2.5f)
                        .noOcclusion()
                        .isRedstoneConductor((s, w, p) -> false)
                        .pushReaction(PushReaction.DESTROY),
                DeckboxBlock::new,
                (block, itemSettings) -> new DeckboxBlockItem(block, itemSettings.stacksTo(1))
        );

        DECKBOX = deckbox.block;
        DECKBOX_ITEM = deckbox.item;

        // ---- Deck Control ----
        var dcSettings = BlockBehaviour.Properties.of()
                .strength(2.0f)
                .noOcclusion()
                .isRedstoneConductor((s, w, p) -> false)
                .isSuffocating((s, w, p) -> false)
                .isViewBlocking((s, w, p) -> false)
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> {
                    if (!state.getValue(DeckControlBlock.LIT)) return 0;
                    return switch (state.getValue(DeckControlBlock.COLOR)) {
                        case DEFAULT -> 14;
                        case SOULFIRE -> 10;
                        case REDSTONE -> 7;
                        case COPPER -> 14;
                    };
                });

        DECK_CONTROL_STONE = registerBlockItem("deck_control_stone", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_GRANITE = registerBlockItem("deck_control_polished_granite", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_DIORITE = registerBlockItem("deck_control_polished_diorite", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_ANDESITE = registerBlockItem("deck_control_polished_andesite", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_TUFF = registerBlockItem("deck_control_polished_tuff", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_DEEPSLATE = registerBlockItem("deck_control_polished_deepslate", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_BLACKSTONE = registerBlockItem("deck_control_polished_blackstone", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_PRISMARINE = registerBlockItem("deck_control_prismarine", dcSettings, DeckControlBlock::new);

        // ---- Card Store ----
        CARD_STORE = registerBlockItem(
                "card_store",
                BlockBehaviour.Properties.of().strength(2.0f),
                CardStoreBlock::new
        );

        // ---- Graveyard ----
        GRAVEYARD = registerBlockItem(
                "graveyard",
                BlockBehaviour.Properties.of().strength(2.0f),
                GraveyardBlock::new
        );

        // ---- Display Block (custom item) ----
        DISPLAY_BLOCK = registerWithItem(
                "display_block",
                BlockBehaviour.Properties.of()
                        .strength(2.0f)
                        .pushReaction(PushReaction.BLOCK),
                DisplayBlock::new,
                (block, itemSettings) -> new DisplayBlockItem(block, itemSettings)
        );

        // ---- Card Database ----
        CARD_DB = registerBlockItem(
                "card_database",
                BlockBehaviour.Properties.of()
                        .strength(3.5f)
                        .requiresCorrectToolForDrops(),
                CardDatabaseBlock::new
        );
    }

    // ---------------- helpers ----------------

    private static <T extends Block> T registerBlockItem(
            String path,
            BlockBehaviour.Properties base,
            Function<BlockBehaviour.Properties, T> ctor
    ) {
        return registerWithItem(path, base, ctor, BlockItem::new);
    }

    private static <T extends Block> T registerWithItem(
            String path,
            BlockBehaviour.Properties base,
            Function<BlockBehaviour.Properties, T> ctor,
            BiFunction<Block, net.minecraft.world.item.Item.Properties, Item> itemFactory
    ) {
        Identifier id = id(path);

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);

        // IMPORTANT in 1.21.x: settings must carry the registry key
        BlockBehaviour.Properties keyedSettings = base.setId(blockKey);

        T block = ctor.apply(keyedSettings);
        Registry.register(BuiltInRegistries.BLOCK, id, block);

        net.minecraft.world.item.Item.Properties itemSettings = new net.minecraft.world.item.Item.Properties().setId(itemKey);
        Registry.register(BuiltInRegistries.ITEM, id, itemFactory.apply(block, itemSettings));

        return block;
    }
    private static final class BlockAndItem<T extends Block> {
        final T block;
        final Item item;
        BlockAndItem(T block, Item item) { this.block = block; this.item = item; }
    }

    private static <T extends Block> BlockAndItem<T> registerBlockWithItem(
            String path,
            BlockBehaviour.Properties base,
            Function<BlockBehaviour.Properties, T> ctor,
            BiFunction<Block, net.minecraft.world.item.Item.Properties, Item> itemFactory
    ) {
        Identifier id = id(path);

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        ResourceKey<Item> itemKey   = ResourceKey.create(Registries.ITEM, id);

        T block = ctor.apply(base.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, id, block);

        Item item = itemFactory.apply(block, new net.minecraft.world.item.Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, id, item);

        return new BlockAndItem<>(block, item);
    }
}
