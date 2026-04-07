package com.spider.mtgcard.deckbox;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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

public class DeckboxBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<DeckboxBlock> CODEC = simpleCodec(DeckboxBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    // "spin" is the horizontal orientation used when FACING is UP/DOWN
    public static final EnumProperty<Direction> SPIN = EnumProperty.create(
            "spin",
            Direction.class,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    );

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;


    public DeckboxBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(
                this.getStateDefinition().any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(SPIN, Direction.NORTH)
                        .setValue(OPEN, false)
                        .setValue(WATERLOGGED, false)
        );

    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).is(Fluids.WATER);

        // Look direction: if player looks DOWN, this becomes UP (box faces up)
        Direction facing = ctx.getNearestLookingDirection().getOpposite();

        // Horizontal “spin” always follows the player yaw (opposite = faces player)
        Direction spin = ctx.getHorizontalDirection().getOpposite();

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SPIN, spin)
                .setValue(OPEN, false)
                .setValue(WATERLOGGED, water);
    }


    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction f = state.getValue(FACING);
        Direction s = state.getValue(SPIN);

        // Always rotate spin around Y
        s = rotation.rotate(s);

        // Only rotate facing if it is horizontal (UP/DOWN shouldn't change from a Y-rotation)
        if (f.getAxis().isHorizontal()) {
            f = rotation.rotate(f);
        }

        return state.setValue(FACING, f).setValue(SPIN, s);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction f = state.getValue(FACING);
        Direction s = state.getValue(SPIN);

        Rotation rot = mirror.getRotation(s);
        s = rot.rotate(s);

        if (f.getAxis().isHorizontal()) {
            f = mirror.getRotation(f).rotate(f);
        }

        return state.setValue(FACING, f).setValue(SPIN, s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN, OPEN, WATERLOGGED);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeckboxBlockEntity(pos, state);
    }

    /* ---------------- Comparator output ---------------- */

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeckboxBlockEntity deckbox) {
            return deckbox.getComparatorLevelMainOnly();
        }
        return 0;
    }

    /* ---------------- Use (dye or open) ---------------- */

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                              Player player, BlockHitResult hit) {

        if (world.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeckboxBlockEntity deckbox) {
            player.openMenu(deckbox);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        return (be instanceof DeckboxBlockEntity dbe) ? dbe : null;
    }

    /* ---------------- Player break suppression ---------------- */

    // ✅ Your mappings: onBreak returns BlockState (not void)
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = world.getBlockEntity(pos);

        if (!world.isClientSide() && be instanceof DeckboxBlockEntity deckbox && world instanceof ServerLevel sw) {
            ItemStack drop = new ItemStack(ModBlocks.DECKBOX_ITEM);

            CompoundTag beTag = buildBlockEntityTag(deckbox);
            CompoundTag carrier = new CompoundTag();
            carrier.put("BlockEntityTag", beTag);
            drop.set(DataComponents.CUSTOM_DATA, CustomData.of(carrier));

            Containers.dropItemStack(sw, pos.getX(), pos.getY(), pos.getZ(), drop);

            deckbox.clearForDropNoSync();
            sw.removeBlockEntity(pos);

            // IMPORTANT: return state directly (do NOT call super) so vanilla doesn't also drop an empty item
            return state;
        }

        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos,
                           BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // DO NOT remove PLAYER_BREAKING here.
        // In this version/mappings, afterBreak can run before onStateReplaced.
        super.playerDestroy(world, player, pos, state, blockEntity, tool);
    }

    /**
     * Stop vanilla from spilling inventory.
     * - Player break: do NOT call super() (that's where spill happens), keep BE for loot to read.
     * - Other removals: clear inventory then call super() so there's nothing to spill.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        // If we already cleared inventory in onBreak, this won't spill anything.
        // And it will still handle comparator updates properly.
        Containers.updateNeighboursAfterDestroy(state, world, pos);
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }



    /* ---------------- Restore from item on place ---------------- */

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!world.isClientSide()) {
            var comp = stack.get(DataComponents.CUSTOM_DATA);
            if (comp != null) {
                CompoundTag tag = comp.copyTag();
                if (tag != null && tag.contains("BlockEntityTag")) {
                    var beTagOpt = tag.getCompound("BlockEntityTag");
                    if (beTagOpt.isPresent()) {
                        CompoundTag beTag = beTagOpt.get();

                        BlockEntity be = world.getBlockEntity(pos);
                        if (be instanceof DeckboxBlockEntity deckbox) {

                            var itemsOpt = beTag.getList("Items");
                            if (itemsOpt.isPresent()) {
                                ListTag list = itemsOpt.get();
                                for (int i = 0; i < list.size(); i++) {
                                    var entryOpt = list.getCompound(i);
                                    if (entryOpt.isEmpty()) continue;

                                    CompoundTag st = entryOpt.get();
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

        super.setPlacedBy(world, pos, state, placer, stack);
    }

    /* ---------------- helpers ---------------- */

    private static CompoundTag buildBlockEntityTag(DeckboxBlockEntity deckbox) {
        CompoundTag beTag = new CompoundTag();
        ListTag list = new ListTag();

        for (int i = 0; i < DeckboxBlockEntity.INVENTORY_SIZE; i++) {
            ItemStack s = deckbox.getStack(i);
            if (!s.isEmpty()) {
                CompoundTag st = new CompoundTag();
                st.putInt("Slot", i);

                var encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, s).result();
                if (encoded.isPresent() && encoded.get() instanceof CompoundTag stackTag) {
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
            Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 15.0);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_INSET_1PX;
    }

    /**
     * IMPORTANT:
     * Keep raycast as full cube so the block is always targetable and doesn't "click through"
     * to the block below when your outline has gaps/inset edges.
     */
    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.block();
    }

    // Optional: keep collision full cube so players can't stand "inside" it
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
