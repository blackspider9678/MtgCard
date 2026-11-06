package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.content.pack.PackItem;
import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.displayblock.DisplayBlockItem;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Mtgcard.MOD_ID);
    private static final List<String> CREATIVE_TAB_ORDER = List.of(
            "mtg_pack",
            "card",
            "card_database",
            "life_point",
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
            "warped_deckbox",
            "graveyard",
            "card_store",
            "display_block",
            "deck_control_stone",
            "deck_control_polished_granite",
            "deck_control_polished_diorite",
            "deck_control_polished_andesite",
            "deck_control_polished_tuff",
            "deck_control_polished_deepslate",
            "deck_control_polished_blackstone",
            "deck_control_prismarine",
            "deck_control_cut_sandstone",
            "deck_control_cut_red_sandstone",
            "d4_dice",
            "d6_dice",
            "d8_dice",
            "d10_dice",
            "d12_dice",
            "d20_dice",
            "d100_dice",
            "guidebook"
    );

    public static Item CARD;
    public static Item MTG_PACK;
    public static Item D4_DICE;
    public static Item D6_DICE;
    public static Item D8_DICE;
    public static Item D10_DICE;
    public static Item D12_DICE;
    public static Item D20_DICE;
    public static Item D100_DICE;
    public static Item GUIDEBOOK;

    public static final DeferredItem<CardItem> CARD_HOLDER =
            register("card", CardItem::new, p -> p, item -> CARD = item);
    public static final DeferredItem<PackItem> MTG_PACK_HOLDER =
            register("mtg_pack", PackItem::new, p -> p, item -> MTG_PACK = item);
    public static final DeferredItem<DiceItem> D4_DICE_HOLDER =
            register("d4_dice", p -> new DiceItem(p, 4), p -> p.stacksTo(16), item -> D4_DICE = item);
    public static final DeferredItem<DiceItem> D6_DICE_HOLDER =
            register("d6_dice", p -> new DiceItem(p, 6), p -> p.stacksTo(16), item -> D6_DICE = item);
    public static final DeferredItem<DiceItem> D8_DICE_HOLDER =
            register("d8_dice", p -> new DiceItem(p, 8), p -> p.stacksTo(16), item -> D8_DICE = item);
    public static final DeferredItem<DiceItem> D10_DICE_HOLDER =
            register("d10_dice", p -> new DiceItem(p, 10), p -> p.stacksTo(16), item -> D10_DICE = item);
    public static final DeferredItem<DiceItem> D12_DICE_HOLDER =
            register("d12_dice", p -> new DiceItem(p, 12), p -> p.stacksTo(16), item -> D12_DICE = item);
    public static final DeferredItem<DiceItem> D20_DICE_HOLDER =
            register("d20_dice", p -> new DiceItem(p, 20), p -> p.stacksTo(16), item -> D20_DICE = item);
    public static final DeferredItem<DiceItem> D100_DICE_HOLDER =
            register("d100_dice", p -> new DiceItem(p, 100), p -> p.stacksTo(16), item -> D100_DICE = item);
    public static final DeferredItem<GuideBookItem> GUIDEBOOK_HOLDER =
            register("guidebook", GuideBookItem::new, p -> p.stacksTo(1), item -> GUIDEBOOK = item);

    static {
        for (int i = 0; i < ModBlocks.getDeckboxRegistryPaths().size(); i++) {
            String path = ModBlocks.getDeckboxRegistryPaths().get(i);
            int index = i;
            registerBlockItem(path,
                    () -> ModBlocks.getDeckboxBlockHolders().get(index).get(),
                    (block, properties) -> new DeckboxBlockItem(block, properties.stacksTo(1)),
                    item -> ModBlocks.addDeckboxItem(path, item));
        }

        registerSimpleBlockItem("deck_control_stone", ModBlocks.DECK_CONTROL_STONE_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_granite", ModBlocks.DECK_CONTROL_POLISHED_GRANITE_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_diorite", ModBlocks.DECK_CONTROL_POLISHED_DIORITE_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_andesite", ModBlocks.DECK_CONTROL_POLISHED_ANDESITE_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_tuff", ModBlocks.DECK_CONTROL_POLISHED_TUFF_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_deepslate", ModBlocks.DECK_CONTROL_POLISHED_DEEPSLATE_HOLDER::get);
        registerSimpleBlockItem("deck_control_polished_blackstone", ModBlocks.DECK_CONTROL_POLISHED_BLACKSTONE_HOLDER::get);
        registerSimpleBlockItem("deck_control_prismarine", ModBlocks.DECK_CONTROL_PRISMARINE_HOLDER::get);
        registerSimpleBlockItem("deck_control_cut_sandstone", ModBlocks.DECK_CONTROL_CUT_SANDSTONE_HOLDER::get);
        registerSimpleBlockItem("deck_control_cut_red_sandstone", ModBlocks.DECK_CONTROL_CUT_RED_SANDSTONE_HOLDER::get);

        registerSimpleBlockItem("card_store", ModBlocks.CARD_STORE_HOLDER::get);
        registerSimpleBlockItem("graveyard", ModBlocks.GRAVEYARD_HOLDER::get);
        registerBlockItem("display_block", ModBlocks.DISPLAY_BLOCK_HOLDER::get, DisplayBlockItem::new, item -> {});
        registerSimpleBlockItem("card_database", ModBlocks.CARD_DB_HOLDER::get);
        registerSimpleBlockItem("life_point", ModBlocks.LIFE_POINT_HOLDER::get);
    }

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static void initialize() {
    }

    public static List<String> getCreativeTabOrder() {
        return CREATIVE_TAB_ORDER;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM, id(path));
    }

    private static Item.Properties properties(String path) {
        return new Item.Properties();
    }

    private static <T extends Item> DeferredItem<T> register(
            String path,
            Function<Item.Properties, T> factory,
            Function<Item.Properties, Item.Properties> configure,
            java.util.function.Consumer<T> assign
    ) {
        return ITEMS.register(path, () -> {
            T item = factory.apply(configure.apply(properties(path)));
            assign.accept(item);
            return item;
        });
    }

    private static DeferredItem<BlockItem> registerSimpleBlockItem(String path, Supplier<? extends Block> block) {
        return registerBlockItem(path, block, BlockItem::new, item -> {});
    }

    private static <T extends Item> DeferredItem<T> registerBlockItem(
            String path,
            Supplier<? extends Block> block,
            BiFunction<Block, Item.Properties, T> factory,
            java.util.function.Consumer<T> assign
    ) {
        return ITEMS.register(path, () -> {
            T item = factory.apply(block.get(), properties(path));
            assign.accept(item);
            return item;
        });
    }
}
