package com.spider.mtgcard.deckcontrol;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public class DeckControlBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    // 6-way facing (UP/DOWN included)
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final EnumProperty<DeckControlWindowColor> COLOR =
            EnumProperty.create("color", DeckControlWindowColor.class);

    public static final MapCodec<DeckControlBlock> CODEC = simpleCodec(DeckControlBlock::new);

    public DeckControlBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(
                this.getStateDefinition().any()
                        .setValue(FACING, Direction.UP) // default doesn't matter much
                        .setValue(LIT, true)
                        .setValue(COLOR, DeckControlWindowColor.DEFAULT)
                        .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, COLOR, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).is(Fluids.WATER);

        // Shulker-style: face = the face you clicked (UP on floor, DOWN on ceiling, etc.)
        Direction face = ctx.getClickedFace();

        return this.defaultBlockState()
                .setValue(FACING, face)
                .setValue(LIT, true)
                .setValue(COLOR, DeckControlWindowColor.DEFAULT)
                .setValue(WATERLOGGED, water);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    private static VoxelShape rotateX90(VoxelShape shape) {
        return rotate(shape, (x1,y1,z1,x2,y2,z2) ->
                Shapes.box(x1, z1, 1 - y2, x2, z2, 1 - y1));
    }

    private static VoxelShape rotateX180(VoxelShape shape) {
        return rotate(shape, (x1,y1,z1,x2,y2,z2) ->
                Shapes.box(x1, 1 - y2, 1 - z2, x2, 1 - y1, 1 - z1));
    }

    private static VoxelShape rotateY90(VoxelShape shape) {
        return rotate(shape, (x1,y1,z1,x2,y2,z2) ->
                Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2));
    }

    private static VoxelShape rotateY180(VoxelShape shape) {
        return rotate(shape, (x1,y1,z1,x2,y2,z2) ->
                Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1));
    }

    private static VoxelShape rotateY270(VoxelShape shape) {
        return rotate(shape, (x1,y1,z1,x2,y2,z2) ->
                Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1));
    }

    @FunctionalInterface
    private interface BoxRot {
        VoxelShape apply(double x1,double y1,double z1,double x2,double y2,double z2);
    }

    private static VoxelShape rotate(VoxelShape shape, BoxRot rot) {
        VoxelShape[] out = new VoxelShape[]{Shapes.empty()};
        shape.forAllBoxes((x1,y1,z1,x2,y2,z2) -> out[0] = Shapes.or(out[0], rot.apply(x1,y1,z1,x2,y2,z2)));
        return out[0].optimize();
    }

    // Add near your OUTLINE_SHAPE
    private static final EnumMap<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), OUTLINE_SHAPE);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), OUTLINE_SHAPE);
    }


    @Override
    public VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }


    // Outline/collision matching the model (symmetric, so no facing rotation needed)
    private static final VoxelShape OUTLINE_SHAPE = Shapes.or(
            Block.box(3.5, 0.0, 3.5, 12.5, 2.0, 12.5),     // base_plinth
            Block.box(4.25, 2.0, 4.25, 11.75, 3.0, 11.75), // base_cap
            Block.box(5.0, 3.0, 5.0, 11.0, 6.0, 11.0),     // lower_pedestal
            Block.box(5.6, 6.0, 5.6, 10.4, 9.2, 10.4),     // waist_taper
            Block.box(6.0, 9.2, 6.0, 10.0, 10.6, 10.0),    // upper_shaft
            Block.box(5.4, 10.6, 5.4, 10.6, 11.4, 10.6),   // lantern_floor
            Block.box(5.6, 11.4, 5.6, 10.4, 14.2, 10.4),   // lantern_body
            Block.box(4.4, 14.2, 4.4, 11.6, 15.0, 11.6),   // roof_main
            Block.box(3.8, 15.0, 3.8, 12.2, 15.6, 12.2),   // roof_overhang
            Block.box(6.7, 15.6, 6.7, 9.3, 16.0, 9.3)      // finial_base
    );

    static {
        SHAPES.put(Direction.UP, OUTLINE_SHAPE);
        SHAPES.put(Direction.DOWN, rotateX180(OUTLINE_SHAPE));

        // Shulker-style: wall facings are X=90 then Y rotation
        VoxelShape wall = rotateX90(OUTLINE_SHAPE); // corresponds to x=90
        SHAPES.put(Direction.NORTH, wall);
        SHAPES.put(Direction.SOUTH, rotateY180(wall));
        SHAPES.put(Direction.WEST, rotateY270(wall));
        SHAPES.put(Direction.EAST, rotateY90(wall));
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            net.minecraft.world.level.LevelReader world,
            net.minecraft.world.level.ScheduledTickAccess tickView,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            net.minecraft.util.RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            tickView.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }


    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeckControlBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.DECK_CONTROL
                ? (w, p, s, be) -> ((DeckControlBlockEntity) be).tick()
                : null;
    }

    // --- Interaction rules ---
    // If holding special items: toggle/set window states, do NOT open GUI.
    // Otherwise: keep your existing behavior (empty hand opens GUI; shift+empty hand shuffles).

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        // If player is empty-hand, keep your existing GUI behavior
        if (player.getMainHandItem().isEmpty()) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof DeckControlBlockEntity dc)) return InteractionResult.PASS;

            if (player.isShiftKeyDown()) {
                dc.shuffle();
                return InteractionResult.CONSUME;
            }

            player.openMenu(dc);
            return InteractionResult.CONSUME;
        }

        // If not empty hand, let onUseWithItem decide (it will PASS for non-handled items)
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                      Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return useWithoutItem(state, world, pos, player, hit);

        Item item = stack.getItem();

        // Any shovel = unlit (keep color stored)
        if (item instanceof ShovelItem) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            if (state.getValue(LIT)) {
                world.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            return InteractionResult.CONSUME;
        }

        // Light sources
        if (item == Items.FLINT_AND_STEEL) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            if (!state.getValue(LIT)) {
                world.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.9f, 1.0f);

                // damage tool
                stack.hurtAndBreak(1, player, hand);
            }
            return InteractionResult.CONSUME;
        }

        if (item == Items.FIRE_CHARGE) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;

            if (!state.getValue(LIT)) {
                world.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.9f, 1.0f);

                if (!player.isCreative()) stack.shrink(1);
            }
            return InteractionResult.CONSUME;
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
        return InteractionResult.PASS;
    }

    private static InteractionResult setColor(Level world, BlockPos pos, BlockState state, DeckControlWindowColor color) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        if (state.getValue(COLOR) != color) {
            world.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);

            // subtle "mode click"
            world.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.35f, 1.5f);
        }
        return InteractionResult.CONSUME;
    }
}


