package com.spider.mtgcard.display;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.util.StackData;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class CardDisplayEntity extends HangingEntity {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final double CARD_SIZE = 1.0D;
    private static final double CARD_THICKNESS = 1.0D / 16.0D;
    private static final double FACE_OFFSET = 0.46875D;
    private static final double SURFACE_SEPARATION = 1.0E-4D;
    private static final double SURFACE_MATCH_EPSILON = 1.0E-3D;

    private static final EntityDataAccessor<Integer> FLAT_YAW_STEP =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<ItemStack> STACK =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> ROT_STEP =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.INT);

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

    public CardDisplayEntity(EntityType<? extends CardDisplayEntity> type, Level world) {
        super(type, world);
    }

    public CardDisplayEntity(Level world, BlockPos attachmentPos, Direction facing) {
        super(ModEntities.CARD_DISPLAY, world, attachmentPos);
        this.setDirection(facing);
    }

    public CardDisplayEntity(Level world, BlockPos supportPos, Direction facing,
                             double surfaceOffset, double surfaceX, double surfaceY, double surfaceZ) {
        super(ModEntities.CARD_DISPLAY, world, supportPos);
        setSurfaceAnchor(surfaceOffset, surfaceX, surfaceY, surfaceZ);
        this.setDirection(facing);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(ROT_STEP, 0);
        builder.define(FLAT_YAW_STEP, 0);
        builder.define(SURFACE_ANCHORED, false);
        builder.define(SURFACE_OFFSET, 1.0F);
        builder.define(SURFACE_X, 0.5F);
        builder.define(SURFACE_Y, 0.5F);
        builder.define(SURFACE_Z, 0.5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data.equals(SURFACE_ANCHORED)
                || data.equals(SURFACE_OFFSET)
                || data.equals(SURFACE_X)
                || data.equals(SURFACE_Y)
                || data.equals(SURFACE_Z)) {
            this.recalculateBoundingBox();
        }
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
        entityData.set(SURFACE_OFFSET, (float) clamp01(offset));
        entityData.set(SURFACE_X, (float) clamp01(x));
        entityData.set(SURFACE_Y, (float) clamp01(y));
        entityData.set(SURFACE_Z, (float) clamp01(z));
    }

    private boolean isShapeSurfaceAnchored() {
        return isSurfaceAnchored() && this.pos != null;
    }

    public void setFlatYawTowardPlayer(net.minecraft.world.entity.player.Player player) {
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();

        Direction toPlayer;
        if (Math.abs(dx) < 1.0E-4 && Math.abs(dz) < 1.0E-4) {
            toPlayer = player.getDirection();
        } else {
            toPlayer = Direction.getApproximateNearest(dx, 0.0, dz);
            if (!toPlayer.getAxis().isHorizontal()) {
                toPlayer = player.getDirection();
            }
        }

        setFlatYawStep(toPlayer.get2DDataValue());
        this.setDirection(this.getDirection());
    }

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
        entityData.set(ROT_STEP, (v & 1));
    }

    public void cycleRot() {
        setRotStep(getRotStep() ^ 1);
    }

    @Override
    public Component getName() {
        ItemStack stack = getStack();
        if (!stack.isEmpty() && !StackData.readHidden(stack)) {
            return stack.getHoverName();
        }

        return Component.translatable("entity.mtgcard.card_display");
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (isShapeSurfaceAnchored()) {
            this.recalculateBoundingBox();
            return;
        }

        super.setPos(x, y, z);
    }

    @Override
    protected void setDirection(Direction facing) {
        super.setDirectionRaw(facing);

        if (facing.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot(facing.get2DDataValue() * 90f);
        } else {
            this.setXRot(-90f * facing.getAxisDirection().getStep());
            this.setYRot(getFlatYawStep() * 90f);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
        if (isSurfaceAnchored()) {
            return calculateSurfaceBoundingBox(pos, side);
        }

        Vec3 center = Vec3.atCenterOf(pos).relative(side, -FACE_OFFSET);
        return AABB.ofSize(center,
                sizeForAxis(side.getAxis(), Direction.Axis.X),
                sizeForAxis(side.getAxis(), Direction.Axis.Y),
                sizeForAxis(side.getAxis(), Direction.Axis.Z));
    }

    private AABB calculateSurfaceBoundingBox(BlockPos pos, Direction side) {
        Direction.Axis axis = side.getAxis();
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double centerOnAxis = blockCoordinate(pos, axis)
                + clamp01(getSurfaceOffset())
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

    private static double sizeForAxis(Direction.Axis normalAxis, Direction.Axis sizeAxis) {
        return normalAxis == sizeAxis ? CARD_THICKNESS : CARD_SIZE;
    }

    @Override
    public boolean survives() {
        Direction facing = this.getDirection();
        if (isSurfaceAnchored()) {
            return survivesOnSurface(facing);
        }

        return super.survives();
    }

    private boolean survivesOnSurface(Direction facing) {
        if (!hasSurfaceAt(this.level(), this.getPos(), facing,
                getSurfaceOffset(), getSurfaceX(), getSurfaceY(), getSurfaceZ())) {
            return false;
        }

        return this.canCoexist(false);
    }

    public static OptionalDouble findSurfaceOffset(Level world, BlockPos pos, Direction face, Vec3 hitLocation) {
        VoxelShape shape = getSurfaceShape(world, pos);
        if (shape.isEmpty()) {
            return OptionalDouble.empty();
        }

        double localX = clamp01(hitLocation.x - pos.getX());
        double localY = clamp01(hitLocation.y - pos.getY());
        double localZ = clamp01(hitLocation.z - pos.getZ());
        Direction.Axis axis = face.getAxis();
        double hitAxis = coordinateForAxis(axis, localX, localY, localZ);
        boolean positive = isPositive(face);
        List<AABB> boxes = shape.toAabbs();

        double best = positive ? -1.0D : 2.0D;
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

        if (positive && best >= 0.0D) {
            return OptionalDouble.of(clamp01(best));
        }
        if (!positive && best <= 1.0D) {
            return OptionalDouble.of(clamp01(best));
        }
        return OptionalDouble.empty();
    }

    public static OptionalDouble findHorizontalSurfaceOffset(Level world, BlockPos pos, Direction face, Vec3 hitLocation) {
        if (face.getAxis() != Direction.Axis.Y) {
            return OptionalDouble.empty();
        }

        return findSurfaceOffset(world, pos, face, hitLocation);
    }

    private static boolean hasSurfaceAt(Level world, BlockPos pos, Direction face,
                                        double surfaceOffset, double localX, double localY, double localZ) {
        VoxelShape shape = getSurfaceShape(world, pos);
        if (shape.isEmpty()) {
            return false;
        }

        Direction.Axis axis = face.getAxis();
        double surface = clamp01(surfaceOffset);
        double x = clamp01(localX);
        double y = clamp01(localY);
        double z = clamp01(localZ);
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

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

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
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTracker) {
        return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.pos = BlockPos.containing(packet.getX(), packet.getY(), packet.getZ());
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    public void dropItem(ServerLevel world, @Nullable Entity breaker) {
        dropAsItem(world, null);
    }

    public void dropAsItem(ServerLevel world, @Nullable DamageSource source) {
        ItemStack st = getStack();
        if (!st.isEmpty()) {
            this.spawnAtLocation(world, st.copy());
        }
        setStack(ItemStack.EMPTY);
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        this.dropAsItem(world, source);
        this.discard();
        return true;
    }

    @Override
    public InteractionResult interact(net.minecraft.world.entity.player.Player player, InteractionHand hand, Vec3 location) {
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                ItemStack st = this.getStack().copy();
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        sp,
                        new com.spider.mtgcard.net.payload.CardDisplayPayloads.OpenDisplayViewS2C(this.getId(), st)
                );
            }
            return InteractionResult.CONSUME;
        }

        this.cycleRot();
        return InteractionResult.CONSUME;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        ItemStack st = getStack();
        if (!st.isEmpty()) view.store("Card", ItemStack.CODEC, st);
        view.putInt("Rot", getRotStep());
        view.store("Facing", Direction.LEGACY_ID_CODEC, this.getDirection());
        view.putInt("FlatYaw", getFlatYawStep());
        if (isSurfaceAnchored()) {
            view.putBoolean("SurfaceAnchored", true);
            view.putFloat("SurfaceOffset", getSurfaceOffset());
            view.putFloat("SurfaceX", getSurfaceX());
            view.putFloat("SurfaceY", getSurfaceY());
            view.putFloat("SurfaceZ", getSurfaceZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        setStack(view.read("Card", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        int r = view.getIntOr("Rot", 0);
        setRotStep(r == 2 ? 1 : r);
        setFlatYawStep(view.getIntOr("FlatYaw", 0));
        if (view.getBooleanOr("SurfaceAnchored", false)) {
            setSurfaceAnchor(
                    view.getFloatOr("SurfaceOffset", 1.0F),
                    view.getFloatOr("SurfaceX", 0.5F),
                    view.getFloatOr("SurfaceY", 0.5F),
                    view.getFloatOr("SurfaceZ", 0.5F)
            );
        } else {
            entityData.set(SURFACE_ANCHORED, false);
        }
        Direction f = view.read("Facing", Direction.LEGACY_ID_CODEC).orElse(this.getNearestViewDirection());
        this.setDirection(f);
    }
}
