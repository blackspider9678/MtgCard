package com.spider.mtgcard.config;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;

public final class Perms {

    /** Treat OP as "permission level >= 2" equivalent for our mod. */
    public static boolean isOp(ServerPlayer player) {
        if (player == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;

        // 1.21.11: PlayerManager#isOperator expects PlayerConfigEntry
        return server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    private Perms() {}
}
