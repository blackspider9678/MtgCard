package com.spider.mtgcard.mixin;

import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents nested deckbox contents from being copied into initial chunk packets. */
@Mixin(BlockEntity.class)
public abstract class BlockEntityChunkSyncMixin {
    @Inject(method = "getUpdateTag", at = @At("HEAD"), cancellable = true)
    private void mtgcard$omitNestedDeckboxesFromChunkSync(
            HolderLookup.Provider registries,
            CallbackInfoReturnable<CompoundTag> cir
    ) {
        if (!((Object) this instanceof Container container)) return;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof DeckboxBlockItem) {
                // Container menus synchronize their slots when opened. The initial
                // chunk packet does not need a second copy of the nested inventory.
                cir.setReturnValue(new CompoundTag());
                return;
            }
        }
    }
}
