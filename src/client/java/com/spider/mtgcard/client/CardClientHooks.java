package com.spider.mtgcard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

@Environment(EnvType.CLIENT)
public class CardClientHooks {

    /** Preferred: called reflectively from CardItem with (player, hand). */
    public static void openLargeViewFromHand(PlayerEntity player, Hand hand) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || player == null) return;

        ItemStack stack = player.getStackInHand(hand);
        if (stack == null || stack.isEmpty()) return;

        int slot = (hand == Hand.OFF_HAND) ? 40 : player.getInventory().getSelectedSlot();

        mc.setScreen(new CardLargeViewScreen(stack, slot, -1));
    }

    /** Legacy: old reflective entrypoint. Works, but can pick wrong slot if duplicates exist. */
    public static void openLargeView(ItemStack stack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        // Keep your old behavior as a fallback
        int slot = findHandSlot(mc.player, stack);
        if (slot < 0) slot = mc.player.getInventory().getSelectedSlot();

        mc.setScreen(new CardLargeViewScreen(stack, slot, -1));
    }

    /** Returns inventory slot index for the held stack, or -1 if unknown. */
    private static int findHandSlot(PlayerEntity player, ItemStack held) {
        // Main hand → selected hotbar slot
        if (ItemStack.areEqual(player.getMainHandStack(), held)) {
            return player.getInventory().getSelectedSlot();
        }

        // Offhand → vanilla index 40
        if (ItemStack.areEqual(player.getOffHandStack(), held)) {
            return 40;
        }

        // Fallback: search inventory (WARNING: can match duplicates)
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (ItemStack.areEqual(s, held)) return i;
        }

        return -1;
    }
}
