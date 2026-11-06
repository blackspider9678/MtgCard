package com.spider.mtgcard.cards;

import com.spider.mtgcard.util.StackData;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class CardNbt {
    private CardNbt() {}

    /** Root of your CUSTOM_DATA (StackData) */
    private static CompoundTag root(ItemStack stack) {
        // Your StackData returns the Optional-style compound used in CardNBTUtil
        return StackData.readCustom(stack);
    }

    /** mtg_meta subcompound (Optional-aware) */
    private static CompoundTag meta(ItemStack stack) {
        CompoundTag r = root(stack);
        return r.getCompound("mtg_meta");
    }

    /** "{W}{U}{2}" etc. Fallbacks to first face when needed. */
    public static String getManaCost(ItemStack stack) {
        CompoundTag m = meta(stack);

        // Single-face mana_cost
        String mc = m.getString("mana_cost");
        if (!mc.isEmpty()) return mc;

        // Faces[0].mana_cost
        ListTag faces = m.getList("card_faces", Tag.TAG_COMPOUND);
        if (faces.isEmpty()) return "";
        return faces.getCompound(0).getString("mana_cost");
    }

    /** Simple legality: legendary creature OR explicit commander legality not "illegal". */
    public static boolean isCommanderLegal(ItemStack stack) {
        CompoundTag m = meta(stack);

        boolean legendary = m.getBoolean("is_legendary");
        String typeLine = m.getString("type_line").toLowerCase(Locale.ROOT);
        boolean creature = typeLine.contains("creature");
        boolean result = legendary && creature;

        // If explicit legality exists, prefer it
        String cmd = m.getCompound("legalities").getString("commander");
        if (!cmd.isEmpty()) {
            result = !"illegal".equalsIgnoreCase(cmd);
        }
        return result;
    }
}
