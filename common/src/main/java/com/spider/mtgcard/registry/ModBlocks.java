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
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ModBlocks {

    private static boolean inited = false;
    private static boolean poplarDeckboxEnabled = false;
    private static final List<String> DECKBOX_REGISTRY_PATHS = List.of(
            "deckbox",
            "oak_deckbox",
            "birch_deckbox",
            "jungle_deckbox",
            "acacia_deckbox",
            "dark_oak_deckbox",
            "mangrove_deckbox",
            "cherry_deckbox",
            "pale_oak_deckbox",
            "bamboo_deckbox",
            "crimson_deckbox",
            "warped_deckbox"
    );
    private static final List<Block> DECKBOX_BLOCKS = new ArrayList<>();
    private static final List<Item> DECKBOX_ITEMS = new ArrayList<>();
    private static final Map<String, Block> REGISTERED_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Item> REGISTERED_BLOCK_ITEMS = new LinkedHashMap<>();

    // ---- Blocks (assigned in init) ----
    public static Block DECKBOX;

    public static Block DECK_CONTROL_STONE;
    public static Block DECK_CONTROL_POLISHED_GRANITE;
    public static Block DECK_CONTROL_POLISHED_DIORITE;
    public static Block DECK_CONTROL_POLISHED_ANDESITE;
    public static Block DECK_CONTROL_POLISHED_TUFF;
    public static Block DECK_CONTROL_POLISHED_SULFUR;
    public static Block DECK_CONTROL_POLISHED_CINNABAR;
    public static Block DECK_CONTROL_POLISHED_DEEPSLATE;
    public static Block DECK_CONTROL_POLISHED_BLACKSTONE;
    public static Block DECK_CONTROL_PRISMARINE;
    public static Block DECK_CONTROL_CUT_SANDSTONE;
    public static Block DECK_CONTROL_CUT_RED_SANDSTONE;

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

    public static List<Block> getDeckboxBlocks() {
        return List.copyOf(DECKBOX_BLOCKS);
    }

    public static List<Item> getDeckboxItems() {
        return List.copyOf(DECKBOX_ITEMS);
    }

    public static Map<String, Block> getRegisteredBlocks() {
        return Map.copyOf(REGISTERED_BLOCKS);
    }

    public static Map<String, Item> getRegisteredBlockItems() {
        return Map.copyOf(REGISTERED_BLOCK_ITEMS);
    }

    public static boolean isDeckbox(BlockState state) {
        return state != null && state.getBlock() instanceof DeckboxBlock;
    }

    public static boolean isDeckbox(ItemStack stack) {
        return stack != null && stack.getItem() instanceof DeckboxBlockItem;
    }

    public static void enablePoplarDeckboxIfAvailable() {
        if (!inited && hasVanillaBlock("poplar_planks")) {
            poplarDeckboxEnabled = true;
        }
    }

    public static void init() {
        if (inited) return;
        inited = true;

        // ---- Deckbox ----
        List<String> deckboxPaths = new ArrayList<>(DECKBOX_REGISTRY_PATHS);
        if (poplarDeckboxEnabled) {
            deckboxPaths.add(deckboxPaths.indexOf("bamboo_deckbox"), "poplar_deckbox");
        }

        for (String path : deckboxPaths) {
            var deckbox = registerBlockWithItem(
                    path,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, w, p) -> false)
                            .pushReaction(pushReaction("POPPED", "DESTROY")),
                    DeckboxBlock::new,
                    (block, itemSettings) -> new DeckboxBlockItem(block, itemSettings.stacksTo(1))
            );

            if ("deckbox".equals(path)) {
                DECKBOX = deckbox.block;
                DECKBOX_ITEM = deckbox.item;
            }

            DECKBOX_BLOCKS.add(deckbox.block);
            DECKBOX_ITEMS.add(deckbox.item);
        }

        // ---- Deck Control ----
        var dcSettings = BlockBehaviour.Properties.of()
                .strength(2.0f)
                .noOcclusion()
                .isRedstoneConductor((s, w, p) -> false)
                .isSuffocating((s, w, p) -> false)
                .pushReaction(pushReaction("POPPED", "DESTROY"))
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
        DECK_CONTROL_POLISHED_SULFUR = registerBlockItem("deck_control_polished_sulfur", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_CINNABAR = registerBlockItem("deck_control_polished_cinnabar", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_DEEPSLATE = registerBlockItem("deck_control_polished_deepslate", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_POLISHED_BLACKSTONE = registerBlockItem("deck_control_polished_blackstone", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_PRISMARINE = registerBlockItem("deck_control_prismarine", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_CUT_SANDSTONE = registerBlockItem("deck_control_cut_sandstone", dcSettings, DeckControlBlock::new);
        DECK_CONTROL_CUT_RED_SANDSTONE = registerBlockItem("deck_control_cut_red_sandstone", dcSettings, DeckControlBlock::new);

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
                        .pushReaction(pushReaction("IMMOVEABLE", "BLOCK")),
                DisplayBlock::new,
                (block, itemSettings) -> new DisplayBlockItem(block, itemSettings)
        );

        // ---- Card Database ----
        CARD_DB = registerBlockItem(
                "card_database",
                BlockBehaviour.Properties.of()
                        .strength(3.5f),
                CardDatabaseBlock::new
        );
    }

    // ---------------- helpers ----------------

    private static PushReaction pushReaction(String modernName, String legacyName) {
        for (String name : new String[] { modernName, legacyName }) {
            try {
                return PushReaction.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return PushReaction.values()[0];
    }

    private static boolean hasVanillaBlock(String path) {
        return BuiltInRegistries.BLOCK.containsKey(Identifier.fromNamespaceAndPath("minecraft", path));
    }

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

        net.minecraft.world.item.Item.Properties itemSettings = new net.minecraft.world.item.Item.Properties().setId(itemKey);
        Item item = itemFactory.apply(block, itemSettings);
        REGISTERED_BLOCKS.put(path, block);
        REGISTERED_BLOCK_ITEMS.put(path, item);

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

        Item item = itemFactory.apply(block, new net.minecraft.world.item.Item.Properties().setId(itemKey));
        REGISTERED_BLOCKS.put(path, block);
        REGISTERED_BLOCK_ITEMS.put(path, item);

        return new BlockAndItem<>(block, item);
    }
}
