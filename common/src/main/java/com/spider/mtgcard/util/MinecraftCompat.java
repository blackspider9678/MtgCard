package com.spider.mtgcard.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.lang.reflect.Method;
import java.util.stream.Stream;

public final class MinecraftCompat {
    public static ItemEntity drop(Player player, ItemStack stack, boolean throwRandomly) {
        if (player == null) return null;

        try {
            Class<?> predictionClass = Class.forName("net.minecraft.util.Prediction");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object serverOnly = Enum.valueOf((Class<? extends Enum>) predictionClass.asSubclass(Enum.class), "SERVER_ONLY");
            Method drop = player.getClass().getMethod("drop", ItemStack.class, boolean.class, predictionClass);
            return (ItemEntity) drop.invoke(player, stack, throwRandomly, serverOnly);
        } catch (Throwable ignored) {
        }

        try {
            Method drop = player.getClass().getMethod("drop", ItemStack.class, boolean.class);
            return (ItemEntity) drop.invoke(player, stack, throwRandomly);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Stream<ItemStack> bundleItemCopies(BundleContents contents) {
        if (contents == null) return Stream.empty();

        for (String methodName : new String[] { "itemCopies", "itemCopyStream" }) {
            try {
                Method method = BundleContents.class.getMethod(methodName);
                Object value = method.invoke(contents);
                if (value instanceof Stream<?> stream) {
                    return stream
                            .filter(ItemStack.class::isInstance)
                            .map(ItemStack.class::cast);
                }
            } catch (Throwable ignored) {
            }
        }

        return Stream.empty();
    }

    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        if (entity == null) return;

        for (String methodName : new String[] { "setPermanentlyInvulnerable", "setInvulnerable" }) {
            try {
                Method method = entity.getClass().getMethod(methodName, boolean.class);
                method.invoke(entity, invulnerable);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private MinecraftCompat() {}
}
