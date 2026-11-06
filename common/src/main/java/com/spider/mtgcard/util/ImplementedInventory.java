package com.spider.mtgcard.util;

import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public interface ImplementedInventory extends Container {
    NonNullList<ItemStack> getItems();

    static ImplementedInventory of(NonNullList<ItemStack> items) {
        return new ImplementedInventory() {
            @Override public NonNullList<ItemStack> getItems() { return items; }
        };
    }

    static ImplementedInventory ofSize(int size) {
        return of(NonNullList.withSize(size, ItemStack.EMPTY));
    }

    @Override default int getContainerSize() { return getItems().size(); }
    @Override default boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }
    @Override default ItemStack getItem(int slot) { return getItems().get(slot); }
    @Override default ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(getItems(), slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override default ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(getItems(), slot);
    }
    @Override default void setItem(int slot, ItemStack stack) {
        getItems().set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }
    @Override default void clearContent() { getItems().clear(); }
    @Override default void setChanged() {}
    @Override default boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
}
