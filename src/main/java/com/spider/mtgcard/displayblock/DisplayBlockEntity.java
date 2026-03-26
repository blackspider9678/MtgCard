package com.spider.mtgcard.displayblock;

import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.life.LifePointBlockEntity;
import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DisplayBlockEntity extends BlockEntity {

    // NBT keys on item + BE
    public static final String NBT_LINK_DIM = "LinkedLifeDim";
    public static final String NBT_LINK_POS = "LinkedLifePos";

    private Identifier linkedDimId = null;
    private BlockPos linkedLifePos = null;

    // Milestone 2: controller election + cached bounds
    private BlockPos controllerPos = null;

    private boolean reverseLinkRegistered = false;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISPLAY_BLOCK, pos, state);
    }

    public boolean hasLink() {
        return linkedDimId != null && linkedLifePos != null;
    }

    public Optional<Identifier> getLinkedDimId() { return Optional.ofNullable(linkedDimId); }
    public Optional<BlockPos> getLinkedLifePos() { return Optional.ofNullable(linkedLifePos); }

    private void updateReverseLink(@Nullable Identifier oldDim, @Nullable BlockPos oldLife,
                                   @Nullable Identifier newDim, @Nullable BlockPos newLife) {
        if (level == null || level.isClientSide()) return;
        if (!(level instanceof ServerLevel sw)) return;

        // During shutdown/unload, do NOT touch other chunks/dimensions.
        if (isServerStopping(sw)) return;

        // unregister old (NO-LOAD)
        if (oldDim != null && oldLife != null) {
            getLifeNoLoad(sw, oldDim, oldLife).ifPresent(lp -> lp.removeLinkedDisplay(this.worldPosition));
        }

        // register new (NO-LOAD)
        if (newDim != null && newLife != null) {
            getLifeNoLoad(sw, newDim, newLife).ifPresent(lp -> lp.addLinkedDisplay(this.worldPosition));
        }
    }

    public void setLink(Identifier dimId, BlockPos lifePos) {
        Identifier oldDim = this.linkedDimId;
        BlockPos oldPos = this.linkedLifePos;

        this.linkedDimId = dimId;
        this.linkedLifePos = lifePos;

        updateReverseLink(oldDim, oldPos, dimId, lifePos);

        setChanged();
        syncToClient();
    }

    public void clearLink() {
        Identifier oldDim = this.linkedDimId;
        BlockPos oldPos = this.linkedLifePos;

        this.linkedDimId = null;
        this.linkedLifePos = null;

        updateReverseLink(oldDim, oldPos, null, null);

        setChanged();
        syncToClient();
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sw) {
            // Don’t do cross-world/chunk touching during shutdown
            if (!isServerStopping(sw)) {
                Identifier oldDim = this.linkedDimId;
                BlockPos oldPos = this.linkedLifePos;
                updateReverseLink(oldDim, oldPos, null, null);
            }
        }
        super.setRemoved();
    }

    public void setControllerPos(BlockPos p) {
        controllerPos = p;
        setChanged();
        syncToClient();
    }

    // DisplayBlockEntity.java

    public void propagateLinkToUnlinkedComponent() {
        if (level == null || level.isClientSide()) return;
        if (!hasLink()) return;

        Direction facing = getBlockState().getValue(DisplayBlock.FACING);

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();

        q.add(this.worldPosition);
        visited.add(this.worldPosition);

        while (!q.isEmpty()) {
            BlockPos cur = q.removeFirst();

            for (Direction d : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
            }) {
                BlockPos np = cur.relative(d);
                if (visited.contains(np)) continue;

                BlockEntity nbe = level.getBlockEntity(np);
                if (!(nbe instanceof DisplayBlockEntity other)) continue;

                // must be same facing/orientation
                if (other.getBlockState().getValue(DisplayBlock.FACING) != facing) continue;

                // If neighbor is linked to a DIFFERENT life, it's a hard boundary
                if (other.hasLink() && !this.sameLinkAs(other)) continue;

                // If neighbor is unlinked, CLAIM it
                if (!other.hasLink()) {
                    other.setLink(this.linkedDimId, this.linkedLifePos);
                }

                visited.add(np);
                q.addLast(np);
            }
        }
    }

    // --- Multiblock cached layout (controller-owned) ---
    private int cachedMinX, cachedMinY, cachedMinZ;
    private int cachedMaxX, cachedMaxY, cachedMaxZ;
    private boolean cacheValid = false;

    public boolean isController() {
        return controllerPos == null || controllerPos.equals(worldPosition);
    }

    public BlockPos getControllerPos() {
        return controllerPos == null ? worldPosition : controllerPos;
    }

    public boolean sameLinkAs(DisplayBlockEntity other) {
        if (other == null) return false;
        if (!this.hasLink() || !other.hasLink()) return false;
        return this.linkedDimId.equals(other.linkedDimId) && this.linkedLifePos.equals(other.linkedLifePos);
    }

    public void applyLinkFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;

        CompoundTag nbt = custom.copyTag(); // safe mutable copy
        if (nbt == null) return;

        String dim = nbt.getString(NBT_LINK_DIM).orElse("");
        if (dim.isBlank()) return;

        long packed = nbt.getLong(NBT_LINK_POS).orElse(0L);
        if (packed == 0L) return;

        setLink(Identifier.parse(dim), BlockPos.of(packed));
    }

    public Optional<LifePointBlockEntity> resolveLinkedLifeClient() {
        if (level == null || !level.isClientSide()) return Optional.empty();
        if (!hasLink()) return Optional.empty();

        // Only render if the linked LifeBlock is in the same dimension the client is currently in
        if (!level.dimension().identifier().equals(linkedDimId)) return Optional.empty();

        var be = level.getBlockEntity(linkedLifePos);
        return (be instanceof LifePointBlockEntity lp) ? Optional.of(lp) : Optional.empty();
    }

    public boolean hasCache() { return cacheValid; }

    public int getCachedMinX() { return cachedMinX; }
    public int getCachedMinY() { return cachedMinY; }
    public int getCachedMinZ() { return cachedMinZ; }
    public int getCachedMaxX() { return cachedMaxX; }
    public int getCachedMaxY() { return cachedMaxY; }
    public int getCachedMaxZ() { return cachedMaxZ; }


    /**
     * Inherit from adjacent display blocks (4-way) that:
     * - have a link
     * - have same facing/orientation
     */
    public void tryInheritLinkFromNeighbors() {
        if (level == null || level.isClientSide()) return;

        Direction facing = getBlockState().getValue(DisplayBlock.FACING);

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos np = worldPosition.relative(d);
            BlockEntity be = level.getBlockEntity(np);
            if (!(be instanceof DisplayBlockEntity other)) continue;

            if (other.getBlockState().getValue(DisplayBlock.FACING) != facing) continue;
            if (!other.hasLink()) continue;

            Identifier dim = other.linkedDimId;
            BlockPos lp = other.linkedLifePos;
            if (dim != null && lp != null) {
                setLink(dim, lp);
                return;
            }
        }
    }

    public void requestRebuild() {
        if (level == null || level.isClientSide()) return;

        // Always rebuild from a nearby controller candidate:
        // if we already know controllerPos, ask it to rebuild; otherwise rebuild self.
        BlockPos cpos = getControllerPos();
        BlockEntity be = level.getBlockEntity(cpos);
        if (be instanceof DisplayBlockEntity ctrl) {
            ctrl.rebuildScreenFromController();
        } else {
            rebuildScreenFromController();
        }
    }

    private void rebuildScreenFromController() {
        if (level == null || level.isClientSide()) return;

        // Controller must be a real member of the screen
        Direction facing = getBlockState().getValue(DisplayBlock.FACING);

        // Flood-fill set of connected display blocks
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(worldPosition);
        visited.add(worldPosition);

        while (!q.isEmpty()) {
            BlockPos cur = q.removeFirst();

            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
                BlockPos np = cur.relative(d);
                if (visited.contains(np)) continue;

                BlockEntity nbe = level.getBlockEntity(np);
                if (!(nbe instanceof DisplayBlockEntity other)) continue;

                // Must share same facing/orientation to merge
                if (other.getBlockState().getValue(DisplayBlock.FACING) != facing) continue;

                // Must be same link to merge (unlinked blocks should have inherited by now)
                // If either has no link, we DO NOT merge (prevents ambiguity)
                if (!other.hasLink()) continue;          // neighbor must be linked
                if (!this.hasLink()) continue;           // controller must be linked
                if (!this.sameLinkAs(other)) continue;   // must match link

                visited.add(np);
                q.add(np);
            }
        }

        // Elect controller: pick the smallest BlockPos (stable)
        BlockPos elected = visited.stream()
                .min((a, b) -> {
                    if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
                    if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
                    return Integer.compare(a.getX(), b.getX());
                })
                .orElse(worldPosition);

        // Compute bounds (we'll use for UV mapping later)
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

        // Apply controller + cache to all members
        for (BlockPos p : visited) {
            BlockEntity mbe = level.getBlockEntity(p);
            if (!(mbe instanceof DisplayBlockEntity member)) continue;

            member.controllerPos = elected; // direct set (avoid extra rebuild ping)
            member.setChanged();
        }

        // Clear cache on all members (only controller will re-set it)
        for (BlockPos p : visited) {
            BlockEntity mbe = level.getBlockEntity(p);
            if (mbe instanceof DisplayBlockEntity member) {
                member.cacheValid = false;
                member.setChanged();
            }
        }

        // Store bounds cache only on elected controller
        BlockEntity ebe = level.getBlockEntity(elected);
        if (ebe instanceof DisplayBlockEntity ctrl) {
            ctrl.cachedMinX = minX;
            ctrl.cachedMinY = minY;
            ctrl.cachedMinZ = minZ;
            ctrl.cachedMaxX = maxX;
            ctrl.cachedMaxY = maxY;
            ctrl.cachedMaxZ = maxZ;
            ctrl.cacheValid = true;
            ctrl.setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        view.putString(NBT_LINK_DIM, linkedDimId == null ? "" : linkedDimId.toString());
        view.putLong(NBT_LINK_POS, linkedLifePos == null ? 0L : linkedLifePos.asLong());
        view.putLong("ControllerPos", controllerPos == null ? 0L : controllerPos.asLong());
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        String dim = view.getStringOr(NBT_LINK_DIM, "");
        linkedDimId = (dim == null || dim.isBlank()) ? null : Identifier.parse(dim);

        long packed = view.getLongOr(NBT_LINK_POS, 0L);
        linkedLifePos = (packed == 0L) ? null : BlockPos.of(packed);

        long c = view.getLongOr("ControllerPos", 0L);
        controllerPos = (c == 0L) ? null : BlockPos.of(c);
    }

    private void syncToClient() {
        if (level == null || level.isClientSide()) return;

        // marks BE for update packet to tracking clients
        if (level instanceof ServerLevel sw) {
            sw.getChunkSource().blockChanged(worldPosition);
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, DisplayBlockEntity be) {
        if (world.isClientSide()) return;
        if (!(world instanceof ServerLevel sw)) return;

        // One-time reverse-link registration after load (Yarn has no onLoad())
        if (!be.reverseLinkRegistered) {
            be.reverseLinkRegistered = true;

            if (be.hasLink()) {
                be.updateReverseLink(null, null, be.linkedDimId, be.linkedLifePos);
                be.setChanged();
            }
        }

        if (be.hasLink()) {
            if (be.resolveLinkedLife(sw).isEmpty()) {
                // life block missing -> clear link + notify clients
                be.clearLink();
            }
        }

        // Only controller should broadcast (prevents spam)
        if (!be.isController()) return;
        if (!be.hasLink()) return;

        long t = sw.getGameTime();
        if ((t % 10L) != 0L) return;

        be.resolveLinkedLife(sw).ifPresent(lp -> {
            var lpPos = be.getLinkedLifePos().orElse(null);
            if (lpPos == null) return;
            LifePointPackets.syncToPlayers(PlayerLookup.tracking(sw, pos), lpPos, lp);
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Resolve linked LifePoint BE on server (uses linked dimension). */
    public Optional<LifePointBlockEntity> resolveLinkedLife(ServerLevel anyServerWorld) {
        if (anyServerWorld == null) return Optional.empty();
        if (!hasLink()) return Optional.empty();
        if (linkedDimId == null || linkedLifePos == null) return Optional.empty();

        return getLifeNoLoad(anyServerWorld, linkedDimId, linkedLifePos);
    }

    private boolean isServerStopping(ServerLevel sw) {
        MinecraftServer s = sw.getServer();
        return s != null && s.isShutdown();
    }

    /** No-load lookup for the LifePointBlockEntity in a possibly different dimension. */
    private Optional<LifePointBlockEntity> getLifeNoLoad(ServerLevel contextWorld, Identifier dimId, BlockPos lifePos) {
        if (contextWorld == null || dimId == null || lifePos == null) return Optional.empty();

        MinecraftServer server = contextWorld.getServer();
        if (server == null) return Optional.empty();

        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimId);
        ServerLevel target = server.getLevel(key);
        if (target == null) return Optional.empty();

        // IMPORTANT: do not load chunks here
        ChunkPos cp = new ChunkPos(lifePos.getX() >> 4, lifePos.getZ() >> 4);
        if (!target.getChunkSource().hasChunk(cp.x(), cp.z())) return Optional.empty();

        var chunk = target.getChunk(cp.x(), cp.z(), ChunkStatus.FULL, false);
        if (chunk == null) return Optional.empty();

        BlockEntity be = chunk.getBlockEntity(lifePos);
        return (be instanceof LifePointBlockEntity lp) ? Optional.of(lp) : Optional.empty();
    }
}
