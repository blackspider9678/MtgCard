package com.spider.mtgcard.deckcontrol;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShovelItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DeckControlBlock extends BlockWithEntity implements Waterloggable {

    public static final EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;


    // Window state
    public static final BooleanProperty LIT = BooleanProperty.of("lit");
    public static final EnumProperty<DeckControlWindowColor> COLOR =
            EnumProperty.of("color", DeckControlWindowColor.class);

    // ✅ Required by BlockWithEntity in modern versions
    public static final MapCodec<DeckControlBlock> CODEC = createCodec(DeckControlBlock::new);

    // Outline/collision matching the model (symmetric, so no facing rotation needed)
    private static final VoxelShape OUTLINE_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(3.5, 0.0, 3.5, 12.5, 2.0, 12.5),     // base_plinth
            Block.createCuboidShape(4.25, 2.0, 4.25, 11.75, 3.0, 11.75), // base_cap
            Block.createCuboidShape(5.0, 3.0, 5.0, 11.0, 6.0, 11.0),     // lower_pedestal
            Block.createCuboidShape(5.6, 6.0, 5.6, 10.4, 9.2, 10.4),     // waist_taper
            Block.createCuboidShape(6.0, 9.2, 6.0, 10.0, 10.6, 10.0),    // upper_shaft
            Block.createCuboidShape(5.4, 10.6, 5.4, 10.6, 11.4, 10.6),   // lantern_floor
            Block.createCuboidShape(5.6, 11.4, 5.6, 10.4, 14.2, 10.4),   // lantern_body
            Block.createCuboidShape(4.4, 14.2, 4.4, 11.6, 15.0, 11.6),   // roof_main
            Block.createCuboidShape(3.8, 15.0, 3.8, 12.2, 15.6, 12.2),   // roof_overhang
            Block.createCuboidShape(6.7, 15.6, 6.7, 9.3, 16.0, 9.3)      // finial_base
    );

    public DeckControlBlock(Settings settings) {
        super(settings);
        this.setDefaultState(
                this.getStateManager().getDefaultState()
                        .with(FACING, Direction.NORTH)
                        .with(LIT, true)
                        .with(COLOR, DeckControlWindowColor.DEFAULT)
                        .with(WATERLOGGED, false)
        );
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, COLOR, WATERLOGGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        boolean water = ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER);
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(LIT, true)
                .with(COLOR, DeckControlWindowColor.DEFAULT)
                .with(WATERLOGGED, water);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(
            BlockState state,
            net.minecraft.world.WorldView world,
            net.minecraft.world.tick.ScheduledTickView tickView,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            net.minecraft.util.math.random.Random random
    ) {
        if (state.get(WATERLOGGED)) {
            tickView.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }


    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DeckControlBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.DECK_CONTROL
                ? (w, p, s, be) -> ((DeckControlBlockEntity) be).tick()
                : null;
    }

    // Shapes
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public VoxelShape getCullingShape(BlockState state) {
        return VoxelShapes.empty();
    }

    // --- Interaction rules ---
    // If holding special items: toggle/set window states, do NOT open GUI.
    // Otherwise: keep your existing behavior (empty hand opens GUI; shift+empty hand shuffles).

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // If player is empty-hand, keep your existing GUI behavior
        if (player.getMainHandStack().isEmpty()) {
            if (world.isClient()) return ActionResult.SUCCESS;

            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof DeckControlBlockEntity dc)) return ActionResult.PASS;

            if (player.isSneaking()) {
                dc.shuffle();
                return ActionResult.CONSUME;
            }

            player.openHandledScreen(dc);
            return ActionResult.CONSUME;
        }

        // If not empty hand, let onUseWithItem decide (it will PASS for non-handled items)
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                      PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return onUse(state, world, pos, player, hit);

        Item item = stack.getItem();

        // Any shovel = unlit (keep color stored)
        if (item instanceof ShovelItem) {
            if (world.isClient()) return ActionResult.SUCCESS;

            if (state.get(LIT)) {
                world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 1.0f);
            }
            return ActionResult.CONSUME;
        }

        // Light sources
        if (item == Items.FLINT_AND_STEEL) {
            if (world.isClient()) return ActionResult.SUCCESS;

            if (!state.get(LIT)) {
                world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.9f, 1.0f);

                // damage tool
                stack.damage(1, player, hand);
            }
            return ActionResult.CONSUME;
        }

        if (item == Items.FIRE_CHARGE) {
            if (world.isClient()) return ActionResult.SUCCESS;

            if (!state.get(LIT)) {
                world.setBlockState(pos, state.with(LIT, true), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.9f, 1.0f);

                if (!player.isCreative()) stack.decrement(1);
            }
            return ActionResult.CONSUME;
        }

        // Color changers (do NOT force lit; just store color and keep lit/unlit state)
        // NOTE: You said "netherrack = normal"; mapping that to DEFAULT.
        if (item == Items.NETHERRACK) {
            return setColor(world, pos, state, DeckControlWindowColor.DEFAULT);
        }
        if (item == Items.SOUL_SAND) {
            return setColor(world, pos, state, DeckControlWindowColor.SOULFIRE);
        }
        if (item == Items.REDSTONE) {
            return setColor(world, pos, state, DeckControlWindowColor.REDSTONE);
        }

        // You said "copper nugget" — vanilla doesn’t have that item.
        // If your mod defines one, replace this with your item reference.
        // For now, support vanilla COPPER_INGOT as a fallback.
        if (item == Items.COPPER_NUGGET) {
            return setColor(world, pos, state, DeckControlWindowColor.COPPER);
        }

        // Not a handled item: let normal item behavior happen (and don't open GUI)
        return ActionResult.PASS;
    }

    private static ActionResult setColor(World world, BlockPos pos, BlockState state, DeckControlWindowColor color) {
        if (world.isClient()) return ActionResult.SUCCESS;

        if (state.get(COLOR) != color) {
            world.setBlockState(pos, state.with(COLOR, color), Block.NOTIFY_ALL);

            // subtle "mode click"
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.35f, 1.5f);
        }
        return ActionResult.CONSUME;
    }
}
