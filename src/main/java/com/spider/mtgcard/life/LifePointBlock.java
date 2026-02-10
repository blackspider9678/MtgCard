package com.spider.mtgcard.life;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

public class LifePointBlock extends BlockWithEntity {
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final MapCodec<LifePointBlock> CODEC = createCodec(LifePointBlock::new);

    public LifePointBlock(Settings settings) {
        super(settings);
        this.setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (world.isClient()) return null;

        // Manual equivalent of checkType(...)
        if (type == ModBlockEntities.LIFE_POINT) {
            return (w, p, s, be) -> LifePointBlockEntity.tick(w, p, s, (LifePointBlockEntity) be);
        }

        return null;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LifePointBlockEntity(pos, state);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        var be = world.getBlockEntity(pos);
        if (!(be instanceof LifePointBlockEntity lp)) return ActionResult.PASS;

        LifePointPackets.openScreen((ServerWorld) world, player, pos, lp);
        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos, Direction direction) {
        var be = world.getBlockEntity(pos);
        return (be instanceof LifePointBlockEntity lp && lp.isTurnActive()) ? 15 : 0;
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos,
                                  net.minecraft.block.Block neighborBlock,
                                  WireOrientation wireOrientation,
                                  boolean notify) {
        if (!world.isClient()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                boolean poweredNow = world.isReceivingRedstonePower(pos);
                boolean poweredBefore = lp.getLastPowered();

                if (!poweredBefore && poweredNow) {
                    if (lp.isTurnActive()) {
                        LifePlayGroups.passTurn((ServerWorld) world, pos);
                    }
                }

                lp.setLastPowered(poweredNow);
            }
        }

        super.neighborUpdate(state, world, pos, neighborBlock, wireOrientation, notify);
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (world.getBlockState(pos).getBlock() != this) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                lp.unlinkAllDisplaysOnBreak(world);
            }
            LifePlayGroups.onMemberBroken(world, pos);
        }

        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (world instanceof ServerWorld sw) {
            var be = sw.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                lp.ensureDefaultName(sw);
            }
        }
    }

}
