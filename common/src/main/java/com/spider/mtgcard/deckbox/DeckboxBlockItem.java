package com.spider.mtgcard.deckbox;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DeckboxBlockItem extends BlockItem {

    public DeckboxBlockItem(net.minecraft.world.level.block.Block block, Item.Properties settings) {
        super(block, settings);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component commanderName = DeckboxNaming.getCommanderName(stack);
        if (commanderName != null) {
            return commanderName;
        }

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            return customName;
        }

        return super.getName(stack);
    }
}
