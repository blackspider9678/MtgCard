// com/spider/mtgcard/graveyard/GraveyardBlock.java
package com.spider.mtgcard.graveyard;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class GraveyardBlock extends BlockWithEntity {

    public static final BooleanProperty OPEN = BooleanProperty.of("open");

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    // Newer API wants a codec on BlockWithEntity blocks
    public static final MapCodec<GraveyardBlock> CODEC = createCodec(GraveyardBlock::new);

    public GraveyardBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(
                this.getStateManager().getDefaultState()
                        .with(FACING, Direction.NORTH)
                        .with(OPEN, false)
        );
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Most “machine” blocks face the player (front toward player),
        // so we use opposite of player facing.
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return this.getDefaultState()
                .with(FACING, facing)
                .with(OPEN, false);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(OPEN);
        builder.add(FACING);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GraveyardBlockEntity(pos, state);
    }

    /**
     * Newer mappings: use onUseWithItem instead of onUse.
     * This is called for item-in-hand interactions, but it also works fine for empty hand.
     */
    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ExtendedScreenHandlerFactory<?> factory && player instanceof ServerPlayerEntity sp) {
            sp.openHandledScreen(factory);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos, Direction direction) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof GraveyardBlockEntity gy)) return 0;

        int filled = 0;
        for (int i = 0; i < 100; i++) { // graveyard only
            if (!gy.getStack(i).isEmpty()) filled++;
        }

        // scale 0..100 → 0..15
        return Math.min(15, (int) Math.floor((filled / 100.0) * 15.0));
    }
}
