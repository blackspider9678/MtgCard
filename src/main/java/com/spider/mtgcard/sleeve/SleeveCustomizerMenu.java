package com.spider.mtgcard.sleeve;

import com.spider.mtgcard.api.SleeveApplication;
import com.spider.mtgcard.api.SleeveRegistry;
import com.spider.mtgcard.api.CardItemRegistry;
import com.spider.mtgcard.api.CardSleeves;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class SleeveCustomizerMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int RESULT_SLOT = 1;
    public static final int PLAYER_INV_START = 2;
    private final SimpleContainer input;
    private final ResultContainer result = new ResultContainer();
    private final Player player;
    private final DataSlot selected = DataSlot.standalone(); // -1 = mixed/no selection, 0 = No Sleeve

    public SleeveCustomizerMenu(int containerId, Inventory inventory) {
        super(ModScreenHandlers.SLEEVE_CUSTOMIZER, containerId);
        this.player = inventory.player;
        this.input = new SimpleContainer(1) {
            @Override public void setChanged() { super.setChanged(); refreshSelection(); }
        };
        addSlot(new Slot(input, 0, 20, 33) {
            @Override public boolean mayPlace(ItemStack stack) { return SleeveApplication.isCompatibleInput(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(result, 0, 143, 33) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public void onTake(Player player, ItemStack taken) {
                if (player instanceof ServerPlayer serverPlayer && !input().isEmpty() && selected.get() >= 0) {
                    var sleeves = SleeveRegistry.values();
                    var sleeveId = selected.get() == 0 ? null : sleeves.get(selected.get() - 1).id();
                    // Deckbox contents are mutated only when its output is actually taken.
                    SleeveApplication.apply(serverPlayer.level().getServer(), serverPlayer.registryAccess(), input(), sleeveId);
                    input.removeItem(0, 1);
                    input.setChanged();
                }
                super.onTake(player, taken);
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlot(selected);
        selected.set(-1);
    }

    public ItemStack input() { return input.getItem(0); }
    public int selectedIndex() { return selected.get(); }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id <= SleeveRegistry.values().size()) {
            selected.set(id);
            updateResult();
            broadcastChanges();
            return true;
        }
        return false;
    }

    private void refreshSelection() {
        if (!(player instanceof ServerPlayer serverPlayer) || input().isEmpty()) {
            selected.set(-1);
            result.setItem(0, ItemStack.EMPTY);
            return;
        }
        var state = SleeveApplication.current(serverPlayer.level().getServer(), serverPlayer.registryAccess(), input());
        if (state.state() == SleeveApplication.CurrentState.NONE) selected.set(0);
        else if (state.state() == SleeveApplication.CurrentState.ONE_SLEEVE) {
            int index = -1;
            var values = SleeveRegistry.values();
            for (int i = 0; i < values.size(); i++) if (state.sleeveId().filter(values.get(i).id()::equals).isPresent()) { index = i + 1; break; }
            selected.set(index);
        } else selected.set(-1);
        updateResult();
    }

    private void updateResult() {
        if (input().isEmpty() || selected.get() < 0 || selected.get() > SleeveRegistry.values().size()) {
            result.setItem(0, ItemStack.EMPTY);
            return;
        }
        ItemStack output = input().copyWithCount(1);
        if (CardItemRegistry.isCard(output)) {
            if (selected.get() == 0) CardSleeves.clear(output);
            else CardSleeves.set(output, SleeveRegistry.values().get(selected.get() - 1).id());
        }
        result.setItem(0, output);
    }

    @Override public boolean stillValid(Player player) {
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-8, -8, -8), player.blockPosition().offset(8, 8, 8)))
            if (player.level().getBlockState(pos).is(Blocks.LOOM) && player.distanceToSqr(pos.getCenter()) <= 64) return true;
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy(), moving = slot.getItem();
        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(moving, PLAYER_INV_START, slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(moving, original);
        } else if (index == INPUT_SLOT) {
            if (!moveItemStackTo(moving, PLAYER_INV_START, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!SleeveApplication.isCompatibleInput(moving) || !moveItemStackTo(moving, INPUT_SLOT, INPUT_SLOT + 1, false)) return ItemStack.EMPTY;
        }
        if (moving.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (index == RESULT_SLOT) slot.onTake(player, original);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, input);
    }
}
