// com/spider/mtgcard/graveyard/GraveyardInventory.java
package com.spider.mtgcard.graveyard;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class GraveyardInventory implements Container {
    private final GraveyardBlockEntity be;

    public GraveyardInventory(GraveyardBlockEntity be) {
        this.be = be;
    }

    @Override public int getContainerSize() { return GraveyardBlockEntity.TOTAL; }
    @Override public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getItem(int slot) { return be == null ? ItemStack.EMPTY : be.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        // cards are stack size 1 anyway; keep it simple
        if (be == null) return ItemStack.EMPTY;
        ItemStack st = be.getItem(slot);
        if (st.isEmpty()) return ItemStack.EMPTY;
        return be.removeItemNoUpdate(slot);
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (be == null) return ItemStack.EMPTY;
        return be.removeItemNoUpdate(slot);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (be == null) return;
        be.setItem(slot, stack);
    }
    @Override public void setChanged() {}
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
    @Override public void clearContent() {
        if (be == null) return;
        for (int i = 0; i < getContainerSize(); i++) be.setItem(i, ItemStack.EMPTY);
    }
}
