package com.spider.mtgcard.deckbox;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DeckboxBlock extends BlockWithEntity implements Waterloggable {

    public static final MapCodec<DeckboxBlock> CODEC = createCodec(DeckboxBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final EnumProperty<Direction> FACING = Properties.FACING;

    // "spin" is the horizontal orientation used when FACING is UP/DOWN
    public static final EnumProperty<Direction> SPIN = EnumProperty.of(
            "spin",
            Direction.class,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    );

    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;


    public DeckboxBlock(Settings settings) {
        super(settings);
        this.setDefaultState(
                this.getStateManager().getDefaultState()
                        .with(FACING, Direction.NORTH)
                        .with(SPIN, Direction.NORTH)
                        .with(OPEN, false)
                        .with(WATERLOGGED, false)
        );

    }

    @Override
    protected boolean isTransparent(BlockState state) {
        return true;
    }

    @Override
    protected boolean isShapeFullCube(BlockState state, BlockView world, BlockPos pos) {
        return false;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        boolean water = ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER);

        // Look direction: if player looks DOWN, this becomes UP (box faces up)
        Direction facing = ctx.getPlayerLookDirection().getOpposite();

        // Horizontal “spin” always follows the player yaw (opposite = faces player)
        Direction spin = ctx.getHorizontalPlayerFacing().getOpposite();

        return this.getDefaultState()
                .with(FACING, facing)
                .with(SPIN, spin)
                .with(OPEN, false)
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
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        Direction f = state.get(FACING);
        Direction s = state.get(SPIN);

        // Always rotate spin around Y
        s = rotation.rotate(s);

        // Only rotate facing if it is horizontal (UP/DOWN shouldn't change from a Y-rotation)
        if (f.getAxis().isHorizontal()) {
            f = rotation.rotate(f);
        }

        return state.with(FACING, f).with(SPIN, s);
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        Direction f = state.get(FACING);
        Direction s = state.get(SPIN);

        BlockRotation rot = mirror.getRotation(s);
        s = rot.rotate(s);

        if (f.getAxis().isHorizontal()) {
            f = mirror.getRotation(f).rotate(f);
        }

        return state.with(FACING, f).with(SPIN, s);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN, OPEN, WATERLOGGED);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DeckboxBlockEntity(pos, state);
    }

    /* ---------------- Comparator output ---------------- */

    @Override
    protected boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos, Direction direction) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeckboxBlockEntity deckbox) {
            return deckbox.getComparatorLevelMainOnly();
        }
        return 0;
    }

    /* ---------------- Use (dye or open) ---------------- */

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, BlockHitResult hit) {

        if (world.isClient()) return ActionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeckboxBlockEntity deckbox) {
            player.openHandledScreen(deckbox);
            return ActionResult.CONSUME;
        }

        return ActionResult.PASS;
    }

    @Override
    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return (be instanceof DeckboxBlockEntity dbe) ? dbe : null;
    }

    /* ---------------- Player break suppression ---------------- */

    // ✅ Your mappings: onBreak returns BlockState (not void)
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockEntity be = world.getBlockEntity(pos);

        if (!world.isClient() && be instanceof DeckboxBlockEntity deckbox && world instanceof ServerWorld sw) {
            ItemStack drop = new ItemStack(ModBlocks.DECKBOX_ITEM);

            NbtCompound beTag = buildBlockEntityTag(deckbox);
            NbtCompound carrier = new NbtCompound();
            carrier.put("BlockEntityTag", beTag);
            drop.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(carrier));

            ItemScatterer.spawn(sw, pos.getX(), pos.getY(), pos.getZ(), drop);

            deckbox.clearForDropNoSync();
            sw.removeBlockEntity(pos);

            // IMPORTANT: return state directly (do NOT call super) so vanilla doesn't also drop an empty item
            return state;
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos,
                           BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // DO NOT remove PLAYER_BREAKING here.
        // In this version/mappings, afterBreak can run before onStateReplaced.
        super.afterBreak(world, player, pos, state, blockEntity, tool);
    }

    /**
     * Stop vanilla from spilling inventory.
     * - Player break: do NOT call super() (that's where spill happens), keep BE for loot to read.
     * - Other removals: clear inventory then call super() so there's nothing to spill.
     */
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        // If we already cleared inventory in onBreak, this won't spill anything.
        // And it will still handle comparator updates properly.
        ItemScatterer.onStateReplaced(state, world, pos);
        super.onStateReplaced(state, world, pos, moved);
    }



    /* ---------------- Restore from item on place ---------------- */

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!world.isClient()) {
            var comp = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (comp != null) {
                NbtCompound tag = comp.copyNbt();
                if (tag != null && tag.contains("BlockEntityTag")) {
                    var beTagOpt = tag.getCompound("BlockEntityTag");
                    if (beTagOpt.isPresent()) {
                        NbtCompound beTag = beTagOpt.get();

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof DeckboxBlockEntity deckbox) {

                            var itemsOpt = beTag.getList("Items");
                            if (itemsOpt.isPresent()) {
                                NbtList list = itemsOpt.get();
                                for (int i = 0; i < list.size(); i++) {
                                    var entryOpt = list.getCompound(i);
                                    if (entryOpt.isEmpty()) continue;

                                    NbtCompound st = entryOpt.get();
                                    int slot = st.getInt("Slot").orElse(-1);
                                    if (slot < 0 || slot >= DeckboxBlockEntity.INVENTORY_SIZE) continue;

                                    ItemStack stackToSet = ItemStack.EMPTY;
                                    var innerOpt = st.getCompound("Stack");
                                    if (innerOpt.isPresent()) {
                                        stackToSet = ItemStack.CODEC.parse(NbtOps.INSTANCE, innerOpt.get())
                                                .result().orElse(ItemStack.EMPTY);
                                    } else {
                                        stackToSet = ItemStack.CODEC.parse(NbtOps.INSTANCE, st)
                                                .result().orElse(ItemStack.EMPTY);
                                    }

                                    deckbox.setStack(slot, stackToSet);
                                }
                            }

                            int tint = beTag.getInt("Tint").orElse(0xFFFFFF);
                            deckbox.setRgbTint(tint);
                            deckbox.sync();
                        }
                    }
                }
            }
        }

        super.onPlaced(world, pos, state, placer, stack);
    }

    /* ---------------- helpers ---------------- */

    private static NbtCompound buildBlockEntityTag(DeckboxBlockEntity deckbox) {
        NbtCompound beTag = new NbtCompound();
        NbtList list = new NbtList();

        for (int i = 0; i < DeckboxBlockEntity.INVENTORY_SIZE; i++) {
            ItemStack s = deckbox.getStack(i);
            if (!s.isEmpty()) {
                NbtCompound st = new NbtCompound();
                st.putInt("Slot", i);

                var encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, s).result();
                if (encoded.isPresent() && encoded.get() instanceof NbtCompound stackTag) {
                    st.put("Stack", stackTag);
                }
                list.add(st);
            }
        }

        beTag.put("Items", list);
        beTag.putInt("Tint", deckbox.getRgbTint());
        return beTag;
    }

    // 1px inset cube (covers “bulk”, still looks like it fits the model)
    private static final VoxelShape OUTLINE_INSET_1PX =
            Block.createCuboidShape(1.0, 1.0, 1.0, 15.0, 15.0, 15.0);

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE_INSET_1PX;
    }

    /**
     * IMPORTANT:
     * Keep raycast as full cube so the block is always targetable and doesn't "click through"
     * to the block below when your outline has gaps/inset edges.
     */
    @Override
    public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.fullCube();
    }

    // Optional: keep collision full cube so players can't stand "inside" it
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.fullCube();
    }
}
