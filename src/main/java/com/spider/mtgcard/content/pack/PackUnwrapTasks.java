package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.net.ModPayloads;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

// com.spider.mtgcard.content.pack.PackUnwrapTasks
public final class PackUnwrapTasks {
    public static void start(ServerWorld world, ServerPlayerEntity player, ItemStack pack) {
        // 15 cards -> do 15 steps
        final int steps = 15;
        world.getServer().execute(() -> runStep(world, player, pack, 0, steps));
    }

    private static void runStep(ServerWorld world, ServerPlayerEntity player, ItemStack pack, int i, int steps) {
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
