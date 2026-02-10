package com.spider.mtgcard.deckbox;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
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
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DeckboxBlock extends BlockWithEntity implements Waterloggable {

    public static final MapCodec<DeckboxBlock> CODEC = createCodec(DeckboxBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;


    // Positions currently being broken by a player (to prevent spill without deleting BE too early)
    private static final Set<Long> PLAYER_BREAKING = ConcurrentHashMap.newKeySet();

    public DeckboxBlock(Settings settings) {
        super(settings);
        this.setDefaultState(
                this.getStateManager().getDefaultState()
                        .with(FACING, Direction.NORTH)
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
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
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
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }


    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, WATERLOGGED);
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
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {

        if (world.isClient()) return ActionResult.SUCCESS;

        for (Hand hand : Hand.values()) {
            ItemStack held = player.getStackInHand(hand);

            // Dye -> tint
            if (!held.isEmpty() && held.getItem() instanceof DyeItem dye) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof DeckboxBlockEntity deckbox) {
                    deckbox.setRgbTint(dye.getColor().getMapColor().color);
                    if (!player.isCreative()) held.decrement(1);
                    return ActionResult.CONSUME;
                }
            }
        }

        // Open UI
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
        if (!world.isClient()) {
            long key = pos.asLong();
            PLAYER_BREAKING.add(key);

            // ✅ Creative does NOT call loot drops, so we manually spawn the bundled item here.
            if (player.isCreative() && world instanceof ServerWorld sw) {
                BlockEntity be = sw.getBlockEntity(pos);
                if (be instanceof DeckboxBlockEntity deckbox) {
                    ItemStack drop = new ItemStack(ModBlocks.DECKBOX);

                    NbtCompound beTag = buildBlockEntityTag(deckbox);
                    NbtCompound carrier = new NbtCompound();
                    carrier.put("BlockEntityTag", beTag);
                    drop.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(carrier));

                    // spawn the single deckbox item
                    ItemScatterer.spawn(sw, pos.getX(), pos.getY(), pos.getZ(), drop);

                    // ✅ prevent spill/duplication
                    deckbox.clearForDropNoSync();
                    sw.removeBlockEntity(pos);
                }
            }
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos,
                           BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!world.isClient()) {
            PLAYER_BREAKING.remove(pos.asLong());
        }
        super.afterBreak(world, player, pos, state, blockEntity, tool);
    }

    /* ---------------- Drops (SHULKER-LIKE but ALSO DROPS IN CREATIVE) ---------------- */

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
        BlockEntity be = builder.getOptional(LootContextParameters.BLOCK_ENTITY);

        if (be instanceof DeckboxBlockEntity deckbox) {
            ItemStack drop = new ItemStack(ModBlocks.DECKBOX_ITEM);

            NbtCompound beTag = buildBlockEntityTag(deckbox);
            NbtCompound carrier = new NbtCompound();
            carrier.put("BlockEntityTag", beTag);
            drop.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(carrier));

            // ✅ Prevent any later spill/duplication: loot already captured, now wipe inventory
            deckbox.clearForDropNoSync();

            return List.of(drop);
        }

        // fallback: drop empty deckbox
        return List.of(new ItemStack(ModBlocks.DECKBOX_ITEM));
    }

    /**
     * Stop vanilla from spilling inventory.
     * - Player break: do NOT call super() (that's where spill happens), keep BE for loot to read.
     * - Other removals: clear inventory then call super() so there's nothing to spill.
     */
    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        long key = pos.asLong();

        // If this was a player break, we must prevent vanilla spill
        if (PLAYER_BREAKING.remove(key)) {
            // ✅ Ensure BE is gone before vanilla cleanup runs (so nothing can scatter)
            world.removeBlockEntity(pos);
            super.onStateReplaced(state, world, pos, moved);
            return;
        }

        // Non-player removal (pistons/explosions/etc):
        // your getDroppedStacks() will handle the bundled item,
        // so just clear inventory so vanilla can't spill.
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof DeckboxBlockEntity deckbox) {
            deckbox.clearForDropNoSync();
        }

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
}
