package com.spider.mtgcard.client.compat.mousetweaks;

import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.db.CardDatabaseDebug;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import yalter.mousetweaks.api.IMTModGuiContainer3Ex;

import java.util.List;

@Mixin(CardDatabaseScreen.class)
public abstract class CardDatabaseScreenMouseTweaksMixin
        extends LegacyContainerScreen<CardDatabaseScreenHandler>
        implements IMTModGuiContainer3Ex {
    private static final int DB_WINDOW_SLOTS = CardDatabaseScreenHandler.DB_ROWS * CardDatabaseScreenHandler.DB_COLS;

    private CardDatabaseScreenMouseTweaksMixin(CardDatabaseScreenHandler menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public boolean MT_isMouseTweaksDisabled() {
        return false;
    }

    @Override
    public boolean MT_isWheelTweakDisabled() {
        return false;
    }

    @Override
    public List<Slot> MT_getSlots() {
        return this.menu.slots;
    }

    @Override
    public Slot MT_getSlotUnderMouse(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot.isActive() && this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    @Override
    public boolean MT_isCraftingOutput(Slot slot) {
        return false;
    }

    @Override
    public boolean MT_isIgnored(Slot slot) {
        boolean ignored = slot != null && slot.index >= 0 && slot.index < DB_WINDOW_SLOTS;
        if (ignored) {
            CardDatabaseDebug.log("[CardDBDebug] client MouseTweaks ignoring DB slot index={}", slot.index);
        }
        return ignored;
    }

    @Override
    public boolean MT_disableRMBDraggingFunctionality() {
        boolean wasQuickCrafting = this.isQuickCrafting;
        this.clearDraggingState();
        return wasQuickCrafting;
    }

    @Override
    public void MT_clickSlot(Slot slot, int button, ClickType action) {
        CardDatabaseDebug.log("[CardDBDebug] client MouseTweaks MT_clickSlot slot={} button={} action={}",
                slot == null ? -999 : slot.index, button, action);
        this.slotClicked(slot, slot == null ? -999 : slot.index, button, action);
    }
}
