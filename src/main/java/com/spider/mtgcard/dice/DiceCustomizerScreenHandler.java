package com.spider.mtgcard.dice;

import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;
import java.util.function.Predicate;

public class DiceCustomizerScreenHandler extends AbstractContainerMenu {
    public static final int GUI_W = 360;
    public static final int GUI_H = 276;

    public static final int MATERIAL_SLOT = 0;
    public static final int FOIL_SLOT = 1;
    public static final int PATTERN_SLOT = 2;
    public static final int INGREDIENT_SLOT_COUNT = 3;
    public static final int PLAYER_INV_START = INGREDIENT_SLOT_COUNT;

    public static final int PLAYER_INV_X = 99;
    public static final int PLAYER_INV_Y = 190;

    private final SimpleContainer ingredients;
    private boolean crafting;

    public DiceCustomizerScreenHandler(int syncId, Inventory playerInv) {
        super(ModScreenHandlers.DICE_CUSTOMIZER, syncId);
        this.ingredients = new SimpleContainer(INGREDIENT_SLOT_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                DiceCustomizerScreenHandler.this.slotsChanged(this);
            }
        };

        addSlot(new IngredientSlot(ingredients, MATERIAL_SLOT, 158, 144, DiceCustomizerIngredients::isMaterial, 64));
        addSlot(new IngredientSlot(ingredients, FOIL_SLOT, 236, 144, DiceCustomizerIngredients::isFoil, 64));
        addSlot(new IngredientSlot(ingredients, PATTERN_SLOT, 262, 144, DiceCustomizerIngredients::isBannerPattern, 1));

        addPlayerSlots(playerInv, PLAYER_INV_X, PLAYER_INV_Y);
    }

    public ItemStack getIngredientStack(int index) {
        return ingredients.getItem(index);
    }

    public boolean craft(ServerPlayer player, DiceAppearance requested, int requestedSides) {
        if (crafting) {
            return false;
        }

        crafting = true;
        try {
            if (!stillValid(player)) {
                return false;
            }

            Optional<DiceType> type = DiceType.bySides(requestedSides);
            if (type.isEmpty()) {
                return false;
            }

            if (!hasAtLeast(MATERIAL_SLOT, DiceCustomizerIngredients::isMaterial)) {
                return false;
            }

            boolean foil = !ingredients.getItem(FOIL_SLOT).isEmpty();
            if (foil && !DiceCustomizerIngredients.isFoil(ingredients.getItem(FOIL_SLOT))) {
                return false;
            }

            ItemStack patternStack = ingredients.getItem(PATTERN_SLOT);
            Optional<net.minecraft.resources.Identifier> bannerPattern = Optional.empty();
            if (!patternStack.isEmpty()) {
                if (!DiceCustomizerIngredients.isBannerPattern(patternStack)) {
                    return false;
                }
                bannerPattern = DicePatternResolver.resolveBannerPatternAsset(player.registryAccess(), patternStack);
                if (bannerPattern.isEmpty()) {
                    return false;
                }
            }

            DiceAppearance base = requested == null ? DiceAppearance.DEFAULT : requested;
            DiceAppearance appearance = base.withServerControlledEffects(false, foil, 0L, bannerPattern);

            ItemStack result = new ItemStack(type.get().item(), 1);
            result.set(ModDataComponents.DICE_APPEARANCE, appearance);
            if (foil) {
                result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            if (!canReceive(player.getInventory(), result)) {
                return false;
            }

            consumeOne(MATERIAL_SLOT);
            if (foil) consumeOne(FOIL_SLOT);

            ItemStack toGive = result.copy();
            if (!player.getInventory().add(toGive)) {
                player.drop(result, false);
            }
            broadcastChanges();
            return true;
        } finally {
            crafting = false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return playerNearLoom(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex < INGREDIENT_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoIngredientSlot(stack)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            clearContainer(player, ingredients);
        }
    }

    public static boolean playerNearLoom(Player player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8))) {
            if (player.level().getBlockState(pos).is(Blocks.LOOM) && isWithinEightBlocks(player, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithinEightBlocks(Player player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    private void addPlayerSlots(Inventory playerInv, int left, int top) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }

        int hotbarTop = top + 58;
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInv, col, left + col * 18, hotbarTop));
        }
    }

    private boolean moveIntoIngredientSlot(ItemStack stack) {
        if (DiceCustomizerIngredients.isMaterial(stack)) {
            return moveItemStackTo(stack, MATERIAL_SLOT, MATERIAL_SLOT + 1, false);
        }
        if (DiceCustomizerIngredients.isFoil(stack)) {
            return moveItemStackTo(stack, FOIL_SLOT, FOIL_SLOT + 1, false);
        }
        if (DiceCustomizerIngredients.isBannerPattern(stack)) {
            return moveItemStackTo(stack, PATTERN_SLOT, PATTERN_SLOT + 1, false);
        }
        return false;
    }

    private boolean hasAtLeast(int slot, Predicate<ItemStack> predicate) {
        ItemStack stack = ingredients.getItem(slot);
        return !stack.isEmpty() && predicate.test(stack);
    }

    private void consumeOne(int slot) {
        ingredients.removeItem(slot, 1);
        ingredients.setChanged();
    }

    private static boolean canReceive(Inventory inventory, ItemStack result) {
        return inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(result) >= 0;
    }

    private static final class IngredientSlot extends Slot {
        private final Predicate<ItemStack> predicate;
        private final int maxStackSize;

        private IngredientSlot(Container container, int slot, int x, int y, Predicate<ItemStack> predicate, int maxStackSize) {
            super(container, slot, x, y);
            this.predicate = predicate;
            this.maxStackSize = maxStackSize;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return predicate.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return maxStackSize;
        }
    }
}
