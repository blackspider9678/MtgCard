package com.spider.mtgcard.util;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.List;

public final class NbtCompat {
    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag != null && tag.contains(key) ? tag.getString(key) : fallback;
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag != null && tag.contains(key) ? tag.getInt(key) : fallback;
    }

    public static long getLong(CompoundTag tag, String key, long fallback) {
        return tag != null && tag.contains(key) ? tag.getLong(key) : fallback;
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag != null && tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    public static CompoundTag getCompoundOrNull(CompoundTag tag, String key) {
        return tag != null && tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
    }

    public static <T> List<T> readList(CompoundTag tag, String key, Codec<List<T>> codec, HolderLookup.Provider lookup) {
        if (tag == null || !tag.contains(key)) return List.of();
        return codec.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag.get(key))
                .result()
                .orElse(List.of());
    }

    public static <T> void writeList(CompoundTag tag, String key, Codec<List<T>> codec, List<T> value, HolderLookup.Provider lookup) {
        codec.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), value)
                .result()
                .ifPresent(encoded -> tag.put(key, encoded));
    }

    private NbtCompat() {}
}
