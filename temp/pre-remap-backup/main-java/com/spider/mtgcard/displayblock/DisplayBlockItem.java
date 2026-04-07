package com.spider.mtgcard.displayblock;

import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;

public class DisplayBlockItem extends BlockItem {

    public DisplayBlockItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var world = ctx.getLevel();
        var pos = ctx.getClickedPos();

        // Sneak-rightclick the ITEM onto a LifePointBlock to bind it
        if (!world.isClientSide() && ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity) {
                ItemStack stack = ctx.getItemInHand();

                CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
                CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();

                tag.putString(DisplayBlockEntity.NBT_LINK_DIM, world.dimension().identifier().toString());
                tag.putLong(DisplayBlockEntity.NBT_LINK_POS, pos.asLong());

                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                // small confirmation sound
                world.playSound(
                        null, pos,
                        SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.BLOCKS,
                        0.7f, 1.2f
                );

                return InteractionResult.SUCCESS;
            }
        }

        return super.useOn(ctx);
    }
}
