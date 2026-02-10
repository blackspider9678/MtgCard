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
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
    public static final EnumProperty<Direction> SPIN = EnumProperty.create(
            "spin",
            Direction.class,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    );
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public DeckboxBlock(BlockBehaviour.Properties settings) {
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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN, OPEN, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).is(Fluids.WATER);
        Direction facing = ctx.getNearestLookingDirection().getOpposite();
        Direction spin = ctx.getHorizontalDirection().getOpposite();

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SPIN, spin)
                .setValue(OPEN, false)
                .setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader world,
            ScheduledTickAccess tickAccess,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
    ) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return super.updateShape(state, world, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        Direction spin = rotation.rotate(state.getValue(SPIN));

        if (facing.getAxis().isHorizontal()) {
            facing = rotation.rotate(facing);
        }

        return state.setValue(FACING, facing).setValue(SPIN, spin);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        Direction spin = state.getValue(SPIN);

        Rotation rotation = mirror.getRotation(spin);
        spin = rotation.rotate(spin);

        if (facing.getAxis().isHorizontal()) {
            facing = mirror.getRotation(facing).rotate(facing);
        }

        return state.setValue(FACING, facing).setValue(SPIN, spin);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeckboxBlockEntity(pos, state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        return openMenu(world, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        return openMenu(world, pos, player);
    }

    private static InteractionResult openMenu(Level world, BlockPos pos, Player player) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MenuProvider provider) {
            player.openMenu(provider);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = world.getBlockEntity(pos);

        if (!world.isClientSide() && be instanceof DeckboxBlockEntity deckbox && world instanceof ServerLevel serverLevel) {
            ItemStack drop = new ItemStack(ModBlocks.DECKBOX_ITEM);

            CompoundTag beTag = buildBlockEntityTag(deckbox);
            CompoundTag carrier = new CompoundTag();
            carrier.put("BlockEntityTag", beTag);
            drop.set(DataComponents.CUSTOM_DATA, CustomData.of(carrier));

            Containers.dropItemStack(serverLevel, pos.getX(), pos.getY(), pos.getZ(), drop);

            deckbox.clearForDropNoSync();
            serverLevel.removeBlockEntity(pos);
        }

        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void playerDestroy(Level world, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!world.isClientSide()) {
            var comp = stack.get(DataComponents.CUSTOM_DATA);
            if (comp != null) {
                CompoundTag tag = comp.copyTag();
                tag.getCompound("BlockEntityTag").ifPresent(beTag -> {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (!(be instanceof DeckboxBlockEntity deckbox)) return;

                    beTag.getList("Items").ifPresent(items -> {
                        for (int i = 0; i < items.size(); i++) {
                            var entryOpt = items.getCompound(i);
                            if (entryOpt.isEmpty()) continue;

                            CompoundTag entry = entryOpt.get();
                            int slot = entry.getInt("Slot").orElse(-1);
                            if (slot < 0 || slot >= DeckboxBlockEntity.INVENTORY_SIZE) continue;

                            ItemStack stackToSet = ItemStack.EMPTY;
                            var innerOpt = entry.getCompound("Stack");
                            if (innerOpt.isPresent()) {
                                stackToSet = ItemStack.CODEC.parse(NbtOps.INSTANCE, innerOpt.get()).result().orElse(ItemStack.EMPTY);
                            } else {
                                stackToSet = ItemStack.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(ItemStack.EMPTY);
                            }

                            deckbox.setStack(slot, stackToSet);
                        }
                    });

                    int tint = beTag.getInt("Tint").orElse(0xFFFFFF);
                    deckbox.setRgbTint(tint);
                    deckbox.sync();
                });
            }
        }

        super.setPlacedBy(world, pos, state, placer, stack);
    }

    private static CompoundTag buildBlockEntityTag(DeckboxBlockEntity deckbox) {
        CompoundTag beTag = new CompoundTag();
        ListTag list = new ListTag();

        for (int i = 0; i < DeckboxBlockEntity.INVENTORY_SIZE; i++) {
            ItemStack stack = deckbox.getStack(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", i);

                var encoded = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result();
                if (encoded.isPresent() && encoded.get() instanceof CompoundTag stackTag) {
                    entry.put("Stack", stackTag);
                }
                list.add(entry);
            }
        }

        beTag.put("Items", list);
        beTag.putInt("Tint", deckbox.getRgbTint());
        return beTag;
    }

    private static final VoxelShape OUTLINE_INSET_1PX = Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 15.0);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_INSET_1PX;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
