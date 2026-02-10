package com.spider.mtgcard.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CardCounterNbt {

    // Inside CUSTOM_DATA root
    private static final String TAG_META     = "mtg_meta";
    private static final String TAG_COUNTERS = "counters";
    private static final String TAG_ICONS    = "counter_icons";

    private CardCounterNbt() {}

    public static NbtCompound getCounters(ItemStack stack) {
        return getOrCreateMetaChild(stack, TAG_COUNTERS);
    }

    public static NbtCompound getIcons(ItemStack stack) {
        return getOrCreateMetaChild(stack, TAG_ICONS);
    }

    public static Map<String, Integer> readCounterMap(ItemStack stack) {
        NbtCompound c = getCounters(stack);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String k : c.getKeys()) {
            int v = c.getInt(k).orElse(0);
            if (v > 0) out.put(norm(k), v);
        }
        return out;
    }

    public static String getIcon(ItemStack stack, String key) {
        key = norm(key);
        NbtCompound icons = getIcons(stack);
        return icons.getString(key).orElse("none");
    }

    public static void setCounter(ItemStack stack, String key, int value) {
        key = norm(key);
        value = Math.max(0, value);

        NbtCompound counters = getCounters(stack);
        if (value <= 0) counters.remove(key);
        else counters.putInt(key, value);

        // write-back already handled by getOrCreateMetaChild (it writes when it creates),
        // BUT we still need to persist modifications:
        writeMetaChild(stack, TAG_COUNTERS, counters);
    }

    public static void removeCounter(ItemStack stack, String key) {
        key = norm(key);

        NbtCompound counters = getCounters(stack);
        NbtCompound icons = getIcons(stack);

        counters.remove(key);
        icons.remove(key);

        writeMetaChild(stack, TAG_COUNTERS, counters);
        writeMetaChild(stack, TAG_ICONS, icons);
    }

    public static void setIcon(ItemStack stack, String key, String iconKey) {
        key = norm(key);
        iconKey = (iconKey == null || iconKey.isBlank()) ? "none" : iconKey.trim().toLowerCase(Locale.ROOT);

        NbtCompound icons = getIcons(stack);
        if ("none".equals(iconKey)) icons.remove(key);
        else icons.putString(key, iconKey);

        writeMetaChild(stack, TAG_ICONS, icons);
    }

    // ---------------- internals ----------------

    private static NbtCompound getOrCreateMetaChild(ItemStack stack, String childKey) {
        NbtCompound root = getRoot(stack);
        NbtCompound meta = root.getCompound(TAG_META).orElseGet(NbtCompound::new);

        NbtCompound child = meta.getCompound(childKey).orElseGet(NbtCompound::new);
        meta.put(childKey, child);
        root.put(TAG_META, meta);

        // ensure component exists
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        return child;
    }

    private static void writeMetaChild(ItemStack stack, String childKey, NbtCompound child) {
        NbtCompound root = getRoot(stack);
        NbtCompound meta = root.getCompound(TAG_META).orElseGet(NbtCompound::new);

        meta.put(childKey, child);
        root.put(TAG_META, meta);

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private static NbtCompound getRoot(ItemStack stack) {
        var comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        return (comp == null) ? new NbtCompound() : comp.copyNbt();
    }

    private static String norm(String k) {
        if (k == null) return "";
        return k.trim().toLowerCase(Locale.ROOT);
    }
}
