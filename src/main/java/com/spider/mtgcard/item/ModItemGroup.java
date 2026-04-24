package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModItemGroup {
    public static CreativeModeTab GROUP;

    public static void register() {
        GROUP = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "main"),
                FabricCreativeModeTab.builder()
                        // SAFE ICON: don’t depend on your static fields here
                        .icon(() -> new ItemStack(ModItems.CARD))
                        .title(Component.translatable("itemGroup.mtgcard"))
                        .displayItems((ctx, entries) -> {
                            // Resolve from the registry to avoid nulls
                            var pack                    = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "mtg_pack"));
                            var card                    = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card"));
                            var card_database           = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_database"));
                            var life_point              = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "life_point"));
                            var deckbox                 = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deckbox"));
                            var graveyard               = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "graveyard"));
                            var card_store              = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_store"));
                            var display_block           = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "display_block"));

                            // Deck control
                            var dc_stone                = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_stone"));
                            var dc_polished_granite     = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_granite"));
                            var dc_polished_diorite     = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_diorite"));
                            var dc_polished_andesite    = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_andesite"));
                            var dc_polished_tuff        = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_tuff"));
                            var dc_polished_deepslate   = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_deepslate"));
                            var dc_polished_blackstone  = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_polished_blackstone"));
                            var dc_prismarine           = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_prismarine"));

                            // Dice
                            var d4_dice                 = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d4_dice"));
                            var d6_dice                 = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d6_dice"));
                            var d8_dice                 = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d8_dice"));
                            var d10_dice                = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d10_dice"));
                            var d12_dice                = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d12_dice"));
                            var d20_dice                = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d20_dice"));
                            var d100_dice               = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "d100_dice"));

                            var guidebook               = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guidebook"));


                            // Items
                            if (pack                    != Items.AIR) entries.accept(new ItemStack(pack));
                            if (card                    != Items.AIR) entries.accept(new ItemStack(card));
                            if (card_database           != Items.AIR) entries.accept(new ItemStack(card_database));
                            if (life_point              != Items.AIR) entries.accept(new ItemStack(life_point));
                            if (deckbox                 != Items.AIR) entries.accept(new ItemStack(deckbox));
                            if (graveyard               != Items.AIR) entries.accept(new ItemStack(graveyard));
                            if (card_store              != Items.AIR) entries.accept(new ItemStack(card_store));
                            if (display_block           != Items.AIR) entries.accept(new ItemStack(display_block));

                            // Deck controls
                            if (dc_stone                != Items.AIR) entries.accept(new ItemStack(dc_stone));
                            if (dc_polished_granite     != Items.AIR) entries.accept(new ItemStack(dc_polished_granite));
                            if (dc_polished_diorite     != Items.AIR) entries.accept(new ItemStack(dc_polished_diorite));
                            if (dc_polished_andesite    != Items.AIR) entries.accept(new ItemStack(dc_polished_andesite));
                            if (dc_polished_tuff        != Items.AIR) entries.accept(new ItemStack(dc_polished_tuff));
                            if (dc_polished_deepslate   != Items.AIR) entries.accept(new ItemStack(dc_polished_deepslate));
                            if (dc_polished_blackstone  != Items.AIR) entries.accept(new ItemStack(dc_polished_blackstone));
                            if (dc_prismarine           != Items.AIR) entries.accept(new ItemStack(dc_prismarine));

                            // Dice
                            if (d4_dice                 != Items.AIR) entries.accept(new ItemStack(d4_dice));
                            if (d6_dice                 != Items.AIR) entries.accept(new ItemStack(d6_dice));
                            if (d8_dice                 != Items.AIR) entries.accept(new ItemStack(d8_dice));
                            if (d10_dice                != Items.AIR) entries.accept(new ItemStack(d10_dice));
                            if (d12_dice                != Items.AIR) entries.accept(new ItemStack(d12_dice));
                            if (d20_dice                != Items.AIR) entries.accept(new ItemStack(d20_dice));
                            if (d100_dice               != Items.AIR) entries.accept(new ItemStack(d100_dice));

                            // Guidebook
                            if (guidebook               != Items.AIR) entries.accept(new ItemStack(guidebook));
                        })

                        .build()
        );
    }

    private ModItemGroup() {}
}
