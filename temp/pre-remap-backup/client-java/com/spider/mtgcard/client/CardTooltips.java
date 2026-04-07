package com.spider.mtgcard.client;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.util.StackData;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class CardTooltips {
    private CardTooltips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, displayComponent, lines) -> {
            if (!stack.is(ModItems.CARD)) return;

            CompoundTag root = StackData.readCustom(stack);
            CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

            String set = meta.getString("set").orElse("");
            String num = meta.getString("collector_number").orElse("");
            boolean foil = root.getBoolean("mtg_foil").orElse(false);

            if (!set.isEmpty() || !num.isEmpty()) {
                String line = (set.isEmpty() ? "" : set.toUpperCase())
                        + (num.isEmpty() ? "" : " • #" + num)
                        + (foil ? " • Foil" : "");
                lines.add(Component.literal(line));
            }
        });
    }
}