package com.spider.mtgcard.content.pack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class PackInventoryUtil {
    static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        player.getInventory().placeItemBackInInventory(stack);
        player.containerMenu.broadcastChanges();
    }

    private PackInventoryUtil() {}
}
