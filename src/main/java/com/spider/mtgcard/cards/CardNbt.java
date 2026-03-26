package com.spider.mtgcard.cards;

import com.spider.mtgcard.util.StackData;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

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
        return r.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    /** "{W}{U}{2}" etc. Fallbacks to first face when needed. */
    public static String getManaCost(ItemStack stack) {
        CompoundTag m = meta(stack);

        // Single-face mana_cost
        Optional<String> mc = m.getString("mana_cost");
        if (mc.isPresent() && !mc.get().isEmpty()) return mc.get();

        // Faces[0].mana_cost
        return m.getList("card_faces")
                .flatMap((ListTag faces) -> faces.getCompound(0))
                .flatMap((CompoundTag face0) -> face0.getString("mana_cost"))
                .orElse("");
    }

    /** Simple legality: legendary creature OR explicit commander legality not "illegal". */
    public static boolean isCommanderLegal(ItemStack stack) {
        CompoundTag m = meta(stack);

        boolean legendary = m.getBoolean("is_legendary").orElse(false);
        String typeLine = m.getString("type_line").orElse("").toLowerCase(Locale.ROOT);
        boolean creature = typeLine.contains("creature");
        boolean result = legendary && creature;

        // If explicit legality exists, prefer it
        String cmd = m.getCompound("legalities")
                .flatMap((CompoundTag leg) -> leg.getString("commander"))
                .orElse("");
        if (!cmd.isEmpty()) {
            result = !"illegal".equalsIgnoreCase(cmd);
        }
        return result;
    }
}
