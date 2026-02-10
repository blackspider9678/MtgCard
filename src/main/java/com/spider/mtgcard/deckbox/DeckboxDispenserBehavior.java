// src/main/java/com/spider/mtgcard/registry/DeckboxDispenserBehavior.java
package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.BlockPlacementDispenserBehavior;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class DeckboxDispenserBehavior extends BlockPlacementDispenserBehavior {

    @Override
    protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
        // Let vanilla place it first
        ItemStack result = super.dispenseSilently(pointer, stack);

        Direction dispFacing = pointer.state().get(DispenserBlock.FACING);
        BlockPos placedPos = pointer.pos().offset(dispFacing);

        BlockState placed = pointer.world().getBlockState(placedPos);
        if (placed.isOf(ModBlocks.DECKBOX) && placed.contains(DeckboxBlock.FACING)) {
            pointer.world().setBlockState(
                    placedPos,
                    placed.with(DeckboxBlock.FACING, dispFacing).with(DeckboxBlock.OPEN, false),
                    Block.NOTIFY_ALL
            );
        }

        return result;
    }
}
