package com.spider.mtgcard.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Utilities for reading/writing custom per-item NBT in 1.21.10+ mappings. */
public final class StackData {

    /** Read the CUSTOM_DATA tag; returns a mutable copy (never null). */
    public static NbtCompound readCustom(ItemStack stack) {
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        return comp == null ? new NbtCompound() : comp.copyNbt();
    }

    /** Write the CUSTOM_DATA tag back to the stack. */
    public static void writeCustom(ItemStack stack, NbtCompound tag) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    /**
     * Always returns a real compound for "mtg_meta".
     * If it doesn't exist, creates it and writes it back immediately.
     */
    public static NbtCompound getOrCreateMeta(ItemStack stack) {
        NbtCompound root = readCustom(stack);

        // unwrap Optional<NbtCompound> safely
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        root.put("mtg_meta", meta);

        // save back so the meta always exists on the stack
        writeCustom(stack, root);
        return meta;
    }

    public static void writeFace(ItemStack st, int face) {
        if (st == null || st.isEmpty()) return;

        var comp = st.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound root = comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        meta.putInt("mtg_face", Math.max(0, face));

        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    // StackData.java (add near other helpers)
    public static void ensureUniqueUid(ItemStack st) {
        if (st == null || st.isEmpty()) return;

        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();

        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        // Always overwrite (so copies can't share the same uid)
        meta.putString("mtg_uid", java.util.UUID.randomUUID().toString());

        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    // StackData.java
    public static boolean readHidden(ItemStack st) {
        NbtCompound root = readCustom(st);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        return meta.getBoolean("mtg_hidden").orElse(false);
    }

    public static void deleteCounterKey(ItemStack st, String key) {
        if (st == null || st.isEmpty()) return;
        if (key == null || key.isBlank()) return;

        final String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');

        var comp = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound root = (comp == null) ? new net.minecraft.nbt.NbtCompound() : comp.copyNbt();
        net.minecraft.nbt.NbtCompound meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.NbtCompound::new);

        // counters
        net.minecraft.nbt.NbtCompound counters = meta.getCompound("counters").orElseGet(net.minecraft.nbt.NbtCompound::new);
        counters.remove(k);
        meta.put("counters", counters);

        // icons
        net.minecraft.nbt.NbtCompound icons = meta.getCompound("counter_icons").orElseGet(net.minecraft.nbt.NbtCompound::new);
        icons.remove(k);
        meta.put("counter_icons", icons);

        // names
        net.minecraft.nbt.NbtCompound names = meta.getCompound("counter_names").orElseGet(net.minecraft.nbt.NbtCompound::new);
        names.remove(k);
        meta.put("counter_names", names);

        root.put("mtg_meta", meta);
        st.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(root));
    }

    public static void writeHidden(ItemStack st, boolean hidden) {
        // If you already have editMeta(...) use it, otherwise do it inline like this:
        var comp = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();

        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        meta.putBoolean("mtg_hidden", hidden);

        root.put("mtg_meta", meta);
        st.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(root));
    }
    private StackData() {}
}
