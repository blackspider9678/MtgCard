package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.net.ModPayloads;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

// com.spider.mtgcard.content.pack.PackUnwrapTasks
public final class PackUnwrapTasks {
    public static void start(ServerLevel world, ServerPlayer player, ItemStack pack) {
        // 15 cards -> do 15 steps
        final int steps = 15;
        world.getServer().execute(() -> runStep(world, player, pack, 0, steps));
    }

    private static void runStep(ServerLevel world, ServerPlayer player, ItemStack pack, int i, int steps) {
        if (i >= steps) {
            ModPayloads.sendUnpackProgress(player, 100);
            // replace pack with bundle (already filled by your generator)
            // … your existing “generatePackIntoBundle(player, pack)” here …
            return;
        }
        int pct = (i * 100) / steps;
        ModPayloads.sendUnpackProgress(player, pct);

        // Optionally: generate one card and stash into a pending list here

        // schedule next step next tick
        world.getServer().execute(() -> runStep(world, player, pack, i + 1, steps));
    }
}
