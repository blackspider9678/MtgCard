package com.spider.mtgcard.api;

import com.spider.mtgcard.util.StackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Storage and lookup helpers. Removing a sleeve removes the key; there is no default sentinel ID. */
public final class CardSleeves {
    public static final String DATA_KEY = "mtgcard_sleeve";

    public static Optional<Identifier> id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        String raw = StackData.readCustom(stack).getString(DATA_KEY).orElse("");
        if (raw.isBlank()) return Optional.empty();
        try { return Optional.of(Identifier.parse(raw)); }
        catch (RuntimeException ignored) { return Optional.empty(); }
    }

    /** Returns empty for both no sleeve and an ID no longer present in the registry. */
    public static Optional<CardSleeve> get(ItemStack stack) {
        return id(stack).flatMap(SleeveRegistry::get);
    }

    public static void set(ItemStack stack, Identifier sleeveId) {
        if (!CardItemRegistry.isCard(stack)) throw new IllegalArgumentException("Item is not a registered card");
        if (SleeveRegistry.get(sleeveId).isEmpty()) throw new IllegalArgumentException("Unknown sleeve: " + sleeveId);
        CompoundTag root = StackData.readCustom(stack);
        root.putString(DATA_KEY, sleeveId.toString());
        StackData.writeCustom(stack, root);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag root = StackData.readCustom(stack);
        if (!root.contains(DATA_KEY)) return;
        root.remove(DATA_KEY);
        StackData.writeCustom(stack, root);
    }

    private CardSleeves() {}
}
