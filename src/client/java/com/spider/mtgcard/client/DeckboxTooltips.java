package com.spider.mtgcard.client;

import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.registry.ModBlocks;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DeckboxTooltips {

    private static final int COMMANDER_SLOT = DeckboxBlockEntity.SECOND_SIDE_SLOT;
    private static final int PARTNER_SLOT   = DeckboxBlockEntity.THIRD_SIDE_SLOT;

    private DeckboxTooltips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, displayComponent, lines) -> {
            if (!stack.is(ModBlocks.DECKBOX_ITEM)) return;

            CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
            if (comp == null) return;

            CompoundTag root = comp.copyTag();
            if (root == null) return;

            var beTagOpt = root.getCompound("BlockEntityTag");
            if (beTagOpt.isEmpty()) return;

            var itemsOpt = beTagOpt.get().getList("Items");
            if (itemsOpt.isEmpty()) return;

            ListTag items = itemsOpt.get();

            Component commanderName = null;
            Component partnerName = null;

            for (int i = 0; i < items.size(); i++) {
                if (!(items.get(i) instanceof CompoundTag entry)) continue;

                int slot = entry.getInt("Slot").orElse(-1);
                if (slot != COMMANDER_SLOT && slot != PARTNER_SLOT) continue;

                var stackOpt = entry.getCompound("Stack");
                if (stackOpt.isEmpty()) continue;

                CompoundTag stackTag = stackOpt.get();

                var compsOpt = stackTag.getCompound("components");
                if (compsOpt.isEmpty()) continue;

                CompoundTag comps = compsOpt.get();

                var nameOpt = comps.getString("minecraft:custom_name");
                if (nameOpt.isEmpty()) continue;

                Component name = Component.literal(nameOpt.get());

                if (slot == COMMANDER_SLOT) commanderName = name;
                if (slot == PARTNER_SLOT) partnerName = name;
            }

            if (commanderName != null) {
                lines.add(Component.literal("Commander: ").append(commanderName));
            }
            if (partnerName != null) {
                lines.add(Component.literal("Partner: ").append(partnerName));
            }
        });
    }
}
