package com.spider.mtgcard.displayblock;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class DisplayBlock extends BaseEntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<DisplayBlock> CODEC = MapCodec.unit(() -> new DisplayBlock(Properties.of()));
    public static final EnumProperty<DisplayShape> SHAPE =
            EnumProperty.create("shape", DisplayShape.class);

    public DisplayBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(SHAPE, DisplayShape.SINGLE));
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighborBlock,
                                   net.minecraft.world.level.redstone.Orientation wireOrientation,
                                   boolean notify) {
        if (!world.isClientSide()) {
            var be = world.getBlockEntity(pos);
            if (be instanceof DisplayBlockEntity dbe) {
                // NEW: if this tile is linked, claim any adjacent unlinked tiles
                if (dbe.hasLink()) {
                    dbe.propagateLinkToUnlinkedComponent();
                }
                dbe.requestRebuild();
            }
        }
        updateSelfAndNeighbors(world, pos);

        super.neighborChanged(state, world, pos, neighborBlock, wireOrientation, notify);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel world, BlockPos pos, boolean moved) {
        if (world.getBlockState(pos).getBlock() != this) {
            // Ask neighbors to rebuild screens
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
                updateSelfAndNeighbors(world, pos.relative(d));
            }
        }
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(SHAPE, DisplayShape.SINGLE);
    }

    private static boolean canConnect(Level world, BlockPos a, BlockPos b) {
        if (!(world.getBlockState(b).getBlock() instanceof DisplayBlock)) return false;

        var beA = world.getBlockEntity(a);
        var beB = world.getBlockEntity(b);
        if (!(beA instanceof DisplayBlockEntity da) || !(beB instanceof DisplayBlockEntity db)) return false;

        // Must face same way
        Direction fa = world.getBlockState(a).getValue(FACING);
        Direction fb = world.getBlockState(b).getValue(FACING);
        if (fa != fb) return false;

        // Must have same link to merge visually (matches your BE merge rule)
        if (!da.hasLink() || !db.hasLink()) return false;
        return da.sameLinkAs(db);
    }

    private static DisplayShape computeShape(Level world, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction leftDir  = facing.getCounterClockWise();
        Direction rightDir = facing.getClockWise();

        boolean up    = canConnect(world, pos, pos.above());
        boolean down  = canConnect(world, pos, pos.below());
        boolean left  = canConnect(world, pos, pos.relative(leftDir));
        boolean right = canConnect(world, pos, pos.relative(rightDir));

        if (!up && !down && !left && !right) return DisplayShape.SINGLE;

        // Pure vertical (N×1)
        if ((up || down) && !(left || right)) {
            if (up && down) return DisplayShape.V_MIDDLE;
            if (up) return DisplayShape.V_BOTTOM;     // neighbor above -> you're bottom end
            return DisplayShape.V_TOP;                // neighbor below -> you're top end
        }

        // Pure horizontal (1×N)
        if ((left || right) && !(up || down)) {
            if (left && right) return DisplayShape.H_MIDDLE;
            if (right) return DisplayShape.H_LEFT;    // neighbor to the right only -> left end
            return DisplayShape.H_RIGHT;              // neighbor to the left only -> right end
        }

        // 2D grid (N×M): 9-slice classification
        boolean topRow = !up;
        boolean bottomRow = !down;
        boolean leftCol = !left;
        boolean rightCol = !right;

        if (topRow && leftCol) return DisplayShape.CORNER_TL;
        if (topRow && rightCol) return DisplayShape.CORNER_TR;
        if (bottomRow && leftCol) return DisplayShape.CORNER_BL;
        if (bottomRow && rightCol) return DisplayShape.CORNER_BR;

        if (topRow) return DisplayShape.SIDE_TOP;
        if (bottomRow) return DisplayShape.SIDE_BOTTOM;
        if (leftCol) return DisplayShape.SIDE_LEFT;
        if (rightCol) return DisplayShape.SIDE_RIGHT;

        return DisplayShape.MIDDLE;
    }

    private static void updateSelfAndNeighbors(Level world, BlockPos pos) {
        if (world.isClientSide()) return;
        BlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DisplayBlock)) return;

        DisplayShape newShape = computeShape(world, pos, st);
        if (st.getValue(SHAPE) != newShape) {
            world.setBlock(pos, st.setValue(SHAPE, newShape), 3);
        }

        // Update neighbors in the screen plane + vertical
        Direction facing = st.getValue(FACING);
        Direction leftDir  = facing.getCounterClockWise();
        Direction rightDir = facing.getClockWise();

        BlockPos[] affected = new BlockPos[] {
                pos.above(), pos.below(),
                pos.relative(leftDir), pos.relative(rightDir)
        };

        for (BlockPos p : affected) {
            BlockState s2 = world.getBlockState(p);
            if (!(s2.getBlock() instanceof DisplayBlock)) continue;

            DisplayShape ns = computeShape(world, p, s2);
            if (s2.getValue(SHAPE) != ns) {
                world.setBlock(p, s2.setValue(SHAPE, ns), 3);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        if (world.isClientSide()) return;

        var be = world.getBlockEntity(pos);
        if (!(be instanceof DisplayBlockEntity dbe)) return;

        // 1) Apply link from item (if present)
        dbe.applyLinkFromItem(stack);

        // DisplayBlock.java onPlaced()

        // 2) If no link, inherit from adjacent display screens (same facing)
        if (!dbe.hasLink()) {
            dbe.tryInheritLinkFromNeighbors();
        }

        // NEW: if we ARE linked, push that link into nearby unlinked tiles
        if (dbe.hasLink()) {
            dbe.propagateLinkToUnlinkedComponent();
        }

        // 3) Rebuild
        dbe.requestRebuild();

        // 4) Also ask neighbors to rebuild so the elected controller + cache settles immediately
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            var nbe = world.getBlockEntity(pos.relative(d));
            if (nbe instanceof DisplayBlockEntity ndbe) ndbe.requestRebuild();
        }
        // after dbe.tryInheritLinkFromNeighbors();
        updateSelfAndNeighbors(world, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide()) return null;

        if (type == ModBlockEntities.DISPLAY_BLOCK) {

            return (w, p, s, be) -> DisplayBlockEntity.tick(w, p, s, (DisplayBlockEntity) be);
        }
        return null;
    }

}
