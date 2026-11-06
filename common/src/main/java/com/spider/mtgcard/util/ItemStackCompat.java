package com.spider.mtgcard.util;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;

public final class ItemStackCompat {
    private ItemStackCompat(){}

    /** Try several APIs to attach BlockEntity data to a BlockItem stack. */
    public static void putBlockEntityData(ItemStack stack, BlockEntityType<?> type, CompoundTag beTag) {
        // 1) Newer data-components (if present)
        try {
            Class<?> dct = Class.forName("net.minecraft.component.DataComponentTypes");
            Class<?> bed = Class.forName("net.minecraft.component.type.BlockEntityDataComponent");
            Object key   = dct.getField("BLOCK_ENTITY_DATA").get(null);
            Object comp  = bed.getConstructor(CompoundTag.class).newInstance(beTag);
            // stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, new BlockEntityDataComponent(tag))
            ItemStack.class.getMethod("set", key.getClass(), Object.class).invoke(stack, key, comp);
            return;
        } catch (Throwable ignored) {}

        // 2) Yarn name A: BlockItem.setBlockEntityData(stack, type, tag)
        try {
            Method m = BlockItem.class.getMethod("setBlockEntityData", ItemStack.class, BlockEntityType.class, CompoundTag.class);
            m.invoke(null, stack, type, beTag);
            return;
        } catch (Throwable ignored) {}

        // 3) Yarn name B: BlockItem.setBlockEntityNbt(stack, type, tag)
        try {
            Method m = BlockItem.class.getMethod("setBlockEntityNbt", ItemStack.class, BlockEntityType.class, CompoundTag.class);
            m.invoke(null, stack, type, beTag);
            return;
        } catch (Throwable ignored) {}

        // 4) Classic fallback: put under "BlockEntityTag"
        try {
            // stack.getOrCreateNbt().put("BlockEntityTag", beTag)
            ItemStack.class.getMethod("getOrCreateNbt")
                    .invoke(stack);
            Object tag = ItemStack.class.getMethod("getOrCreateNbt").invoke(stack);
            tag.getClass().getMethod("put", String.class, CompoundTag.class)
                    .invoke(tag, "BlockEntityTag", beTag);
            return;
        } catch (Throwable ignored) {}

        // 5) Older fallback: getOrCreateSubNbt("BlockEntityTag").copyFrom(beTag)
        try {
            Object sub = ItemStack.class.getMethod("getOrCreateSubNbt", String.class).invoke(stack, "BlockEntityTag");
            sub.getClass().getMethod("copyFrom", CompoundTag.class).invoke(sub, beTag);
        } catch (Throwable ignored) {}
    }
}
