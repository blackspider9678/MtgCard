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
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

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
        return Identifier.of(Mtgcard.MOD_ID, path);
    }

    public static void init() {
        if (inited) return;
        inited = true;

        // ---- Deckbox ----
        var deckbox = registerBlockWithItem(
                "deckbox",
                AbstractBlock.Settings.create()
                        .mapColor(MapColor.OAK_TAN)
                        .strength(2.5f)
                        .nonOpaque()
                        .solidBlock((s, w, p) -> false)
                        .pistonBehavior(PistonBehavior.DESTROY),
                DeckboxBlock::new,
                (block, itemSettings) -> new DeckboxBlockItem(block, itemSettings.maxCount(1))
        );

        DECKBOX = deckbox.block;
        DECKBOX_ITEM = deckbox.item;

        // ---- Deck Control ----
        var dcSettings = AbstractBlock.Settings.create()
                .strength(2.0f)
                .nonOpaque()
                .solidBlock((s, w, p) -> false)
                .suffocates((s, w, p) -> false)
                .blockVision((s, w, p) -> false)
                .pistonBehavior(PistonBehavior.DESTROY)
                .luminance(state -> {
                    if (!state.get(DeckControlBlock.LIT)) return 0;
                    return switch (state.get(DeckControlBlock.COLOR)) {
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
                AbstractBlock.Settings.create().strength(2.0f),
                CardStoreBlock::new
        );

        // ---- Graveyard ----
        GRAVEYARD = registerBlockItem(
                "graveyard",
                AbstractBlock.Settings.create().strength(2.0f),
                GraveyardBlock::new
        );

        // ---- Display Block (custom item) ----
        DISPLAY_BLOCK = registerWithItem(
                "display_block",
                AbstractBlock.Settings.create()
                        .strength(2.0f)
                        .pistonBehavior(PistonBehavior.BLOCK),
                DisplayBlock::new,
                (block, itemSettings) -> new DisplayBlockItem(block, itemSettings)
        );

        // ---- Card Database ----
        CARD_DB = registerBlockItem(
                "card_database",
                AbstractBlock.Settings.create()
                        .strength(3.5f)
                        .requiresTool(),
                CardDatabaseBlock::new
        );
    }

    // ---------------- helpers ----------------

    private static <T extends Block> T registerBlockItem(
            String path,
            AbstractBlock.Settings base,
            Function<AbstractBlock.Settings, T> ctor
    ) {
        return registerWithItem(path, base, ctor, BlockItem::new);
    }

    private static <T extends Block> T registerWithItem(
            String path,
            AbstractBlock.Settings base,
            Function<AbstractBlock.Settings, T> ctor,
            BiFunction<Block, Item.Settings, Item> itemFactory
    ) {
        Identifier id = id(path);

        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);

        // IMPORTANT in 1.21.x: settings must carry the registry key
        AbstractBlock.Settings keyedSettings = base.registryKey(blockKey);

        T block = ctor.apply(keyedSettings);
        Registry.register(Registries.BLOCK, id, block);

        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey);
        Registry.register(Registries.ITEM, id, itemFactory.apply(block, itemSettings));

        return block;
    }
    private static final class BlockAndItem<T extends Block> {
        final T block;
        final Item item;
        BlockAndItem(T block, Item item) { this.block = block; this.item = item; }
    }

    private static <T extends Block> BlockAndItem<T> registerBlockWithItem(
            String path,
            AbstractBlock.Settings base,
            Function<AbstractBlock.Settings, T> ctor,
            BiFunction<Block, Item.Settings, Item> itemFactory
    ) {
        Identifier id = id(path);

        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        RegistryKey<Item> itemKey   = RegistryKey.of(RegistryKeys.ITEM, id);

        T block = ctor.apply(base.registryKey(blockKey));
        Registry.register(Registries.BLOCK, id, block);

        Item item = itemFactory.apply(block, new Item.Settings().registryKey(itemKey));
        Registry.register(Registries.ITEM, id, item);

        return new BlockAndItem<>(block, item);
    }
}
