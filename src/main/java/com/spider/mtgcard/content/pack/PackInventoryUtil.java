package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.List;

final class PackInventoryUtil {
    record DeliveryResult(boolean success, String mode) {
        static DeliveryResult failed(String mode) {
            return new DeliveryResult(false, mode);
        }
    }

    static DeliveryResult giveOrDrop(ServerPlayer player, ItemStack stack, int preferredSlot) {
        return giveOrDrop(player, stack, preferredSlot, "generic");
    }

    static DeliveryResult ejectLikeBundle(ServerPlayer player, ItemStack stack, String context) {
        if (player == null) return DeliveryResult.failed("no_player");
        if (stack == null || stack.isEmpty()) return DeliveryResult.failed("empty_stack");

        ItemStack reward = stack.copy();
        String playerName = player.getName().getString();
        boolean packDebug = MtgcardConfig.packDebugEnabled();
        int selectedSlot = player.getInventory().getSelectedSlot();

        if (packDebug) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Bundle-like ejection attempt context={} player={} reward={} inventoryBefore={}",
                    context,
                    playerName,
                    describeStack(reward),
                    describeInventoryState(player, selectedSlot)
            );
        }

        ItemEntity dropped = player.drop(reward, true);
        sync(player);
        if (dropped != null) {
            if (packDebug) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Bundle-like ejection succeeded context={} player={} droppedStack={} dropPos=({}, {}, {}) velocity=({}, {}, {}) inventoryAfter={}",
                        context,
                        playerName,
                        describeStack(reward),
                        String.format("%.2f", dropped.getX()),
                        String.format("%.2f", dropped.getY()),
                        String.format("%.2f", dropped.getZ()),
                        String.format("%.2f", dropped.getDeltaMovement().x),
                        String.format("%.2f", dropped.getDeltaMovement().y),
                        String.format("%.2f", dropped.getDeltaMovement().z),
                        describeInventoryState(player, selectedSlot)
                );
            }
            return new DeliveryResult(true, "bundle_eject");
        }

        if (packDebug) {
            Mtgcard.LOGGER.error(
                    "[MTGCard][PackDebug] Bundle-like ejection failed context={} player={} rewardRemaining={} inventoryAfter={}",
                    context,
                    playerName,
                    describeStack(reward),
                    describeInventoryState(player, selectedSlot)
            );
        }
        return DeliveryResult.failed("bundle_eject_failed");
    }

    static DeliveryResult giveOrDrop(ServerPlayer player, ItemStack stack, int preferredSlot, String context) {
        if (player == null) return DeliveryResult.failed("no_player");
        if (stack == null || stack.isEmpty()) return DeliveryResult.failed("empty_stack");

        ItemStack reward = stack.copy();
        String playerName = player.getName().getString();
        boolean packDebug = MtgcardConfig.packDebugEnabled();

        if (packDebug) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Delivery attempt context={} player={} preferredSlot={} reward={} inventoryBefore={}",
                    context,
                    playerName,
                    preferredSlot,
                    describeStack(reward),
                    describeInventoryState(player, preferredSlot)
            );
        }

        if (isValidPreferredSlot(player, preferredSlot) && player.getInventory().getItem(preferredSlot).isEmpty()) {
            player.getInventory().setItem(preferredSlot, reward);
            player.getInventory().setChanged();
            syncPlayerInventorySlot(player, preferredSlot);
            sync(player);
            if (packDebug) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Delivery used preferred slot context={} player={} preferredAfter={} inventoryAfter={}",
                        context,
                        playerName,
                        describeSlot(player, preferredSlot),
                        describeInventoryState(player, preferredSlot)
                );
            }
            return new DeliveryResult(true, "preferred_slot");
        }

        boolean added = player.getInventory().add(reward);
        player.getInventory().setChanged();
        sync(player);
        if (reward.isEmpty()) {
            if (packDebug) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Delivery added to inventory context={} player={} added={} inventoryAfter={}",
                        context,
                        playerName,
                        added,
                        describeInventoryState(player, preferredSlot)
                );
            }
            return new DeliveryResult(true, added ? "inventory" : "inventory_unknown");
        }

        ItemEntity dropped = player.drop(reward, false);
        sync(player);
        if (dropped != null) {
            if (packDebug) {
                Mtgcard.LOGGER.info(
                        "[MTGCard][PackDebug] Delivery dropped near player context={} player={} addedBeforeDrop={} droppedStack={} dropPos=({}, {}, {}) inventoryAfter={}",
                        context,
                        playerName,
                        added,
                        describeStack(reward),
                        String.format("%.2f", dropped.getX()),
                        String.format("%.2f", dropped.getY()),
                        String.format("%.2f", dropped.getZ()),
                        describeInventoryState(player, preferredSlot)
                );
            }
            return new DeliveryResult(true, added ? "inventory_and_drop" : "drop");
        }

        if (packDebug) {
            Mtgcard.LOGGER.error(
                    "[MTGCard][PackDebug] Delivery failed context={} player={} rewardRemaining={} inventoryAfter={}",
                    context,
                    playerName,
                    describeStack(reward),
                    describeInventoryState(player, preferredSlot)
            );
        }
        return DeliveryResult.failed("delivery_failed");
    }

    static String describeInventoryState(ServerPlayer player, int preferredSlot) {
        if (player == null) return "no_player";

        int size = player.getInventory().getContainerSize();
        int empty = 0;
        int bundles = 0;
        for (int i = 0; i < size; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.isEmpty()) empty++;
            if (st.is(Items.BUNDLE)) bundles++;
        }

        int selectedSlot = player.getInventory().getSelectedSlot();
        return "selected=" + describeSlot(player, selectedSlot)
                + ", preferred=" + describeSlot(player, preferredSlot)
                + ", empty=" + empty + "/" + size
                + ", bundles=" + bundles;
    }

    static String describeSlot(ServerPlayer player, int slot) {
        if (player == null) return "no_player";
        if (!isValidPreferredSlot(player, slot)) return "slot " + slot + "=<invalid>";
        return "slot " + slot + "=" + describeStack(player.getInventory().getItem(slot));
    }

    static String describeStack(ItemStack stack) {
        if (stack == null) return "<null>";
        if (stack.isEmpty()) return "<empty>";

        String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        String hoverName = stack.getHoverName().getString();
        int bundleItems = countBundleItems(stack);
        String bundleInfo = bundleItems >= 0 ? ", bundleItems=" + bundleItems : "";
        String packUid = readPackUid(stack);
        String packInfo = packUid.isBlank() ? "" : ", packUid=" + packUid;
        return hoverName + " x" + stack.getCount() + " {" + itemId + bundleInfo + packInfo + "}";
    }

    static String readPackUid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        try {
            var comp = stack.get(DataComponents.CUSTOM_DATA);
            if (comp == null) return "";
            var root = comp.copyTag();
            var tag = root.getCompound("mtg_pack").orElse(null);
            if (tag == null) return "";
            return tag.getString("uid").orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isValidPreferredSlot(ServerPlayer player, int preferredSlot) {
        return preferredSlot >= 0 && preferredSlot < player.getInventory().getContainerSize();
    }

    private static int countBundleItems(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.BUNDLE)) return -1;
        try {
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null) return 0;

            try {
                Method sizeMethod = BundleContents.class.getMethod("size");
                Object value = sizeMethod.invoke(contents);
                if (value instanceof Integer count) {
                    return count;
                }
            } catch (Throwable ignored) {}

            try {
                Method itemsMethod = BundleContents.class.getMethod("items");
                Object value = itemsMethod.invoke(contents);
                if (value instanceof List<?> items) {
                    return items.size();
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return -1;
    }

    private static void sync(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void syncPlayerInventorySlot(ServerPlayer player, int slot) {
        ItemStack synced = player.getInventory().getItem(slot).copy();

        try {
            player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));
        } catch (Throwable ignored) {}

        try {
            player.inventoryMenu.findSlot(player.getInventory(), slot)
                    .ifPresent(menuSlot -> player.inventoryMenu.setRemoteSlot(menuSlot, synced.copy()));
        } catch (Throwable ignored) {}

        try {
            player.containerMenu.findSlot(player.getInventory(), slot)
                    .ifPresent(menuSlot -> player.containerMenu.setRemoteSlot(menuSlot, synced.copy()));
        } catch (Throwable ignored) {}
    }

    private PackInventoryUtil() {}
}
