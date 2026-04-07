package com.spider.mtgcard.client.displayblock;

import com.spider.mtgcard.displayblock.DisplayBlock;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;

public final class DisplayScreenBoundsCache {

    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {}

    /**
     * OLD API (facing-only) — keep for compatibility, but make it effectively link-aware by using
     * the BE's current link as the "screen identity".
     */
    public static Bounds computeBoundsFacingOnly(DisplayBlockEntity be, Direction facing) {
        Level world = be.getLevel();
        BlockPos start = be.getBlockPos();

        if (world == null || !world.isClientSide()) {
            return new Bounds(start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ());
        }

        Identifier dim = be.getLinkedDimId().orElse(null);
        BlockPos life = be.getLinkedLifePos().orElse(null);

        // If unlinked, treat as standalone (do NOT merge with anything)
        if (dim == null || life == null) {
            return new Bounds(start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ());
        }

        return computeBoundsFacingAndLink(be, facing, dim, life);
    }

    /**
     * LINK-AWARE bounds: only flood-fill tiles that share:
     *  - DisplayBlock
     *  - same FACING
     *  - same linkedDimId + linkedLifePos
     */
    public static Bounds computeBoundsFacingAndLink(DisplayBlockEntity be, Direction facing,
                                                    Identifier linkedDim, BlockPos linkedLifePos) {
        Level world = be.getLevel();
        BlockPos start = be.getBlockPos();

        if (world == null || !world.isClientSide()) {
            return new Bounds(start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ());
        }

        // Screen plane: UP/DOWN + left/right perpendicular to facing
        Direction left, right;
        if (facing == Direction.NORTH || facing == Direction.SOUTH) {
            left = Direction.WEST;
            right = Direction.EAST;
        } else {
            left = Direction.NORTH;
            right = Direction.SOUTH;
        }
        Direction[] neighbors = new Direction[]{Direction.UP, Direction.DOWN, left, right};

        HashSet<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();

        // Start only counts if the start tile matches this link identity
        if (!isDisplayTileSameLink(world, start, facing, linkedDim, linkedLifePos)) {
            return new Bounds(start.getX(), start.getY(), start.getZ(), start.getX(), start.getY(), start.getZ());
        }

        visited.add(start);
        q.add(start);

        while (!q.isEmpty() && visited.size() < 4096) {
            BlockPos cur = q.removeFirst();

            for (Direction d : neighbors) {
                BlockPos np = cur.relative(d);
                if (visited.contains(np)) continue;

                if (!isDisplayTileSameLink(world, np, facing, linkedDim, linkedLifePos)) continue;

                visited.add(np);
                q.add(np);
            }
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos p : visited) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        if (minX == Integer.MAX_VALUE) {
            minX = maxX = start.getX();
            minY = maxY = start.getY();
            minZ = maxZ = start.getZ();
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isDisplayTileSameLink(Level world, BlockPos pos, Direction facing,
                                                 @Nullable Identifier linkedDim, @Nullable BlockPos linkedLifePos) {
        var st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DisplayBlock)) return false;
        if (st.getValue(DisplayBlock.FACING) != facing) return false;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof DisplayBlockEntity dbe)) return false;

        if (linkedDim == null || linkedLifePos == null) return false;

        return dbe.getLinkedDimId().map(linkedDim::equals).orElse(false)
                && dbe.getLinkedLifePos().map(linkedLifePos::equals).orElse(false);
    }

    private DisplayScreenBoundsCache() {}
}
