package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;

public final class PlayerLookup {
    private PlayerLookup() {
    }

    public static Collection<ServerPlayer> tracking(ServerLevel level, BlockPos pos) {
        return level == null ? List.of() : level.players();
    }
}
