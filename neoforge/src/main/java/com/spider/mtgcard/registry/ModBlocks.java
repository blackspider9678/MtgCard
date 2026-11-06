package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreBlock;
import com.spider.mtgcard.db.CardDatabaseBlock;
import com.spider.mtgcard.deckbox.DeckboxBlock;
import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.deckcontrol.DeckControlBlock;
import com.spider.mtgcard.displayblock.DisplayBlock;
import com.spider.mtgcard.graveyard.GraveyardBlock;
import com.spider.mtgcard.life.LifePointBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Mtgcard.MOD_ID);

    private static final List<String> DECKBOX_REGISTRY_PATHS = List.of(
            "deckbox",
            "oak_deckbox",
            "birch_deckbox",
            "jungle_deckbox",
            "acacia_deckbox",
            "dark_oak_deckbox",
            "mangrove_deckbox",
            "cherry_deckbox",
            "bamboo_deckbox",
            "crimson_deckbox",
            "warped_deckbox"
    );
    private static final List<DeferredBlock<? extends Block>> DECKBOX_BLOCK_HOLDERS = new ArrayList<>();
    private static final List<Block> DECKBOX_BLOCKS = new ArrayList<>();
    private static final List<Item> DECKBOX_ITEMS = new ArrayList<>();

    public static Block DECKBOX;

    public static Block DECK_CONTROL_STONE;
    public static Block DECK_CONTROL_POLISHED_GRANITE;
    public static Block DECK_CONTROL_POLISHED_DIORITE;
    public static Block DECK_CONTROL_POLISHED_ANDESITE;
    public static Block DECK_CONTROL_POLISHED_TUFF;
    public static Block DECK_CONTROL_POLISHED_DEEPSLATE;
    public static Block DECK_CONTROL_POLISHED_BLACKSTONE;
    public static Block DECK_CONTROL_PRISMARINE;
    public static Block DECK_CONTROL_CUT_SANDSTONE;
    public static Block DECK_CONTROL_CUT_RED_SANDSTONE;

    public static Block CARD_STORE;
    public static Block GRAVEYARD;
    public static Block DISPLAY_BLOCK;
    public static Block CARD_DB;
    public static Block LIFE_POINT;

    public static Item DECKBOX_ITEM;

    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_STONE_HOLDER =
            register("deck_control_stone", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_STONE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_GRANITE_HOLDER =
            register("deck_control_polished_granite", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_GRANITE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_DIORITE_HOLDER =
            register("deck_control_polished_diorite", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_DIORITE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_ANDESITE_HOLDER =
            register("deck_control_polished_andesite", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_ANDESITE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_TUFF_HOLDER =
            register("deck_control_polished_tuff", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_TUFF = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_DEEPSLATE_HOLDER =
            register("deck_control_polished_deepslate", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_DEEPSLATE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_POLISHED_BLACKSTONE_HOLDER =
            register("deck_control_polished_blackstone", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_POLISHED_BLACKSTONE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_PRISMARINE_HOLDER =
            register("deck_control_prismarine", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_PRISMARINE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_CUT_SANDSTONE_HOLDER =
            register("deck_control_cut_sandstone", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_CUT_SANDSTONE = b);
    public static final DeferredBlock<DeckControlBlock> DECK_CONTROL_CUT_RED_SANDSTONE_HOLDER =
            register("deck_control_cut_red_sandstone", ModBlocks::deckControlProperties, DeckControlBlock::new, b -> DECK_CONTROL_CUT_RED_SANDSTONE = b);

    public static final DeferredBlock<CardStoreBlock> CARD_STORE_HOLDER =
            register("card_store", () -> BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), CardStoreBlock::new, b -> CARD_STORE = b);
    public static final DeferredBlock<GraveyardBlock> GRAVEYARD_HOLDER =
            register("graveyard", () -> BlockBehaviour.Properties.of().strength(2.0f).noOcclusion(), GraveyardBlock::new, b -> GRAVEYARD = b);
    public static final DeferredBlock<DisplayBlock> DISPLAY_BLOCK_HOLDER =
            register("display_block", () -> BlockBehaviour.Properties.of().strength(2.0f).pushReaction(PushReaction.BLOCK), DisplayBlock::new, b -> DISPLAY_BLOCK = b);
    public static final DeferredBlock<CardDatabaseBlock> CARD_DB_HOLDER =
            register("card_database", () -> BlockBehaviour.Properties.of().strength(3.5f).noOcclusion(), CardDatabaseBlock::new, b -> CARD_DB = b);
    public static final DeferredBlock<LifePointBlock> LIFE_POINT_HOLDER =
            register("life_point", () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.5f)
                    .noOcclusion()
                    .isRedstoneConductor((s, w, p) -> false), LifePointBlock::new, b -> LIFE_POINT = b);

    static {
        for (String path : DECKBOX_REGISTRY_PATHS) {
            DeferredBlock<DeckboxBlock> holder = register(path, ModBlocks::deckboxProperties, DeckboxBlock::new, block -> {
                if ("deckbox".equals(path)) {
                    DECKBOX = block;
                }
                DECKBOX_BLOCKS.add(block);
            });
            DECKBOX_BLOCK_HOLDERS.add(holder);
        }
    }

    private ModBlocks() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    public static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, id(path));
    }

    public static List<Block> getDeckboxBlocks() {
        return List.copyOf(DECKBOX_BLOCKS);
    }

    public static List<Item> getDeckboxItems() {
        return List.copyOf(DECKBOX_ITEMS);
    }

    public static List<String> getDeckboxRegistryPaths() {
        return DECKBOX_REGISTRY_PATHS;
    }

    public static List<DeferredBlock<? extends Block>> getDeckboxBlockHolders() {
        return List.copyOf(DECKBOX_BLOCK_HOLDERS);
    }

    public static boolean isDeckbox(BlockState state) {
        return state != null && state.getBlock() instanceof DeckboxBlock;
    }

    public static boolean isDeckbox(ItemStack stack) {
        return stack != null && stack.getItem() instanceof DeckboxBlockItem;
    }

    public static void addDeckboxItem(String path, Item item) {
        if ("deckbox".equals(path)) {
            DECKBOX_ITEM = item;
        }
        DECKBOX_ITEMS.add(item);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private static <T extends Block> DeferredBlock<T> register(
            String path,
            Supplier<BlockBehaviour.Properties> properties,
            Function<BlockBehaviour.Properties, T> factory,
            java.util.function.Consumer<T> assign
    ) {
        return BLOCKS.register(path, () -> {
            T block = factory.apply(properties.get());
            assign.accept(block);
            return block;
        });
    }

    private static BlockBehaviour.Properties deckboxProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .noOcclusion()
                .isRedstoneConductor((s, w, p) -> false)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties deckControlProperties() {
        return BlockBehaviour.Properties.of()
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
    }
}
