package com.spider.mtgcard.displayblock;

import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.life.LifePointBlockEntity;
import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
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
        if (world == null || world.isClient()) return;
        if (!(world instanceof ServerWorld sw)) return;

        // During shutdown/unload, do NOT touch other chunks/dimensions.
        if (isServerStopping(sw)) return;

        // unregister old (NO-LOAD)
        if (oldDim != null && oldLife != null) {
            getLifeNoLoad(sw, oldDim, oldLife).ifPresent(lp -> lp.removeLinkedDisplay(this.pos));
        }

        // register new (NO-LOAD)
        if (newDim != null && newLife != null) {
            getLifeNoLoad(sw, newDim, newLife).ifPresent(lp -> lp.addLinkedDisplay(this.pos));
        }
    }

    public void setLink(Identifier dimId, BlockPos lifePos) {
        Identifier oldDim = this.linkedDimId;
        BlockPos oldPos = this.linkedLifePos;

        this.linkedDimId = dimId;
        this.linkedLifePos = lifePos;

        updateReverseLink(oldDim, oldPos, dimId, lifePos);

        markDirty();
        syncToClient();
    }

    public void clearLink() {
        Identifier oldDim = this.linkedDimId;
        BlockPos oldPos = this.linkedLifePos;

        this.linkedDimId = null;
        this.linkedLifePos = null;

        updateReverseLink(oldDim, oldPos, null, null);

        markDirty();
        syncToClient();
    }

    @Override
    public void markRemoved() {
        if (world instanceof ServerWorld sw) {
            // Don’t do cross-world/chunk touching during shutdown
            if (!isServerStopping(sw)) {
                Identifier oldDim = this.linkedDimId;
                BlockPos oldPos = this.linkedLifePos;
                updateReverseLink(oldDim, oldPos, null, null);
            }
        }
        super.markRemoved();
    }

    public void setControllerPos(BlockPos p) {
        controllerPos = p;
        markDirty();
        syncToClient();
    }

    // DisplayBlockEntity.java

    public void propagateLinkToUnlinkedComponent() {
        if (world == null || world.isClient()) return;
        if (!hasLink()) return;

        Direction facing = getCachedState().get(DisplayBlock.FACING);

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        HashSet<BlockPos> visited = new HashSet<>();

        q.add(this.pos);
        visited.add(this.pos);

        while (!q.isEmpty()) {
            BlockPos cur = q.removeFirst();

            for (Direction d : new Direction[]{
                    Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN
            }) {
                BlockPos np = cur.offset(d);
                if (visited.contains(np)) continue;

                BlockEntity nbe = world.getBlockEntity(np);
                if (!(nbe instanceof DisplayBlockEntity other)) continue;

                // must be same facing/orientation
                if (other.getCachedState().get(DisplayBlock.FACING) != facing) continue;

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
        return controllerPos == null || controllerPos.equals(pos);
    }

    public BlockPos getControllerPos() {
        return controllerPos == null ? pos : controllerPos;
    }

    public boolean sameLinkAs(DisplayBlockEntity other) {
        if (other == null) return false;
        if (!this.hasLink() || !other.hasLink()) return false;
        return this.linkedDimId.equals(other.linkedDimId) && this.linkedLifePos.equals(other.linkedLifePos);
    }

    public void applyLinkFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (custom == null) return;

        NbtCompound nbt = custom.copyNbt(); // safe mutable copy
        if (nbt == null) return;

        String dim = nbt.getString(NBT_LINK_DIM).orElse("");
        if (dim.isBlank()) return;

        long packed = nbt.getLong(NBT_LINK_POS).orElse(0L);
        if (packed == 0L) return;

        setLink(Identifier.of(dim), BlockPos.fromLong(packed));
    }

    public Optional<LifePointBlockEntity> resolveLinkedLifeClient() {
        if (world == null || !world.isClient()) return Optional.empty();
        if (!hasLink()) return Optional.empty();

        // Only render if the linked LifeBlock is in the same dimension the client is currently in
        if (!world.getRegistryKey().getValue().equals(linkedDimId)) return Optional.empty();

        var be = world.getBlockEntity(linkedLifePos);
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
        if (world == null || world.isClient()) return;

        Direction facing = getCachedState().get(DisplayBlock.FACING);

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos np = pos.offset(d);
            BlockEntity be = world.getBlockEntity(np);
            if (!(be instanceof DisplayBlockEntity other)) continue;

            if (other.getCachedState().get(DisplayBlock.FACING) != facing) continue;
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
        if (world == null || world.isClient()) return;

        // Always rebuild from a nearby controller candidate:
        // if we already know controllerPos, ask it to rebuild; otherwise rebuild self.
        BlockPos cpos = getControllerPos();
        BlockEntity be = world.getBlockEntity(cpos);
        if (be instanceof DisplayBlockEntity ctrl) {
            ctrl.rebuildScreenFromController();
        } else {
            rebuildScreenFromController();
        }
    }

    private void rebuildScreenFromController() {
        if (world == null || world.isClient()) return;

        // Controller must be a real member of the screen
        Direction facing = getCachedState().get(DisplayBlock.FACING);

        // Flood-fill set of connected display blocks
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(pos);
        visited.add(pos);

        while (!q.isEmpty()) {
            BlockPos cur = q.removeFirst();

            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
                BlockPos np = cur.offset(d);
                if (visited.contains(np)) continue;

                BlockEntity nbe = world.getBlockEntity(np);
                if (!(nbe instanceof DisplayBlockEntity other)) continue;

                // Must share same facing/orientation to merge
                if (other.getCachedState().get(DisplayBlock.FACING) != facing) continue;

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
                .orElse(pos);

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
            BlockEntity mbe = world.getBlockEntity(p);
            if (!(mbe instanceof DisplayBlockEntity member)) continue;

            member.controllerPos = elected; // direct set (avoid extra rebuild ping)
            member.markDirty();
        }

        // Clear cache on all members (only controller will re-set it)
        for (BlockPos p : visited) {
            BlockEntity mbe = world.getBlockEntity(p);
            if (mbe instanceof DisplayBlockEntity member) {
                member.cacheValid = false;
                member.markDirty();
            }
        }

        // Store bounds cache only on elected controller
        BlockEntity ebe = world.getBlockEntity(elected);
        if (ebe instanceof DisplayBlockEntity ctrl) {
            ctrl.cachedMinX = minX;
            ctrl.cachedMinY = minY;
            ctrl.cachedMinZ = minZ;
            ctrl.cachedMaxX = maxX;
            ctrl.cachedMaxY = maxY;
            ctrl.cachedMaxZ = maxZ;
            ctrl.cacheValid = true;
            ctrl.markDirty();
        }
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        view.putString(NBT_LINK_DIM, linkedDimId == null ? "" : linkedDimId.toString());
        view.putLong(NBT_LINK_POS, linkedLifePos == null ? 0L : linkedLifePos.asLong());
        view.putLong("ControllerPos", controllerPos == null ? 0L : controllerPos.asLong());
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);

        String dim = view.getString(NBT_LINK_DIM, "");
        linkedDimId = (dim == null || dim.isBlank()) ? null : Identifier.of(dim);

        long packed = view.getLong(NBT_LINK_POS, 0L);
        linkedLifePos = (packed == 0L) ? null : BlockPos.fromLong(packed);

        long c = view.getLong("ControllerPos", 0L);
        controllerPos = (c == 0L) ? null : BlockPos.fromLong(c);
    }

    private void syncToClient() {
        if (world == null || world.isClient()) return;

        // marks BE for update packet to tracking clients
        if (world instanceof ServerWorld sw) {
            sw.getChunkManager().markForUpdate(pos);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, DisplayBlockEntity be) {
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld sw)) return;

        // One-time reverse-link registration after load (Yarn has no onLoad())
        if (!be.reverseLinkRegistered) {
            be.reverseLinkRegistered = true;

            if (be.hasLink()) {
                be.updateReverseLink(null, null, be.linkedDimId, be.linkedLifePos);
                be.markDirty();
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

        long t = sw.getTime();
        if ((t % 10L) != 0L) return;

        be.resolveLinkedLife(sw).ifPresent(lp -> {
            var lpPos = be.getLinkedLifePos().orElse(null);
            if (lpPos == null) return;
            LifePointPackets.syncToPlayers(PlayerLookup.tracking(sw, pos), lpPos, lp);
        });
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** Resolve linked LifePoint BE on server (uses linked dimension). */
    public Optional<LifePointBlockEntity> resolveLinkedLife(ServerWorld anyServerWorld) {
        if (anyServerWorld == null) return Optional.empty();
        if (!hasLink()) return Optional.empty();
        if (linkedDimId == null || linkedLifePos == null) return Optional.empty();

        return getLifeNoLoad(anyServerWorld, linkedDimId, linkedLifePos);
    }

    private boolean isServerStopping(ServerWorld sw) {
        MinecraftServer s = sw.getServer();
        return s != null && s.isStopping();
    }

    /** No-load lookup for the LifePointBlockEntity in a possibly different dimension. */
    private Optional<LifePointBlockEntity> getLifeNoLoad(ServerWorld contextWorld, Identifier dimId, BlockPos lifePos) {
        if (contextWorld == null || dimId == null || lifePos == null) return Optional.empty();

        MinecraftServer server = contextWorld.getServer();
        if (server == null) return Optional.empty();

        var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, dimId);
        ServerWorld target = server.getWorld(key);
        if (target == null) return Optional.empty();

        // IMPORTANT: do not load chunks here
        ChunkPos cp = new ChunkPos(lifePos);
        if (!target.getChunkManager().isChunkLoaded(cp.x, cp.z)) return Optional.empty();

        var chunk = target.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
        if (chunk == null) return Optional.empty();

        BlockEntity be = chunk.getBlockEntity(lifePos);
        return (be instanceof LifePointBlockEntity lp) ? Optional.of(lp) : Optional.empty();
    }
}
