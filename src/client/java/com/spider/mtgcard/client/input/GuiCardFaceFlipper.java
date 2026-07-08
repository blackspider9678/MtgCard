package com.spider.mtgcard.client.input;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.net.payload.SetMenuSlotFacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class GuiCardFaceFlipper {
    private GuiCardFaceFlipper() {}

    public static boolean tryFlip(Minecraft client) {
        if (client == null || client.screen == null) return false;

        if (client.screen instanceof GuiCardFaceFlipHandler handler && handler.mtgcard$flipHoveredCardFace(client)) {
            return true;
        }

        if (!(client.screen instanceof AbstractContainerScreen<?> container)) {
            return false;
        }

        Slot slot = hoveredSlot(container);
        if (slot == null || !slot.hasItem()) return false;

        ItemStack stack = slot.getItem();
        if (!isDoubleFaced(stack)) return false;

        int faceCount = getFaceCount(stack);
        int next = (readFaceIndex(stack) + 1) % faceCount;

        writeFaceIndex(stack, next);

        int slotIndex = container.getMenu().slots.indexOf(slot);
        if (slotIndex >= 0) {
            ClientPlayNetworking.send(new SetMenuSlotFacePayload(container.getMenu().containerId, slotIndex, next));
        }
        return true;
    }

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            java.lang.reflect.Field f = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            f.setAccessible(true);
            Object value = f.get(screen);
            return value instanceof Slot slot ? slot : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isDoubleFaced(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.CARD) && getFaceCount(stack) > 1;
    }

    public static int getFaceCount(ItemStack stack) {
        CompoundTag meta = readMeta(stack);
        var el = meta.get("card_faces");
        return (el instanceof ListTag list) ? list.size() : 1;
    }

    public static int readFaceIndex(ItemStack stack) {
        return readMeta(stack).getInt("mtg_face").orElse(0);
    }

    public static void writeFaceIndex(ItemStack stack, int face) {
        if (stack == null || stack.isEmpty()) return;
        CustomData comp = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        int max = Math.max(0, getFaceCount(stack) - 1);
        meta.putInt("mtg_face", Math.max(0, Math.min(face, max)));
        root.put("mtg_meta", meta);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static CompoundTag readMeta(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = comp == null ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }
}
