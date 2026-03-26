package com.spider.mtgcard.client.displayblock;

import com.spider.mtgcard.client.life.LifePointClientState;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class DisplayLinkedLifeClient {

    public static Optional<CompoundTag> resolve(DisplayBlockEntity be) {
        if (be == null) return Optional.empty();
        var world = be.getLevel();
        if (world == null || !world.isClientSide()) return Optional.empty();
        if (!be.hasLink()) return Optional.empty();

        var dim = be.getLinkedDimId().orElse(null);
        BlockPos lpPos = be.getLinkedLifePos().orElse(null);
        if (dim == null || lpPos == null) return Optional.empty();

        // only render if linked life block is in current client dimension
        if (!world.dimension().identifier().equals(dim)) return Optional.empty();

        // 1) Prefer packet-driven cache (ONLY if we actually have this pos)
        if (LifePointClientState.CACHE.containsKey(lpPos)) {
            CompoundTag st = LifePointClientState.CACHE.get(lpPos);
            if (st != null) return Optional.of(st);
        }

        // 2) Fallback: read the client-side BE's synced NBT
        var be2 = world.getBlockEntity(lpPos);
        if (be2 instanceof LifePointBlockEntity lpbe) {
            CompoundTag n = lpbe.saveWithoutMetadata(world.registryAccess());
            if (n != null) return Optional.of(n);
        }

        return Optional.empty();
    }

    private DisplayLinkedLifeClient() {}
}
