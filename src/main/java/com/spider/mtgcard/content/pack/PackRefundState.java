package com.spider.mtgcard.content.pack;

import com.mojang.serialization.Codec;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.*;

public final class PackRefundState extends PersistentState {

    private static final String KEY = "mtgcard_pack_refunds";

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
            Codec.unboundedMap(Uuids.CODEC, ItemStack.CODEC.listOf())
                    .fieldOf("pending")
                    .xmap(PackRefundState::new, PackRefundState::toMap)
                    .codec();

    private static final PersistentStateType<PackRefundState> TYPE =
            new PersistentStateType<>(
                    KEY,
                    PackRefundState::new, // <-- no-arg supplier (THIS fixes your compile error)
                    CODEC,
                    null
            );

    public static PackRefundState get(MinecraftServer server) {
        ServerWorld overworld = Objects.requireNonNull(server.getWorld(World.OVERWORLD));
        return overworld.getPersistentStateManager().getOrCreate(TYPE);
    }

    public void addRefund(UUID playerId, ItemStack packOne) {
        if (playerId == null) return;
        if (packOne == null || packOne.isEmpty()) return;

        ItemStack one = packOne.copy();
        one.setCount(1);

        pending.computeIfAbsent(playerId, k -> new ArrayList<>()).add(one);
        markDirty();
    }

    public List<ItemStack> drain(UUID playerId) {
        if (playerId == null) return List.of();
        List<ItemStack> list = pending.remove(playerId);
        if (list == null || list.isEmpty()) return List.of();
        markDirty();
        return list;
    }
}
