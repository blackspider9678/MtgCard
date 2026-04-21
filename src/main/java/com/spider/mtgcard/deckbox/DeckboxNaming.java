package com.spider.mtgcard.deckbox;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

public final class DeckboxNaming {

    private static final String LEGACY_BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String LEGACY_ITEMS_TAG = "Items";
    private static final String LEGACY_SLOT_TAG = "Slot";
    private static final String LEGACY_STACK_TAG = "Stack";

    private DeckboxNaming() {
    }

    @Nullable
    public static Component getCommanderName(DeckboxBlockEntity deckbox) {
        return getSlotName(deckbox, DeckboxBlockEntity.SECOND_SIDE_SLOT);
    }

    @Nullable
    public static Component getCommanderName(ItemStack deckboxStack) {
        return getSlotName(deckboxStack, DeckboxBlockEntity.SECOND_SIDE_SLOT);
    }

    @Nullable
    public static Component getPartnerName(ItemStack deckboxStack) {
        return getSlotName(deckboxStack, DeckboxBlockEntity.THIRD_SIDE_SLOT);
    }

    @Nullable
    public static Component getSlotName(DeckboxBlockEntity deckbox, int slot) {
        if (deckbox == null || slot < 0 || slot >= deckbox.getContainerSize()) {
            return null;
        }

        return getCardName(deckbox.getItem(slot));
    }

    @Nullable
    public static Component getSlotName(ItemStack deckboxStack, int slot) {
        if (deckboxStack == null || deckboxStack.isEmpty()) {
            return null;
        }

        return getCardName(getStoredSlotStack(deckboxStack, slot));
    }

    @Nullable
    private static Component getCardName(ItemStack cardStack) {
        if (cardStack == null || cardStack.isEmpty()) {
            return null;
        }

        return cardStack.getHoverName().copy();
    }

    private static ItemStack getStoredSlotStack(ItemStack deckboxStack, int slot) {
        ItemStack stack = getSlotFromContainerComponent(deckboxStack, slot);
        if (!stack.isEmpty()) {
            return stack;
        }

        return getSlotFromLegacyCustomData(deckboxStack, slot);
    }

    private static ItemStack getSlotFromContainerComponent(ItemStack deckboxStack, int slot) {
        if (slot < 0 || slot >= DeckboxBlockEntity.INVENTORY_SIZE) {
            return ItemStack.EMPTY;
        }

        ItemContainerContents contents = deckboxStack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return ItemStack.EMPTY;
        }

        NonNullList<ItemStack> items = NonNullList.withSize(DeckboxBlockEntity.INVENTORY_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);
        return items.get(slot);
    }

    private static ItemStack getSlotFromLegacyCustomData(ItemStack deckboxStack, int slot) {
        CustomData customData = deckboxStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag root = customData.copyTag();
        if (root == null) {
            return ItemStack.EMPTY;
        }

        var blockEntityTag = root.getCompound(LEGACY_BLOCK_ENTITY_TAG);
        if (blockEntityTag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var itemsTag = blockEntityTag.get().getList(LEGACY_ITEMS_TAG);
        if (itemsTag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ListTag items = itemsTag.get();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof CompoundTag entry)) {
                continue;
            }

            if (entry.getInt(LEGACY_SLOT_TAG).orElse(-1) != slot) {
                continue;
            }

            return readLegacyStoredStack(entry);
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack readLegacyStoredStack(CompoundTag entry) {
        var storedStack = entry.getCompound(LEGACY_STACK_TAG);
        if (storedStack.isPresent()) {
            return ItemStack.CODEC.parse(NbtOps.INSTANCE, storedStack.get()).result().orElse(ItemStack.EMPTY);
        }

        return ItemStack.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(ItemStack.EMPTY);
    }
}
