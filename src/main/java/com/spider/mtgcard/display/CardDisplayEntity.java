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

    private static final EntityDataAccessor<Float> SURFACE_Z =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.FLOAT);

    public CardDisplayEntity(EntityType<? extends CardDisplayEntity> type, Level world) {
        super(type, world);
    }

    /** Convenience constructor for spawning like an item frame. */
    public CardDisplayEntity(Level world, BlockPos attachmentPos, Direction facing) {
        super(ModEntities.CARD_DISPLAY, world, attachmentPos); // ✅ sets attachedBlockPos correctly
        this.setDirection(facing);
    }

    /** Creates a flat display anchored to the actual horizontal surface that was clicked. */
    public CardDisplayEntity(Level world, BlockPos supportPos, Direction facing,
                             double surfaceOffset, double surfaceX, double surfaceZ) {
        super(ModEntities.CARD_DISPLAY, world, supportPos);
        setSurfaceAnchor(surfaceOffset, surfaceX, surfaceZ);
        this.setDirection(facing);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(ROT_STEP, 0);
        builder.define(FLAT_YAW_STEP, 0); // 0..3
        builder.define(SURFACE_ANCHORED, false);
        builder.define(SURFACE_OFFSET, 1.0F);
        builder.define(SURFACE_X, 0.5F);
        builder.define(SURFACE_Z, 0.5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data.equals(SURFACE_ANCHORED)
                || data.equals(SURFACE_OFFSET)
                || data.equals(SURFACE_X)
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

    private float getSurfaceZ() {
        return entityData.get(SURFACE_Z);
    }

    private void setSurfaceAnchor(double offset, double x, double z) {
        entityData.set(SURFACE_ANCHORED, true);
        entityData.set(SURFACE_OFFSET, (float) clamp01(offset));
        entityData.set(SURFACE_X, (float) clamp01(x));
        entityData.set(SURFACE_Z, (float) clamp01(z));
    }

    private boolean isHorizontalSurfaceAnchored() {
        return isSurfaceAnchored() && this.pos != null && this.getDirection().getAxis() == Direction.Axis.Y;
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
            toPlayer = Direction.getApproximateNearest(dx, 0.0, dz);
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
    public Component getName() {
        ItemStack stack = getStack();
        if (!stack.isEmpty() && !StackData.readHidden(stack)) {
            return stack.getHoverName();
        }

        return Component.translatable("entity.mtgcard.card_display");
    }

    @Override
    public void setPos(double x, double y, double z) {
        if (isHorizontalSurfaceAnchored()) {
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
            // Laying flat: pitch sets up/down, yaw comes from flat step
            this.setXRot(-90f * facing.getAxisDirection().getStep());
            this.setYRot(getFlatYawStep() * 90f);
        }

        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.recalculateBoundingBox();
    }


    // ---- attachment + bounds ----

    /**
     * Make the interaction hitbox/bounds match a "thin card on a face".
     * AbstractDecorationEntity asks us for a bounding box at a block + face. :contentReference[oaicite:4]{index=4}
     */
    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction side) {
        if (isSurfaceAnchored() && side.getAxis() == Direction.Axis.Y) {
            double surfaceY = pos.getY() + clamp01(getSurfaceOffset());
            double centerY = surfaceY + (side == Direction.UP ? SURFACE_SEPARATION : -SURFACE_SEPARATION);
            Vec3 center = new Vec3(pos.getX() + 0.5D, centerY, pos.getZ() + 0.5D);
            return AABB.ofSize(center, CARD_SIZE, CARD_THICKNESS, CARD_SIZE);
        }

        // Match vanilla item-frame wall offset:
        // center of supporting block, then push BACK into the block along the face normal
        // (this prevents the "one full air block gap" when attachment pos/facing math is involved)
        Vec3 center = Vec3.atCenterOf(pos).relative(side, -FACE_OFFSET);

        // We want a full-card hit area (1x1) but only 1/16 thick in the normal axis.
        // Box.of takes FULL sizes (not half-extents).
        Direction.Axis axis = side.getAxis();

        double sizeX = (axis == Direction.Axis.X) ? CARD_THICKNESS : CARD_SIZE;
        double sizeY = (axis == Direction.Axis.Y) ? CARD_THICKNESS : CARD_SIZE;
        double sizeZ = (axis == Direction.Axis.Z) ? CARD_THICKNESS : CARD_SIZE;

        return AABB.ofSize(center, sizeX, sizeY, sizeZ);
    }


    /**
     * This is REQUIRED to be public (super is public). :contentReference[oaicite:5]{index=5}
     * Keep it simple for now: if the attached block is gone/air, pop off.
     */
    @Override
    public boolean survives() {
        Direction facing = this.getDirection();
        if (isSurfaceAnchored() && facing.getAxis() == Direction.Axis.Y) {
            return survivesOnHorizontalSurface(facing);
        }

        return super.survives();
    }

    private boolean survivesOnHorizontalSurface(Direction facing) {
        if (!hasHorizontalSurfaceAt(this.level(), this.getPos(), facing,
                getSurfaceOffset(), getSurfaceX(), getSurfaceZ())) {
            return false;
        }

        return this.canCoexist(false);
    }

    public static OptionalDouble findHorizontalSurfaceOffset(Level world, BlockPos pos, Direction face, Vec3 hitLocation) {
        if (face.getAxis() != Direction.Axis.Y) {
            return OptionalDouble.empty();
        }

        VoxelShape shape = getSurfaceShape(world, pos);
        if (shape.isEmpty()) {
            return OptionalDouble.empty();
        }

        double localX = clamp01(hitLocation.x - pos.getX());
        double localY = clamp01(hitLocation.y - pos.getY());
        double localZ = clamp01(hitLocation.z - pos.getZ());
        List<AABB> boxes = shape.toAabbs();

        double best = (face == Direction.UP) ? -1.0D : 2.0D;
        for (AABB box : boxes) {
            if (!containsHorizontal(box, localX, localZ)) {
                continue;
            }

            double surface = (face == Direction.UP) ? box.maxY : box.minY;
            if (face == Direction.UP) {
                if (surface <= localY + SURFACE_MATCH_EPSILON
                        && surface > best
                        && isSurfaceExposed(boxes, face, surface, localX, localZ)) {
                    best = surface;
                }
            } else {
                if (surface >= localY - SURFACE_MATCH_EPSILON
                        && surface < best
                        && isSurfaceExposed(boxes, face, surface, localX, localZ)) {
                    best = surface;
                }
            }
        }

        if (face == Direction.UP && best >= 0.0D) {
            return OptionalDouble.of(clamp01(best));
        }
        if (face == Direction.DOWN && best <= 1.0D) {
            return OptionalDouble.of(clamp01(best));
        }
        return OptionalDouble.empty();
    }

    private static boolean hasHorizontalSurfaceAt(Level world, BlockPos pos, Direction face,
                                                  double surfaceOffset, double localX, double localZ) {
        VoxelShape shape = getSurfaceShape(world, pos);
        if (shape.isEmpty()) {
            return false;
        }

        double surface = clamp01(surfaceOffset);
        double x = clamp01(localX);
        double z = clamp01(localZ);
        List<AABB> boxes = shape.toAabbs();

        for (AABB box : boxes) {
            if (!containsHorizontal(box, x, z)) {
                continue;
            }

            double candidate = (face == Direction.UP) ? box.maxY : box.minY;
            if (Math.abs(candidate - surface) <= SURFACE_MATCH_EPSILON
                    && isSurfaceExposed(boxes, face, surface, x, z)) {
                return true;
            }
        }
        return false;
    }

    private static VoxelShape getSurfaceShape(Level world, BlockPos pos) {
        return world.getBlockState(pos).getShape(world, pos);
    }

    private static boolean containsHorizontal(AABB box, double x, double z) {
        return x >= box.minX - SURFACE_MATCH_EPSILON
                && x <= box.maxX + SURFACE_MATCH_EPSILON
                && z >= box.minZ - SURFACE_MATCH_EPSILON
                && z <= box.maxZ + SURFACE_MATCH_EPSILON;
    }

    private static boolean isSurfaceExposed(List<AABB> boxes, Direction face, double surface, double x, double z) {
        for (AABB box : boxes) {
            if (!containsHorizontal(box, x, z)) {
                continue;
            }

            if (face == Direction.UP) {
                if (box.minY < surface + SURFACE_MATCH_EPSILON
                        && box.maxY > surface + SURFACE_MATCH_EPSILON) {
                    return false;
                }
            } else {
                if (box.minY < surface - SURFACE_MATCH_EPSILON
                        && box.maxY > surface - SURFACE_MATCH_EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
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
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entityTracker) {
        return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.pos = BlockPos.containing(packet.getX(), packet.getY(), packet.getZ());
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public void dropItem(ServerLevel world, @Nullable Entity breaker) {
        // Called when the hanging entity is broken “properly”.
        dropAsItem(world, null);
    }

    /** Drop the stored card stack */
    public void dropAsItem(ServerLevel world, @Nullable DamageSource source) {
        ItemStack st = getStack();
        if (!st.isEmpty()) {
            this.spawnAtLocation(world, st.copy());
        }
        setStack(ItemStack.EMPTY);
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        // Break like a frame: drop and remove
        this.dropAsItem(world, source);
        this.discard();
        return true;
    }

    @Override
    public InteractionResult interact(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
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


    // ---- persistence ----
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
                    view.getFloatOr("SurfaceZ", 0.5F)
            );
        } else {
            entityData.set(SURFACE_ANCHORED, false);
        }
        Direction f = view.read("Facing", Direction.LEGACY_ID_CODEC).orElse(this.getNearestViewDirection());
        this.setDirection(f);
    }

}
