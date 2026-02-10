// com/spider/mtgcard/graveyard/GraveyardInventory.java
package com.spider.mtgcard.graveyard;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class GraveyardInventory implements Inventory {
    private final GraveyardBlockEntity be;

    public GraveyardInventory(GraveyardBlockEntity be) {
        this.be = be;
    }

    @Override public int size() { return GraveyardBlockEntity.TOTAL; }
    @Override public boolean isEmpty() {
        for (int i = 0; i < size(); i++) if (!getStack(i).isEmpty()) return false;
        return true;
    }
    @Override public ItemStack getStack(int slot) { return be == null ? ItemStack.EMPTY : be.getStack(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        // cards are stack size 1 anyway; keep it simple
        if (be == null) return ItemStack.EMPTY;
        ItemStack st = be.getStack(slot);
        if (st.isEmpty()) return ItemStack.EMPTY;
        return be.removeStack(slot);
    }
    @Override public ItemStack removeStack(int slot) {
        if (be == null) return ItemStack.EMPTY;
        return be.removeStack(slot);
    }
    @Override public void setStack(int slot, ItemStack stack) {
        if (be == null) return;
        be.setStack(slot, stack);
    }
    @Override public void markDirty() {}
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return true; }
    @Override public void clear() {
        if (be == null) return;
        for (int i = 0; i < size(); i++) be.setStack(i, ItemStack.EMPTY);
    }
}
