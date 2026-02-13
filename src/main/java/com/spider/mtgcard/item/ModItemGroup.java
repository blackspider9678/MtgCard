package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroup {
    public static ItemGroup GROUP;

    public static void register() {
        GROUP = Registry.register(
                Registries.ITEM_GROUP,
                Identifier.of(Mtgcard.MOD_ID, "main"),
                FabricItemGroup.builder()
                        // SAFE ICON: don’t depend on your static fields here
                        .icon(() -> new ItemStack(ModItems.CARD))
                        .displayName(Text.translatable("itemGroup.mtgcard"))
                        .entries((ctx, entries) -> {
                            // Resolve from the registry to avoid nulls
                            var pack                    = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "mtg_pack"));
                            var card                    = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "card"));
                            var card_database           = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "card_database"));
                            var life_point              = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "life_point"));
                            var deckbox                 = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deckbox"));
                            var graveyard               = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "graveyard"));
                            var card_store              = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "card_store"));
                            var display_block           = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "display_block"));

                            // Deck control
                            var dc_stone                = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_stone"));
                            var dc_polished_granite     = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_granite"));
                            var dc_polished_diorite     = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_diorite"));
                            var dc_polished_andesite    = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_andesite"));
                            var dc_polished_tuff        = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_tuff"));
                            var dc_polished_deepslate   = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_deepslate"));
                            var dc_polished_blackstone  = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_polished_blackstone"));
                            var dc_prismarine           = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "deck_control_prismarine"));

                            // Dice
                            var d4_dice                 = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d4_dice"));
                            var d6_dice                 = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d6_dice"));
                            var d8_dice                 = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d8_dice"));
                            var d10_dice                = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d10_dice"));
                            var d12_dice                = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d12_dice"));
                            var d20_dice                = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d20_dice"));
                            var d100_dice               = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "d100_dice"));

                            var guidebook               = Registries.ITEM.get(Identifier.of(Mtgcard.MOD_ID, "guidebook"));


                            // Items
                            if (pack                    != Items.AIR) entries.add(new ItemStack(pack));
                            if (card                    != Items.AIR) entries.add(new ItemStack(card));
                            if (card_database           != Items.AIR) entries.add(new ItemStack(card_database));
                            if (life_point              != Items.AIR) entries.add(new ItemStack(life_point));
                            if (deckbox                 != Items.AIR) entries.add(new ItemStack(deckbox));
                            if (graveyard               != Items.AIR) entries.add(new ItemStack(graveyard));
                            if (card_store              != Items.AIR) entries.add(new ItemStack(card_store));
                            if (display_block           != Items.AIR) entries.add(new ItemStack(display_block));

                            // Deck controls
                            if (dc_stone                != Items.AIR) entries.add(new ItemStack(dc_stone));
                            if (dc_polished_granite     != Items.AIR) entries.add(new ItemStack(dc_polished_granite));
                            if (dc_polished_diorite     != Items.AIR) entries.add(new ItemStack(dc_polished_diorite));
                            if (dc_polished_andesite    != Items.AIR) entries.add(new ItemStack(dc_polished_andesite));
                            if (dc_polished_tuff        != Items.AIR) entries.add(new ItemStack(dc_polished_tuff));
                            if (dc_polished_deepslate   != Items.AIR) entries.add(new ItemStack(dc_polished_deepslate));
                            if (dc_polished_blackstone  != Items.AIR) entries.add(new ItemStack(dc_polished_blackstone));
                            if (dc_prismarine           != Items.AIR) entries.add(new ItemStack(dc_prismarine));

                            // Dice
                            if (d4_dice                 != Items.AIR) entries.add(new ItemStack(d4_dice));
                            if (d6_dice                 != Items.AIR) entries.add(new ItemStack(d6_dice));
                            if (d8_dice                 != Items.AIR) entries.add(new ItemStack(d8_dice));
                            if (d10_dice                != Items.AIR) entries.add(new ItemStack(d10_dice));
                            if (d12_dice                != Items.AIR) entries.add(new ItemStack(d12_dice));
                            if (d20_dice                != Items.AIR) entries.add(new ItemStack(d20_dice));
                            if (d100_dice               != Items.AIR) entries.add(new ItemStack(d100_dice));

                            // Guidebook
                            if (guidebook               != Items.AIR) entries.add(new ItemStack(guidebook));
                        })

                        .build()
        );
    }

    private ModItemGroup() {}
}
