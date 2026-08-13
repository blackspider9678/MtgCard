// src/main/java/com/spider/mtgcard/cardstore/CardStoreBlock.java
package com.spider.mtgcard.cardstore;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CardStoreBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<CardStoreBlock> CODEC = simpleCodec(CardStoreBlock::new);

    public CardStoreBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardStoreBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                            Player player, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        if (!MtgcardConfig.cardStoreEnabled()) {
            player.displayClientMessage(Component.literal("Card Store is unavailable."), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MenuProvider factory) {
            player.openMenu(factory);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CardStoreBlockEntity cs) {
            cs.flushQueueOutFront();
        }
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }


    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (type == ModBlockEntities.CARD_STORE) {
            return (w, p, s, be) -> CardStoreBlockEntity.tick(w, p, s, (CardStoreBlockEntity) be);
        }
        return null;
    }
}

