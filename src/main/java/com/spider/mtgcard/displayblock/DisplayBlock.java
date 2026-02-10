package com.spider.mtgcard.displayblock;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class DisplayBlock extends BlockWithEntity {

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    public static final MapCodec<DisplayBlock> CODEC = createCodec(DisplayBlock::new);

    public static final EnumProperty<DisplayShape> SHAPE =
            EnumProperty.of("shape", DisplayShape.class);

    public DisplayBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(SHAPE, DisplayShape.SINGLE));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos,
                                  net.minecraft.block.Block neighborBlock,
                                  net.minecraft.world.block.WireOrientation wireOrientation,
                                  boolean notify) {
        if (!world.isClient()) {
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

        super.neighborUpdate(state, world, pos, neighborBlock, wireOrientation, notify);
    }

    @Override
    protected void onStateReplaced(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos, boolean moved) {
        if (world.getBlockState(pos).getBlock() != this) {
            // Ask neighbors to rebuild screens
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
                updateSelfAndNeighbors(world, pos.offset(d));
            }
        }
        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(SHAPE, DisplayShape.SINGLE);
    }

    private static boolean canConnect(World world, BlockPos a, BlockPos b) {
        if (!(world.getBlockState(b).getBlock() instanceof DisplayBlock)) return false;

        var beA = world.getBlockEntity(a);
        var beB = world.getBlockEntity(b);
        if (!(beA instanceof DisplayBlockEntity da) || !(beB instanceof DisplayBlockEntity db)) return false;

        // Must face same way
        Direction fa = world.getBlockState(a).get(FACING);
        Direction fb = world.getBlockState(b).get(FACING);
        if (fa != fb) return false;

        // Must have same link to merge visually (matches your BE merge rule)
        if (!da.hasLink() || !db.hasLink()) return false;
        return da.sameLinkAs(db);
    }

    private static DisplayShape computeShape(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        Direction leftDir  = facing.rotateYCounterclockwise();
        Direction rightDir = facing.rotateYClockwise();

        boolean up    = canConnect(world, pos, pos.up());
        boolean down  = canConnect(world, pos, pos.down());
        boolean left  = canConnect(world, pos, pos.offset(leftDir));
        boolean right = canConnect(world, pos, pos.offset(rightDir));

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

    private static void updateSelfAndNeighbors(World world, BlockPos pos) {
        if (world.isClient()) return;
        BlockState st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DisplayBlock)) return;

        DisplayShape newShape = computeShape(world, pos, st);
        if (st.get(SHAPE) != newShape) {
            world.setBlockState(pos, st.with(SHAPE, newShape), 3);
        }

        // Update neighbors in the screen plane + vertical
        Direction facing = st.get(FACING);
        Direction leftDir  = facing.rotateYCounterclockwise();
        Direction rightDir = facing.rotateYClockwise();

        BlockPos[] affected = new BlockPos[] {
                pos.up(), pos.down(),
                pos.offset(leftDir), pos.offset(rightDir)
        };

        for (BlockPos p : affected) {
            BlockState s2 = world.getBlockState(p);
            if (!(s2.getBlock() instanceof DisplayBlock)) continue;

            DisplayShape ns = computeShape(world, p, s2);
            if (s2.get(SHAPE) != ns) {
                world.setBlockState(p, s2.with(SHAPE, ns), 3);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);

        if (world.isClient()) return;

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
            var nbe = world.getBlockEntity(pos.offset(d));
            if (nbe instanceof DisplayBlockEntity ndbe) ndbe.requestRebuild();
        }
        // after dbe.tryInheritLinkFromNeighbors();
        updateSelfAndNeighbors(world, pos);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // No GUI. Scroll interaction will be handled client-side later.
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient()) return null;

        if (type == ModBlockEntities.DISPLAY_BLOCK) {

            return (w, p, s, be) -> DisplayBlockEntity.tick(w, p, s, (DisplayBlockEntity) be);
        }
        return null;
    }

}
