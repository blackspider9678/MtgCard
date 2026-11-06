package com.spider.mtgcard.content.pack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;

import java.util.*;

public final class PackRefundState extends SavedData {

    private static final String KEY = "mtgcard_pack_refunds";

    // UUID -> refunded packs
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public PackRefundState() {}

    private static final SavedData.Factory<PackRefundState> TYPE =
            new SavedData.Factory<>(PackRefundState::new, PackRefundState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private static PackRefundState load(CompoundTag tag, HolderLookup.Provider lookup) {
        PackRefundState state = new PackRefundState();
        CompoundTag pendingTag = tag.getCompound("pending");

        for (String uuidKey : pendingTag.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ListTag stacksTag = pendingTag.getList(uuidKey, Tag.TAG_COMPOUND);
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < stacksTag.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(lookup, stacksTag.getCompound(i));
                if (!stack.isEmpty()) stacks.add(stack);
            }

            if (!stacks.isEmpty()) state.pending.put(id, stacks);
        }

        return state;
    }

    public static PackRefundState get(MinecraftServer server) {
        ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
        return overworld.getDataStorage().computeIfAbsent(TYPE, KEY);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag pendingTag = new CompoundTag();

        for (var entry : pending.entrySet()) {
            ListTag stacksTag = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                if (!stack.isEmpty()) stacksTag.add(stack.save(lookup));
            }
            pendingTag.put(entry.getKey().toString(), stacksTag);
        }

        tag.put("pending", pendingTag);
        return tag;
    }

    public void addRefund(UUID playerId, ItemStack packOne) {
        if (playerId == null) return;
        if (packOne == null || packOne.isEmpty()) return;

        ItemStack one = packOne.copy();
        one.setCount(1);

        pending.computeIfAbsent(playerId, k -> new ArrayList<>()).add(one);
        setDirty();
    }

    public List<ItemStack> drain(UUID playerId) {
        if (playerId == null) return List.of();
        List<ItemStack> list = pending.remove(playerId);
        if (list == null || list.isEmpty()) return List.of();
        setDirty();
        return list;
    }
}
