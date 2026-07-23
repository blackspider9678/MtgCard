package com.spider.mtgcard.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CardCounterNbt {

    // Inside CUSTOM_DATA root
    private static final String TAG_META     = "mtg_meta";
    private static final String TAG_COUNTERS = "counters";
    private static final String TAG_ICONS    = "counter_icons";

    private CardCounterNbt() {}

    public static CompoundTag getCounters(ItemStack stack) {
        return getOrCreateMetaChild(stack, TAG_COUNTERS);
    }

    public static CompoundTag getIcons(ItemStack stack) {
        return getOrCreateMetaChild(stack, TAG_ICONS);
    }

    public static Map<String, Integer> readCounterMap(ItemStack stack) {
        CompoundTag c = getCounters(stack);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String k : c.keySet()) {
            int v = c.getInt(k).orElse(0);
            if (v > 0) out.put(norm(k), v);
        }
        return out;
    }

    public static String getIcon(ItemStack stack, String key) {
        key = norm(key);
        CompoundTag icons = getIcons(stack);
        return icons.getString(key).orElse("none");
    }

    public static void setCounter(ItemStack stack, String key, int value) {
        key = norm(key);
        value = Math.max(0, value);

        CompoundTag counters = getCounters(stack);
        if (value <= 0) counters.remove(key);
        else counters.putInt(key, value);

        // write-back already handled by getOrCreateMetaChild (it writes when it creates),
        // BUT we still need to persist modifications:
        writeMetaChild(stack, TAG_COUNTERS, counters);
    }

    public static void removeCounter(ItemStack stack, String key) {
        key = norm(key);

        CompoundTag counters = getCounters(stack);
        CompoundTag icons = getIcons(stack);

        counters.remove(key);
        icons.remove(key);

        writeMetaChild(stack, TAG_COUNTERS, counters);
        writeMetaChild(stack, TAG_ICONS, icons);
    }

    public static void setIcon(ItemStack stack, String key, String iconKey) {
        key = norm(key);
        iconKey = (iconKey == null || iconKey.isBlank()) ? "none" : iconKey.trim().toLowerCase(Locale.ROOT);

        CompoundTag icons = getIcons(stack);
        if ("none".equals(iconKey)) icons.remove(key);
        else icons.putString(key, iconKey);

        writeMetaChild(stack, TAG_ICONS, icons);
    }

    // ---------------- internals ----------------

    private static CompoundTag getOrCreateMetaChild(ItemStack stack, String childKey) {
        CompoundTag root = getRoot(stack);
        CompoundTag meta = root.getCompound(TAG_META).orElseGet(CompoundTag::new);

        CompoundTag child = meta.getCompound(childKey).orElseGet(CompoundTag::new);
        meta.put(childKey, child);
        root.put(TAG_META, meta);

        // ensure component exists
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return child;
    }

    private static void writeMetaChild(ItemStack stack, String childKey, CompoundTag child) {
        CompoundTag root = getRoot(stack);
        CompoundTag meta = root.getCompound(TAG_META).orElseGet(CompoundTag::new);

        meta.put(childKey, child);
        root.put(TAG_META, meta);

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static CompoundTag getRoot(ItemStack stack) {
        var comp = stack.get(DataComponents.CUSTOM_DATA);
        return (comp == null) ? new CompoundTag() : comp.copyTag();
    }

    private static String norm(String k) {
        if (k == null) return "";
        return k.trim().toLowerCase(Locale.ROOT);
    }
}
