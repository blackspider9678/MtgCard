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
    private static final String BIOMESOPLENTY = "biomesoplenty";
    private static final String BIOMESWEVEGONE = "biomeswevegone";
    private static final List<DeckboxVariant> DECKBOX_REGISTRY_PATHS = List.of(
            vanillaDeckbox("deckbox"),
            vanillaDeckbox("oak_deckbox"),
            vanillaDeckbox("birch_deckbox"),
            vanillaDeckbox("jungle_deckbox"),
            vanillaDeckbox("acacia_deckbox"),
            vanillaDeckbox("dark_oak_deckbox"),
            vanillaDeckbox("mangrove_deckbox"),
            vanillaDeckbox("cherry_deckbox"),
            vanillaDeckbox("pale_oak_deckbox"),
            vanillaDeckbox("bamboo_deckbox"),
            vanillaDeckbox("crimson_deckbox"),
            vanillaDeckbox("warped_deckbox"),

            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_origin_oak_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_fir_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_pine_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_maple_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_redwood_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_mahogany_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_jacaranda_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_palm_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_willow_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_dead_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_magic_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_umbran_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_hellbark_deckbox"),
            optionalDeckbox(BIOMESOPLENTY, "biomesoplenty_empyreal_deckbox"),

            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_aspen_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_baobab_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_blue_enchanted_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_cika_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_cypress_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_ebony_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_fir_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_florus_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_green_enchanted_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_holly_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_ironwood_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_jacaranda_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_mahogany_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_maple_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_palm_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_pine_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_rainbow_eucalyptus_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_redwood_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_sakura_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_skyris_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_spirit_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_white_mangrove_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_willow_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_witch_hazel_deckbox"),
            optionalDeckbox(BIOMESWEVEGONE, "biomeswevegone_zelkova_deckbox")
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

    public static void init() {
        if (inited) return;
        inited = true;

        // ---- Deckbox ----
        for (DeckboxVariant variant : DECKBOX_REGISTRY_PATHS) {
            if (!variant.shouldRegister()) continue;

            String path = variant.path();
            var deckbox = registerBlockWithItem(
                    path,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, w, p) -> false)
                            .pushReaction(PushReaction.DESTROY),
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
                        .pushReaction(PushReaction.BLOCK),
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

    private static DeckboxVariant vanillaDeckbox(String path) {
        return new DeckboxVariant(path, null);
    }

    private static DeckboxVariant optionalDeckbox(String requiredModId, String path) {
        return new DeckboxVariant(path, requiredModId);
    }

    private record DeckboxVariant(String path, String requiredModId) {
        boolean shouldRegister() {
            return requiredModId == null || isModLoaded(requiredModId);
        }
    }

    private static boolean isModLoaded(String modId) {
        if (isFabricModLoaded(modId)) return true;
        return isNeoForgeModLoaded(modId);
    }

    private static boolean isFabricModLoaded(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (Boolean) loaderClass.getMethod("isModLoaded", String.class).invoke(loader, modId);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isNeoForgeModLoaded(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            return (Boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
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
