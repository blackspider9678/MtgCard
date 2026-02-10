package com.spider.mtgcard.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Perms {

    /** Treat OP as "permission level >= 2" equivalent for our mod. */
    public static boolean isOp(ServerPlayerEntity player) {
        if (player == null) return false;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;

        // 1.21.11: PlayerManager#isOperator expects PlayerConfigEntry
        return server.getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()));
    }

    private Perms() {}
}
