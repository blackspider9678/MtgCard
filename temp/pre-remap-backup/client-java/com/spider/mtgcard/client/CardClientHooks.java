package com.spider.mtgcard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

@Environment(EnvType.CLIENT)
public class CardClientHooks {

    /** Preferred: called reflectively from CardItem with (player, hand). */
    public static void openLargeViewFromHand(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || player == null) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack == null || stack.isEmpty()) return;

        int slot = (hand == InteractionHand.OFF_HAND) ? 40 : player.getInventory().getSelectedSlot();

        mc.setScreen(new CardLargeViewScreen(stack, slot, -1));
    }

    /** Legacy: old reflective entrypoint. Works, but can pick wrong slot if duplicates exist. */
    public static void openLargeView(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // Keep your old behavior as a fallback
        int slot = findHandSlot(mc.player, stack);
        if (slot < 0) slot = mc.player.getInventory().getSelectedSlot();

        mc.setScreen(new CardLargeViewScreen(stack, slot, -1));
    }

    /** Returns inventory slot index for the held stack, or -1 if unknown. */
    private static int findHandSlot(Player player, ItemStack held) {
        // Main hand → selected hotbar slot
        if (ItemStack.matches(player.getMainHandItem(), held)) {
            return player.getInventory().getSelectedSlot();
        }

        // Offhand → vanilla index 40
        if (ItemStack.matches(player.getOffhandItem(), held)) {
            return 40;
        }

        // Fallback: search inventory (WARNING: can match duplicates)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (ItemStack.matches(s, held)) return i;
        }

        return -1;
    }
}
