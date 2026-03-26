package com.spider.mtgcard.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * Builds MtG card ItemStacks that are compatible with CardMeta.read().
 *
 * This writes:
 * - root custom data (via StackData)
 * - mtg_meta compound (name, set, rarity, mana_value, type_line, oracle_text, etc.)
 * - root boolean mtg_foil
 */
public final class CardStackFactory {

    private CardStackFactory() {}

    /**
     * @param cardItem The item representing a card (your mod's card item).
     */
    public static ItemStack createCardStack(
            Item cardItem,
            String name,
            String setCode,
            String rarity,
            int manaValue,
            List<String> colors,
            List<String> colorIdentity,
            String typeLine,
            String oracleText,
            boolean foil,
            boolean tokenLike
    ) {
        ItemStack st = new ItemStack(cardItem, 1);

        // Read/modify your custom root data
        CompoundTag root = StackData.readCustom(st);

        // mtg_meta compound as expected by CardMeta.read()
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        meta.putString("name", name);
        meta.putString("set", setCode);
        if (rarity != null && !rarity.isBlank()) meta.putString("rarity", rarity);

        meta.putInt("mana_value", manaValue);

        // Optional fields used by search/filters
        if (typeLine != null) meta.putString("type_line", typeLine);
        if (oracleText != null) meta.putString("oracle_text", oracleText);

        // colors lists: ["W","U"...]
        if (colors != null) {
            var lst = new net.minecraft.nbt.ListTag();
            for (String c : colors) lst.add(net.minecraft.nbt.StringTag.valueOf(c));
            meta.put("colors", lst);
        }
        if (colorIdentity != null) {
            var lst = new net.minecraft.nbt.ListTag();
            for (String c : colorIdentity) lst.add(net.minecraft.nbt.StringTag.valueOf(c));
            meta.put("color_identity", lst);
        }

        meta.putBoolean("is_token_like", tokenLike);

        // Put meta back and commit root
        root.put("mtg_meta", meta);

        // Root foil flag (CardMeta.read uses this)
        root.putBoolean("mtg_foil", foil);

        StackData.writeCustom(st, root);
        return st;
    }
}
