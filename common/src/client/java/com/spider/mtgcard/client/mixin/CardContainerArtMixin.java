package com.spider.mtgcard.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.client.gui.CardSlotArtRenderer;
import com.spider.mtgcard.client.gui.DeckboxScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge extracts slot image drawing into renderSlotContents after drag/split calculations. */
@Mixin(AbstractContainerScreen.class)
public abstract class CardContainerArtMixin {
    @WrapOperation(method = "renderSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void mtgcard$slotArt(GuiGraphics graphics, ItemStack stack, int x, int y, int seed,
                                Operation<Void> original, GuiGraphics slotGraphics, ItemStack displayedStack, Slot slot, String countString) {
        if (!CardSlotArtRenderer.isArtSlot((AbstractContainerScreen<?>)(Object)this, slot)
                || !CardSlotArtRenderer.renderArt(graphics, stack, x, y)) {
            original.call(graphics, stack, x, y, seed);
        }
    }

    @WrapOperation(method = "renderSlotContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void mtgcard$fakeSlotArt(GuiGraphics graphics, ItemStack stack, int x, int y, int seed,
                                    Operation<Void> original, GuiGraphics slotGraphics, ItemStack displayedStack, Slot slot, String countString) {
        if (!CardSlotArtRenderer.isArtSlot((AbstractContainerScreen<?>)(Object)this, slot)
                || !CardSlotArtRenderer.renderArt(graphics, stack, x, y)) {
            original.call(graphics, stack, x, y, seed);
        }
    }

    @WrapOperation(method = "renderFloatingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void mtgcard$carriedArt(GuiGraphics graphics, ItemStack stack, int x, int y,
                                   Operation<Void> original) {
        Object screen = this;
        if (!(screen instanceof DeckboxScreen || screen instanceof CardDatabaseScreen)
                || !CardSlotArtRenderer.renderArt(graphics, stack, x, y)) {
            original.call(graphics, stack, x, y);
        }
    }
}
