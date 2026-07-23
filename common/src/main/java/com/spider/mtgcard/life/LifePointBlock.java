package com.spider.mtgcard.life;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

public class LifePointBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<LifePointBlock> CODEC = simpleCodec(LifePointBlock::new);

    public LifePointBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level world,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (world.isClientSide()) return null;

        // Manual equivalent of checkType(...)
        if (type == ModBlockEntities.LIFE_POINT) {
            return (w, p, s, be) -> LifePointBlockEntity.tick(w, p, s, (LifePointBlockEntity) be);
        }

        return null;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LifePointBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        var be = world.getBlockEntity(pos);
        if (!(be instanceof LifePointBlockEntity lp)) return InteractionResult.PASS;

        LifePointPackets.openScreen((ServerLevel) world, player, pos, lp);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        var be = world.getBlockEntity(pos);
        return (be instanceof LifePointBlockEntity lp && lp.isTurnActive()) ? 15 : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighborBlock,
                                   Orientation wireOrientation,
                                   boolean notify) {
        if (!world.isClientSide()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                boolean poweredNow = world.hasNeighborSignal(pos);
                boolean poweredBefore = lp.getLastPowered();

                if (!poweredBefore && poweredNow) {
                    if (lp.isTurnActive()) {
                        LifePlayGroups.passTurn((ServerLevel) world, pos);
                    }
                }

                lp.setLastPowered(poweredNow);
            }
        }

        super.neighborChanged(state, world, pos, neighborBlock, wireOrientation, notify);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        if (world.getBlockState(pos).getBlock() != this) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                lp.unlinkAllDisplaysOnBreak(world);
            }
            LifePlayGroups.onMemberBroken(world, pos);
        }

        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);

        if (world instanceof ServerLevel sw) {
            var be = sw.getBlockEntity(pos);
            if (be instanceof LifePointBlockEntity lp) {
                lp.ensureDefaultName(sw);
            }
        }
    }

}
