// com/spider/mtgcard/db/CardDatabaseBlock.java
package com.spider.mtgcard.db;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CardDatabaseBlock extends BlockWithEntity {
    public static final MapCodec<CardDatabaseBlock> CODEC = createCodec(CardDatabaseBlock::new);

    // ✅ add facing property to match blockstates/card_database.json
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;


    public CardDatabaseBlock(Settings settings) {
        super(settings);
        // ✅ default state must include FACING
        this.setDefaultState(this.getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override public MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    // ✅ ensure the property exists on the block state
    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    // ✅ set facing on placement
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // common: block faces player
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CardDatabaseBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        NamedScreenHandlerFactory factory = createScreenHandlerFactory(state, world, pos);
        if (factory != null) player.openHandledScreen(factory);
        return ActionResult.CONSUME;
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BlockPos>() {
            @Override public Text getDisplayName() {
                return Text.translatable("screen.mtgcard.card_database");
            }
            @Override public BlockPos getScreenOpeningData(net.minecraft.server.network.ServerPlayerEntity player) {
                return pos;
            }
            @Override public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
                var sp = (net.minecraft.server.network.ServerPlayerEntity) player;
                var session = CardDBSession.forPlayer(sp);
                session.ensureLoaded(sp);
                session.setWindowOffset(0);
                return new CardDatabaseScreenHandler(syncId, playerInv, session, pos);
            }
        };
    }
}
