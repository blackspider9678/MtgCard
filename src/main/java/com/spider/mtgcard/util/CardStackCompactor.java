package com.spider.mtgcard.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Removes the redundant generic copy of native MTG metadata without dropping card data. */
public final class CardStackCompactor {
    private CardStackCompactor() {}

    /**
     * Migrates an existing MTG card in place. All authoritative fields remain in
     * mtg_meta; tcg_meta is reserved for addon-owned non-MTG card schemas.
     */
    public static void compact(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag root = customData.copyTag();
        CompoundTag mtg = root.getCompound(TcgCardMeta.MTG_META).orElse(null);
        if (mtg == null) return;

        root.remove(TcgCardMeta.TCG_META);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
}
