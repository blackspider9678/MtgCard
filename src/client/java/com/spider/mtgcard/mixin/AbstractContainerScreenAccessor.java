package com.spider.mtgcard.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("clickedSlot")
    Slot mtgcard$getClickedSlot();

    @Accessor("draggingItem")
    ItemStack mtgcard$getDraggingItem();

    @Accessor("isSplittingStack")
    boolean mtgcard$isSplittingStack();

    @Accessor("isQuickCrafting")
    boolean mtgcard$isQuickCrafting();

    @Accessor("quickCraftSlots")
    Set<Slot> mtgcard$getQuickCraftSlots();
}
