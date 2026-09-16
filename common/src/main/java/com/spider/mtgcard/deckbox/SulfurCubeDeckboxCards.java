package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.api.DeckboxStorage;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-side card ejection for deckboxes swallowed by sulfur cubes. */
public final class SulfurCubeDeckboxCards {
    private SulfurCubeDeckboxCards() {
    }

    public static boolean hasDeckbox(SulfurCube cube) {
        return cube != null && ModBlocks.isDeckbox(cube.getItemBySlot(EquipmentSlot.BODY));
    }

    public static void dropRandomCard(SulfurCube cube, ServerLevel level) {
        if (!hasDeckbox(cube)) return;

        ItemStack deckbox = cube.getItemBySlot(EquipmentSlot.BODY);
        ItemStack card = removeFromExternalStorage(deckbox, level, cube.getRandom().nextInt())
                .orElseGet(() -> removeFromContainerComponent(cube, deckbox));
        if (card.isEmpty()) return;

        cube.spawnAtLocation(level, card);
    }

    private static Optional<ItemStack> removeFromExternalStorage(ItemStack deckbox, ServerLevel level, int randomValue) {
        UUID id = storageId(deckbox);
        if (id == null) return Optional.empty();

        Optional<DeckboxStorage.Record> stored = DeckboxStorage.load(level.getServer(), level.registryAccess(), id);
        if (stored.isEmpty()) return Optional.empty();

        DeckboxStorage.Record record = stored.get();
        ItemStack removed = removeRandomCard(record.items(), randomValue);
        if (removed.isEmpty()) return Optional.of(ItemStack.EMPTY);

        DeckboxStorage.save(level.getServer(), level.registryAccess(), id, record.type(), record.name(), record.items());
        return Optional.of(removed);
    }

    private static ItemStack removeFromContainerComponent(SulfurCube cube, ItemStack deckbox) {
        ItemContainerContents contents = deckbox.get(DataComponents.CONTAINER);
        if (contents == null) return ItemStack.EMPTY;

        NonNullList<ItemStack> items = NonNullList.withSize(DeckboxBlockEntity.INVENTORY_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);
        ItemStack removed = removeRandomCard(items, cube.getRandom().nextInt());
        if (removed.isEmpty()) return ItemStack.EMPTY;

        ItemStack updated = deckbox.copy();
        updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        cube.setItemSlot(EquipmentSlot.BODY, updated);
        return removed;
    }

    private static ItemStack removeRandomCard(NonNullList<ItemStack> items, int randomValue) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && stack.is(ModItemTags.TCG_CARD)) occupied.add(slot);
        }
        if (occupied.isEmpty()) return ItemStack.EMPTY;

        int slot = occupied.get(Math.floorMod(randomValue, occupied.size()));
        ItemStack stored = items.get(slot);
        ItemStack removed = stored.copyWithCount(1);
        stored.shrink(1);
        if (stored.isEmpty()) items.set(slot, ItemStack.EMPTY);
        return removed;
    }

    private static UUID storageId(ItemStack deckbox) {
        CustomData customData = deckbox.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        String value = tag.getString(DeckboxStorage.ITEM_ID_KEY).orElse("");
        if (value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
