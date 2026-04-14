package com.spider.mtgcard.db;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;

public class CardDatabaseBlock extends BaseEntityBlock {
    public static final MapCodec<CardDatabaseBlock> CODEC = simpleCodec(CardDatabaseBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CardDatabaseBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardDatabaseBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        MenuProvider factory = getMenuProvider(state, world, pos);
        if (factory != null) {
            player.openMenu(factory);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return new ExtendedScreenHandlerFactory<>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer player) {
                return pos;
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.mtgcard.card_database");
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInv, Player player) {
                if (!(player instanceof ServerPlayer sp)) {
                    return new CardDatabaseScreenHandler(syncId, playerInv, pos);
                }

                CardDBSession session = CardDBSession.forPlayer(sp);
                session.ensureLoaded(sp);
                session.setWindowOffset(0);
                return new CardDatabaseScreenHandler(syncId, playerInv, session, pos);
            }
        };
    }
}
