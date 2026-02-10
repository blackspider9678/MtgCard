package com.spider.mtgcard.displayblock;

import com.spider.mtgcard.life.LifePointBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;

public class DisplayBlockItem extends BlockItem {

    public DisplayBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        var world = ctx.getWorld();
        var pos = ctx.getBlockPos();

        // Sneak-rightclick the ITEM onto a LifePointBlock to bind it
        if (!world.isClient() && ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity) {
                ItemStack stack = ctx.getStack();

                NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
                NbtCompound tag = existing == null ? new NbtCompound() : existing.copyNbt();

                tag.putString(DisplayBlockEntity.NBT_LINK_DIM, world.getRegistryKey().getValue().toString());
                tag.putLong(DisplayBlockEntity.NBT_LINK_POS, pos.asLong());

                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

                // small confirmation sound
                world.playSound(
                        null, pos,
                        SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                        SoundCategory.BLOCKS,
                        0.7f, 1.2f
                );

                return ActionResult.SUCCESS;
            }
        }

        return super.useOnBlock(ctx);
    }
}
