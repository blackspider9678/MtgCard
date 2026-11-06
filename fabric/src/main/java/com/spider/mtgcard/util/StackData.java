package com.spider.mtgcard.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/** Utilities for reading/writing custom per-item NBT in 1.21.10+ mappings. */
public final class StackData {

    /** Read the CUSTOM_DATA tag; returns a mutable copy (never null). */
    public static CompoundTag readCustom(ItemStack stack) {
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        return comp == null ? new CompoundTag() : comp.copyTag();
    }

    /** Write the CUSTOM_DATA tag back to the stack. */
    public static void writeCustom(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Always returns a real compound for "mtg_meta".
     * If it doesn't exist, creates it and writes it back immediately.
     */
    public static CompoundTag getOrCreateMeta(ItemStack stack) {
        CompoundTag root = readCustom(stack);

        // unwrap Optional<NbtCompound> safely
        CompoundTag meta = root.getCompound("mtg_meta");
        root.put("mtg_meta", meta);

        // save back so the meta always exists on the stack
        writeCustom(stack, root);
        return meta;
    }

    public static void writeFace(ItemStack st, int face) {
        if (st == null || st.isEmpty()) return;

        var comp = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta");

        meta.putInt("mtg_face", Math.max(0, face));

        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    // StackData.java (add near other helpers)
    public static void ensureUniqueUid(ItemStack st) {
        if (st == null || st.isEmpty()) return;

        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();

        CompoundTag meta = root.getCompound("mtg_meta");

        // Always overwrite (so copies can't share the same uid)
        meta.putString("mtg_uid", java.util.UUID.randomUUID().toString());

        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    // StackData.java
    public static boolean readHidden(ItemStack st) {
        CompoundTag root = readCustom(st);
        CompoundTag meta = root.getCompound("mtg_meta");
        return meta.getBoolean("mtg_hidden");
    }

    public static void deleteCounterKey(ItemStack st, String key) {
        if (st == null || st.isEmpty()) return;
        if (key == null || key.isBlank()) return;

        final String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');

        var comp = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        net.minecraft.nbt.CompoundTag meta = root.getCompound("mtg_meta");

        // counters
        net.minecraft.nbt.CompoundTag counters = meta.getCompound("counters");
        counters.remove(k);
        meta.put("counters", counters);

        // icons
        net.minecraft.nbt.CompoundTag icons = meta.getCompound("counter_icons");
        icons.remove(k);
        meta.put("counter_icons", icons);

        // names
        net.minecraft.nbt.CompoundTag names = meta.getCompound("counter_names");
        names.remove(k);
        meta.put("counter_names", names);

        root.put("mtg_meta", meta);
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(root));
    }

    public static void writeHidden(ItemStack st, boolean hidden) {
        // If you already have editMeta(...) use it, otherwise do it inline like this:
        var comp = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();

        CompoundTag meta = root.getCompound("mtg_meta");
        meta.putBoolean("mtg_hidden", hidden);

        root.put("mtg_meta", meta);
        st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(root));
    }
    private StackData() {}
}
