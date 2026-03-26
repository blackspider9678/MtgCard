package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.content.pack.PackItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

public final class ModItems {

    // Public handles (force-load by calling ModItems.initialize() in your mod init)
    public static final Item CARD     = register("card",     CardItem::new, new Item.Properties());
    public static final Item MTG_PACK = register("mtg_pack", PackItem::new, new Item.Properties());
    // Dice
    public static final Item D4_DICE   = register("d4_dice", s -> new DiceItem(s, 4),   new Item.Properties().stacksTo(16));
    public static final Item D6_DICE   = register("d6_dice", s -> new DiceItem(s, 6),   new Item.Properties().stacksTo(16));
    public static final Item D8_DICE   = register("d8_dice", s -> new DiceItem(s, 8),   new Item.Properties().stacksTo(16));
    public static final Item D10_DICE  = register("d10_dice", s -> new DiceItem(s, 10),  new Item.Properties().stacksTo(16));
    public static final Item D12_DICE  = register("d12_dice", s -> new DiceItem(s, 12),  new Item.Properties().stacksTo(16));
    public static final Item D20_DICE  = register("d20_dice", s -> new DiceItem(s, 20),  new Item.Properties().stacksTo(16));
    public static final Item D100_DICE = register("d100_dice", s -> new DiceItem(s, 100), new Item.Properties().stacksTo(16));

    public static final Item GUIDEBOOK = register("guidebook", GuideBookItem::new, new Item.Properties().stacksTo(1));
    /**
     * 1.21.10: Item.Settings must carry its RegistryKey before the item is constructed.
     */
    public static Item register(String path, Function<Item.Properties, Item> itemFactory, Item.Properties settings) {
        Identifier id = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);

        // Bake the key into settings first, then construct the item
        Item item = itemFactory.apply(settings.setId(key));

        // Register using the same key/id
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private ModItems() {}

    /** Call once from your main init to ensure static fields load in a controlled order. */
    public static void initialize() {
        // no-op; touching this ensures <clinit> runs
    }
}
