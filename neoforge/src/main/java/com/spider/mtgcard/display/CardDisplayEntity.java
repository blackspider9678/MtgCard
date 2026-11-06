package com.spider.mtgcard.display;

import com.spider.mtgcard.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CardDisplayEntity extends HangingEntity {

    private static final double SURFACE_SEPARATION = 1.0E-4D;
    private static final double SURFACE_MATCH_EPSILON = 1.0E-3D;
    private static final double CARD_SIZE = 1.0D;
    private static final double CARD_THICKNESS = 1.0D / 16.0D;
    private static final EntityDataAccessor<Boolean> SURFACE_ANCHORED =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SURFACE_OFFSET =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SURFACE_X =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SURFACE_Y =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SURFACE_Z =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.FLOAT);


    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final EntityDataAccessor<Integer> FLAT_YAW_STEP =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<ItemStack> STACK =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> ROT_STEP =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.INT);

    public CardDisplayEntity(EntityType<? extends CardDisplayEntity> type, Level world) {
        super(type, world);
    }

    /** Convenience constructor for spawning like an item frame. */
    public CardDisplayEntity(Level world, BlockPos attachmentPos, Direction facing) {
        super(ModEntities.CARD_DISPLAY, world, attachmentPos); // âœ… sets attachedBlockPos correctly
        this.setDirection(facing);
    }


    public CardDisplayEntity(Level world, BlockPos supportPos, Direction facing,
                             double surfaceOffset, double surfaceX, double surfaceY, double surfaceZ) {
        super(ModEntities.CARD_DISPLAY, world, supportPos);
        setSurfaceAnchor(surfaceOffset, surfaceX, surfaceY, surfaceZ);
        setDirection(facing);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(ROT_STEP, 0);
        builder.define(SURFACE_ANCHORED, false);
        builder.define(SURFACE_OFFSET, 1.0F);
        builder.define(SURFACE_X, 0.5F);
        builder.define(SURFACE_Y, 0.5F);
        builder.define(SURFACE_Z, 0.5F);
        builder.define(FLAT_YAW_STEP, 0); // 0..3
    }

    public int getFlatYawStep() {
        return entityData.get(FLAT_YAW_STEP);
    }

    public void setFlatYawStep(int v) {
        entityData.set(FLAT_YAW_STEP, ((v % 4) + 4) % 4);
    }

    public void cycleFlatYaw() {
        setFlatYawStep(getFlatYawStep() - 1);
    }

    /** Called by placement code to orient flat cards without exposing protected setFacing(). */
    public void setFlatYawTowardPlayer(net.minecraft.world.entity.player.Player player) {
        // vector from card -> player (horizontal only)
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();

        // if somehow perfectly centered, fall back to player's facing
        Direction toPlayer;
        if (Math.abs(dx) < 1.0E-4 && Math.abs(dz) < 1.0E-4) {
            toPlayer = player.getDirection();
        } else {
            // closest cardinal direction toward the player
            toPlayer = Direction.getNearest(dx, 0.0, dz);
            if (!toPlayer.getAxis().isHorizontal()) {
                toPlayer = player.getDirection();
            }
        }

        // We want the "top" of the card to point toward the player.
        // Depending on your renderer's texture orientation, you may need .getOpposite() here.
        setFlatYawStep(toPlayer.get2DDataValue());

        // refresh pitch/yaw using current facing
        this.setDirection(this.getDirection());
    }

    // ---- data API ----

    public ItemStack getStack() {
        return entityData.get(STACK);
    }

    public void setStack(ItemStack stack) {
        entityData.set(STACK, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    public int getRotStep() {
        return entityData.get(ROT_STEP);
    }

    public void setRotStep(int v) {
        // only allow 0 or 1
        entityData.set(ROT_STEP, (v & 1));
    }

    public void cycleRot() {
        // toggle 0 <-> 1
        setRotStep(getRotStep() ^ 1);
    }

    @Override
    protected void setDirection(Direction facing) {
        // HangingEntity's 1.21.1 implementation only permits wall faces.
        // Like ItemFrame, cards must also support the floor and ceiling.
        this.direction = java.util.Objects.requireNonNull(facing);

        if (facing.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot(facing.get2DDataValue() * 90f);
        } else {
            // Laying flat: pitch sets up/down, yaw comes from flat step
            this.setXRot(-90f * facing.getAxisDirection().getStep());
            this.setYRot(getFlatYawStep() * 90f);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }


    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getAddEntityPacket(net.minecraft.server.level.ServerEntity tracker) {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(
                this, getDirection().get3DDataValue(), this.pos);
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if ((FLAT_YAW_STEP.equals(key) || SURFACE_ANCHORED.equals(key) || SURFACE_OFFSET.equals(key)
                || SURFACE_X.equals(key) || SURFACE_Y.equals(key) || SURFACE_Z.equals(key))
                && this.direction != null) {
            setDirection(this.direction);
        }
    }

    // ---- attachment + bounds ----

    /**
     * Make the interaction hitbox/bounds match a "thin card on a face".
     * AbstractDecorationEntity asks us for a bounding box at a block + face. :contentReference[oaicite:4]{index=4}
     */
    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
        if (entityData != null && isSurfaceAnchored()) return calculateSurfaceBoundingBox(pos, side);
        // Match vanilla item-frame wall offset:
        // center of supporting block, then push BACK into the block along the face normal
        // (this prevents the "one full air block gap" when attachment pos/facing math is involved)
        Vec3 center = Vec3.atCenterOf(pos).relative(side, -0.46875D);

        // We want a full-card hit area (1x1) but only 1/16 thick in the normal axis.
        // Box.of takes FULL sizes (not half-extents).
        double full = 1.0D;
        double thin = 1.0D / 16.0D;

        Direction.Axis axis = side.getAxis();

        double sizeX = (axis == Direction.Axis.X) ? thin : full;
        double sizeY = (axis == Direction.Axis.Y) ? thin : full;
        double sizeZ = (axis == Direction.Axis.Z) ? thin : full;

        return AABB.ofSize(center, sizeX, sizeY, sizeZ);
    }


    /**
     * This is REQUIRED to be public (super is public). :contentReference[oaicite:5]{index=5}
     * Keep it simple for now: if the attached block is gone/air, pop off.
     */
    @Override
    public boolean survives() {
        if (isSurfaceAnchored()) {
            if (!hasSurfaceAt(level(), this.pos, getDirection(), getSurfaceOffset(),
                    getSurfaceX(), getSurfaceY(), getSurfaceZ())) return false;
            return level().getEntities(this, getBoundingBox(), other -> other instanceof HangingEntity).isEmpty();
        }
        return super.survives();
    }

    private boolean isSurfaceAnchored() {
        return entityData.get(SURFACE_ANCHORED);
    }

    private float getSurfaceOffset() {
        return entityData.get(SURFACE_OFFSET);
    }

    private float getSurfaceX() {
        return entityData.get(SURFACE_X);
    }

    private float getSurfaceY() {
        return entityData.get(SURFACE_Y);
    }

    private float getSurfaceZ() {
        return entityData.get(SURFACE_Z);
    }

    private void setSurfaceAnchor(double offset, double x, double y, double z) {
        entityData.set(SURFACE_ANCHORED, true);
        entityData.set(SURFACE_OFFSET, (float) finiteCoordinate(offset));
        entityData.set(SURFACE_X, (float) finiteCoordinate(x));
        entityData.set(SURFACE_Y, (float) finiteCoordinate(y));
        entityData.set(SURFACE_Z, (float) finiteCoordinate(z));
    }

    private AABB calculateSurfaceBoundingBox(BlockPos pos, Direction side) {
        Direction.Axis axis = side.getAxis();
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double centerOnAxis = blockCoordinate(pos, axis)
                + finiteCoordinate(getSurfaceOffset())
                + surfaceSeparation(side);

        switch (axis) {
            case X -> centerX = centerOnAxis;
            case Y -> centerY = centerOnAxis;
            case Z -> centerZ = centerOnAxis;
        }

        return AABB.ofSize(new Vec3(centerX, centerY, centerZ),
                sizeForAxis(axis, Direction.Axis.X),
                sizeForAxis(axis, Direction.Axis.Y),
                sizeForAxis(axis, Direction.Axis.Z));
    }

    public static OptionalDouble findSurfaceOffset(Level world, BlockPos pos, Direction face, Vec3 hitLocation) {
        return findSurfaceOffset(getSurfaceShape(world, pos), face, hitLocation.subtract(Vec3.atLowerCornerOf(pos)));
    }

    public static OptionalDouble findSurfaceOffset(VoxelShape shape, Direction face, Vec3 hitLocation) {
        BlockPos pos = BlockPos.ZERO;
        if (shape.isEmpty()) {
            return OptionalDouble.empty();
        }

        double localX = finiteCoordinate(hitLocation.x - pos.getX());
        double localY = finiteCoordinate(hitLocation.y - pos.getY());
        double localZ = finiteCoordinate(hitLocation.z - pos.getZ());
        Direction.Axis axis = face.getAxis();
        double hitAxis = coordinateForAxis(axis, localX, localY, localZ);
        boolean positive = isPositive(face);
        List<AABB> boxes = shape.toAabbs();

        double best = positive ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (AABB box : boxes) {
            if (!containsPerpendicular(box, axis, localX, localY, localZ)) {
                continue;
            }

            double surface = positive ? maxOnAxis(box, axis) : minOnAxis(box, axis);
            if (positive) {
                if (surface <= hitAxis + SURFACE_MATCH_EPSILON
                        && surface > best
                        && isSurfaceExposed(boxes, face, surface, localX, localY, localZ)) {
                    best = surface;
                }
            } else {
                if (surface >= hitAxis - SURFACE_MATCH_EPSILON
                        && surface < best
                        && isSurfaceExposed(boxes, face, surface, localX, localY, localZ)) {
                    best = surface;
                }
            }
        }

        if (Double.isFinite(best)) {
            return OptionalDouble.of(finiteCoordinate(best));
        }
        return OptionalDouble.empty();
    }

    private static boolean hasSurfaceAt(Level world, BlockPos pos, Direction face,
                                        double surfaceOffset, double localX, double localY, double localZ) {
        return hasSurfaceAt(getSurfaceShape(world, pos), face, surfaceOffset, localX, localY, localZ);
    }

    public static boolean hasSurfaceAt(VoxelShape shape, Direction face,
                                       double surfaceOffset, double localX, double localY, double localZ) {
        if (shape.isEmpty()) {
            return false;
        }

        Direction.Axis axis = face.getAxis();
        double surface = finiteCoordinate(surfaceOffset);
        double x = finiteCoordinate(localX);
        double y = finiteCoordinate(localY);
        double z = finiteCoordinate(localZ);
        List<AABB> boxes = shape.toAabbs();

        for (AABB box : boxes) {
            if (!containsPerpendicular(box, axis, x, y, z)) {
                continue;
            }

            double candidate = isPositive(face) ? maxOnAxis(box, axis) : minOnAxis(box, axis);
            if (Math.abs(candidate - surface) <= SURFACE_MATCH_EPSILON
                    && isSurfaceExposed(boxes, face, surface, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static VoxelShape getSurfaceShape(Level world, BlockPos pos) {
        return world.getBlockState(pos).getShape(world, pos);
    }

    private static boolean containsPerpendicular(AABB box, Direction.Axis normalAxis, double x, double y, double z) {
        return switch (normalAxis) {
            case X -> contains(box.minY, box.maxY, y) && contains(box.minZ, box.maxZ, z);
            case Y -> contains(box.minX, box.maxX, x) && contains(box.minZ, box.maxZ, z);
            case Z -> contains(box.minX, box.maxX, x) && contains(box.minY, box.maxY, y);
        };
    }

    private static boolean contains(double min, double max, double value) {
        return value >= min - SURFACE_MATCH_EPSILON && value <= max + SURFACE_MATCH_EPSILON;
    }

    private static boolean isSurfaceExposed(List<AABB> boxes, Direction face, double surface,
                                            double x, double y, double z) {
        Direction.Axis axis = face.getAxis();
        boolean positive = isPositive(face);
        double outside = surface + (positive ? SURFACE_MATCH_EPSILON : -SURFACE_MATCH_EPSILON);

        for (AABB box : boxes) {
            if (!containsPerpendicular(box, axis, x, y, z)) {
                continue;
            }

            if (minOnAxis(box, axis) < outside && maxOnAxis(box, axis) > outside) {
                return false;
            }
        }
        return true;
    }

    private static double coordinateForAxis(Direction.Axis axis, double x, double y, double z) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    private static double blockCoordinate(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static double minOnAxis(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double maxOnAxis(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static boolean isPositive(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
    }

    private static double surfaceSeparation(Direction direction) {
        return isPositive(direction) ? SURFACE_SEPARATION : -SURFACE_SEPARATION;
    }

    private static double finiteCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return value;
    }

    private static double sizeForAxis(Direction.Axis normalAxis, Direction.Axis sizeAxis) {
        return normalAxis == sizeAxis ? CARD_THICKNESS : CARD_SIZE;
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (entityData != null && this.pos != null && isSurfaceAnchored()) {
            recalculateBoundingBox();
            return;
        }
        super.setPos(x, y, z);
    }

    // ---- behavior ----

    private int attachGrace = 5;

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            if (attachGrace > 0) {
                attachGrace--;
                return;
            }

            if (!this.survives()) {
                this.dropAsItem((ServerLevel) this.level(), null);
                this.discard();
            }
        }
    }


    @Override
    public void playPlacementSound() {
        // Called after spawn/placement for attached entities.
        // You can play a sound or validate here later.
    }

    @Override
    public void dropItem(Entity breaker) {
        // Called when the hanging entity is broken â€œproperlyâ€.
        if (this.level() instanceof ServerLevel world) {
            dropAsItem(world, null);
        }
    }

    /** Drop the stored card stack */
    public void dropAsItem(ServerLevel world, DamageSource source) {
        ItemStack st = getStack();
        if (!st.isEmpty()) {
            this.spawnAtLocation(st.copy());
        }
        setStack(ItemStack.EMPTY);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Break like a frame: drop and remove
        if (this.level() instanceof ServerLevel world) {
            this.dropAsItem(world, source);
            this.discard();
        }
        return true;
    }

    @Override
    public InteractionResult interact(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                ItemStack st = this.getStack().copy();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        sp,
                        new com.spider.mtgcard.net.payload.CardDisplayPayloads.OpenDisplayViewS2C(this.getId(), st)
                );
            }
            return InteractionResult.CONSUME;
        }

        this.cycleRot();
        return InteractionResult.CONSUME;
    }


    // ---- persistence ----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack st = getStack();
        if (!st.isEmpty()) tag.put("Card", st.save(this.registryAccess()));
        tag.putInt("Rot", getRotStep());
        tag.putInt("Facing", this.getDirection().get3DDataValue());
        tag.putInt("FlatYaw", getFlatYawStep());
        if (isSurfaceAnchored()) {
            tag.putBoolean("SurfaceAnchored", true);
            tag.putFloat("SurfaceOffset", getSurfaceOffset());
            tag.putFloat("SurfaceX", getSurfaceX());
            tag.putFloat("SurfaceY", getSurfaceY());
            tag.putFloat("SurfaceZ", getSurfaceZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("SurfaceAnchored")) {
            setSurfaceAnchor(tag.getFloat("SurfaceOffset"), tag.getFloat("SurfaceX"),
                    tag.getFloat("SurfaceY"), tag.getFloat("SurfaceZ"));
        }
        setStack(tag.contains("Card", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound("Card"))
                : ItemStack.EMPTY);
        int r = tag.getInt("Rot");
        setRotStep(r == 2 ? 1 : r);
        setFlatYawStep(tag.getInt("FlatYaw"));
        Direction f = tag.contains("Facing") ? Direction.from3DDataValue(tag.getInt("Facing")) : this.getDirection();
        this.setDirection(f);
    }

}
