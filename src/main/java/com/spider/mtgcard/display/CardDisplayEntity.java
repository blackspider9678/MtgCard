package com.spider.mtgcard.display;

import com.spider.mtgcard.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class CardDisplayEntity extends HangingEntity {

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
        super(ModEntities.CARD_DISPLAY, world, attachmentPos); // ✅ sets attachedBlockPos correctly
        this.setDirection(facing);
    }


    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STACK, ItemStack.EMPTY);
        builder.define(ROT_STEP, 0);
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
            toPlayer = Direction.getApproximateNearest(dx, 0.0, dz);
            if (!toPlayer.getAxis().isHorizontal()) {
                toPlayer = player.getDirection();
            }
        }

        // We want the "top" of the card to point toward the player.
        // Depending on your renderer's texture orientation, you may need .getOpposite() here.
        setFlatYawStep(toPlayer.get2DDataValue());

        // refresh pitch/yaw using current facing
        this.setDirection(this.getNearestViewDirection());
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
        return super.survives();
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
        view.store("Facing", Direction.LEGACY_ID_CODEC, this.getNearestViewDirection());
        view.putInt("FlatYaw", getFlatYawStep());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        setStack(view.read("Card", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        int r = view.getIntOr("Rot", 0);
        setRotStep(r == 2 ? 1 : r);
        setFlatYawStep(view.getIntOr("FlatYaw", 0));
        Direction f = view.read("Facing", Direction.LEGACY_ID_CODEC).orElse(this.getNearestViewDirection());
        this.setDirection(f);
    }

}
