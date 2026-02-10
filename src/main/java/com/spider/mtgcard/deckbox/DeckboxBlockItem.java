package com.spider.mtgcard.deckbox;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class DeckboxBlockItem extends BlockItem {

    // Slot layout:
    // 99  = bundle-only
    // 100 = Commander
    // 101 = Commander Partner
    private static final int COMMANDER_SLOT = DeckboxBlockEntity.SECOND_SIDE_SLOT; // 100
    private static final int PARTNER_SLOT   = DeckboxBlockEntity.THIRD_SIDE_SLOT;  // 101

    public DeckboxBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplayComponent displayComponent,
            Consumer<Text> textConsumer,
            TooltipType type
    ) {
        super.appendTooltip(stack, context, displayComponent, textConsumer, type);

        // Read CUSTOM_DATA -> BlockEntityTag -> Items[]
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return;

        NbtCompound root = comp.copyNbt();
        if (root == null) return;

        var beTagOpt = root.getCompound("BlockEntityTag");
        if (beTagOpt.isEmpty()) return;

        var itemsOpt = beTagOpt.get().getList("Items");
        if (itemsOpt.isEmpty()) return;

        NbtList items = itemsOpt.get();

        Text commanderName = null;
        Text partnerName = null;

        // Find slot 100 + 101 entries (if present)
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof NbtCompound entry)) continue;

            int slot = entry.getInt("Slot").orElse(-1);
            if (slot != COMMANDER_SLOT && slot != PARTNER_SLOT) continue;

            var stackOpt = entry.getCompound("Stack");
            if (stackOpt.isEmpty()) continue;

            NbtCompound stackTag = stackOpt.get();

            // Your stored card stacks are component-based:
            // Stack: { components: { "minecraft:custom_name": "..." , ... } }
            var compsOpt = stackTag.getCompound("components");
            if (compsOpt.isEmpty()) continue;

            NbtCompound comps = compsOpt.get();

            // Primary: custom_name
            var nameOpt = comps.getString("minecraft:custom_name");
            if (nameOpt.isEmpty()) continue;

            Text name = Text.literal(nameOpt.get());

            if (slot == COMMANDER_SLOT) commanderName = name;
            if (slot == PARTNER_SLOT)   partnerName = name;
        }

        // Emit tooltip lines
        if (commanderName != null) {
            textConsumer.accept(Text.literal("Commander: ").append(commanderName));
        }
        if (partnerName != null) {
            textConsumer.accept(Text.literal("Partner: ").append(partnerName));
        }
    }
}
