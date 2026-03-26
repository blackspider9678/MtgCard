// src/main/java/com/spider/mtgcard/registry/DeckboxDispenserBehavior.java
package com.spider.mtgcard.deckbox;

import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.core.dispenser.ShulkerBoxDispenseBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class DeckboxDispenserBehavior extends ShulkerBoxDispenseBehavior {

    @Override
    protected ItemStack execute(BlockSource pointer, ItemStack stack) {
        // Let vanilla place it first
        ItemStack result = super.execute(pointer, stack);

        Direction dispFacing = pointer.state().getValue(DispenserBlock.FACING);
        BlockPos placedPos = pointer.pos().relative(dispFacing);

        BlockState placed = pointer.level().getBlockState(placedPos);
        if (placed.is(ModBlocks.DECKBOX) && placed.hasProperty(DeckboxBlock.FACING)) {
            pointer.level().setBlock(
                    placedPos,
                    placed.setValue(DeckboxBlock.FACING, dispFacing).setValue(DeckboxBlock.OPEN, false),
                    Block.UPDATE_ALL
            );
        }

        return result;
    }
}
