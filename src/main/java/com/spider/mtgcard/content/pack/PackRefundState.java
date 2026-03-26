package com.spider.mtgcard.content.pack;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.Level;

import java.util.*;

public final class PackRefundState extends SavedData {

    private static final Identifier KEY =
            Identifier.fromNamespaceAndPath("mtgcard", "pack_refunds");

    // UUID -> refunded packs
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public PackRefundState() {}

    private PackRefundState(Map<UUID, List<ItemStack>> map) {
        if (map != null) this.pending.putAll(map);
    }

    private Map<UUID, List<ItemStack>> toMap() {
        return pending;
    }

    // Saves: { pending: { <uuid>: [<itemstack>, ...] } }
    private static final Codec<PackRefundState> CODEC =
            Codec.unboundedMap(UUIDUtil.AUTHLIB_CODEC, ItemStack.CODEC.listOf())
                    .fieldOf("pending")
                    .xmap(PackRefundState::new, PackRefundState::toMap)
                    .codec();

    private static final SavedDataType<PackRefundState> TYPE =
            new SavedDataType<>(
                    KEY,
                    PackRefundState::new,
                    CODEC,
                    null
            );

    public static PackRefundState get(MinecraftServer server) {
        ServerLevel overworld = Objects.requireNonNull(server.getLevel(Level.OVERWORLD));
        return overworld.getDataStorage().computeIfAbsent(TYPE);
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
