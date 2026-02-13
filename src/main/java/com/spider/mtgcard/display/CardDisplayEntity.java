package com.spider.mtgcard.display;

import com.spider.mtgcard.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

public class CardDisplayEntity extends AbstractDecorationEntity {

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final TrackedData<Integer> FLAT_YAW_STEP =
            DataTracker.registerData(CardDisplayEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<ItemStack> STACK =
            DataTracker.registerData(CardDisplayEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private static final TrackedData<Integer> ROT_STEP =
            DataTracker.registerData(CardDisplayEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public CardDisplayEntity(EntityType<? extends CardDisplayEntity> type, World world) {
        super(type, world);
    }

    /** Convenience constructor for spawning like an item frame. */
    public CardDisplayEntity(World world, BlockPos attachmentPos, Direction facing) {
        super(ModEntities.CARD_DISPLAY, world, attachmentPos); // ✅ sets attachedBlockPos correctly
        this.setFacing(facing);
    }


    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STACK, ItemStack.EMPTY);
        builder.add(ROT_STEP, 0);
        builder.add(FLAT_YAW_STEP, 0); // 0..3
    }

    public int getFlatYawStep() {
        return dataTracker.get(FLAT_YAW_STEP);
    }

    public void setFlatYawStep(int v) {
        dataTracker.set(FLAT_YAW_STEP, ((v % 4) + 4) % 4);
    }

    public void cycleFlatYaw() {
        setFlatYawStep(getFlatYawStep() - 1);
    }

    /** Called by placement code to orient flat cards without exposing protected setFacing(). */
    public void setFlatYawTowardPlayer(net.minecraft.entity.player.PlayerEntity player) {
        // vector from card -> player (horizontal only)
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();

        // if somehow perfectly centered, fall back to player's facing
        Direction toPlayer;
        if (Math.abs(dx) < 1.0E-4 && Math.abs(dz) < 1.0E-4) {
            toPlayer = player.getHorizontalFacing();
        } else {
            // closest cardinal direction toward the player
            toPlayer = Direction.getFacing(dx, 0.0, dz);
            if (!toPlayer.getAxis().isHorizontal()) {
                toPlayer = player.getHorizontalFacing();
            }
        }

        // We want the "top" of the card to point toward the player.
        // Depending on your renderer's texture orientation, you may need .getOpposite() here.
        setFlatYawStep(toPlayer.getHorizontalQuarterTurns());

        // refresh pitch/yaw using current facing
        this.setFacing(this.getFacing());
    }

    // ---- data API ----

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    public void setStack(ItemStack stack) {
        dataTracker.set(STACK, stack == null ? ItemStack.EMPTY : stack.copy());
    }

    public int getRotStep() {
        return dataTracker.get(ROT_STEP);
    }

    public void setRotStep(int v) {
        // only allow 0 or 1
        dataTracker.set(ROT_STEP, (v & 1));
    }

    public void cycleRot() {
        // toggle 0 <-> 1
        setRotStep(getRotStep() ^ 1);
    }

    @Override
    protected void setFacing(Direction facing) {
        super.setFacingInternal(facing);

        if (facing.getAxis().isHorizontal()) {
            this.setPitch(0.0F);
            this.setYaw(facing.getHorizontalQuarterTurns() * 90f);
        } else {
            // Laying flat: pitch sets up/down, yaw comes from flat step
            this.setPitch(-90f * facing.getDirection().offset());
            this.setYaw(getFlatYawStep() * 90f);
        }

        this.lastPitch = this.getPitch();
        this.lastYaw = this.getYaw();
        this.updateAttachmentPosition();
    }


    // ---- attachment + bounds ----

    /**
     * Make the interaction hitbox/bounds match a "thin card on a face".
     * AbstractDecorationEntity asks us for a bounding box at a block + face. :contentReference[oaicite:4]{index=4}
     */
    @Override
    protected Box calculateBoundingBox(BlockPos pos, Direction side) {
        // Match vanilla item-frame wall offset:
        // center of supporting block, then push BACK into the block along the face normal
        // (this prevents the "one full air block gap" when attachment pos/facing math is involved)
        Vec3d center = Vec3d.ofCenter(pos).offset(side, -0.46875D);

        // We want a full-card hit area (1x1) but only 1/16 thick in the normal axis.
        // Box.of takes FULL sizes (not half-extents).
        double full = 1.0D;
        double thin = 1.0D / 16.0D;

        Direction.Axis axis = side.getAxis();

        double sizeX = (axis == Direction.Axis.X) ? thin : full;
        double sizeY = (axis == Direction.Axis.Y) ? thin : full;
        double sizeZ = (axis == Direction.Axis.Z) ? thin : full;

        return Box.of(center, sizeX, sizeY, sizeZ);
    }


    /**
     * This is REQUIRED to be public (super is public). :contentReference[oaicite:5]{index=5}
     * Keep it simple for now: if the attached block is gone/air, pop off.
     */
    @Override
    public boolean canStayAttached() {
        return super.canStayAttached();
    }

    // ---- behavior ----

    private int attachGrace = 5;

    @Override
    public void tick() {
        super.tick();

        if (!getEntityWorld().isClient()) {
            if (attachGrace > 0) {
                attachGrace--;
                return;
            }

            if (!this.canStayAttached()) {
                this.dropAsItem((ServerWorld) this.getEntityWorld(), null);
                this.discard();
            }
        }
    }


    @Override
    public void onPlace() {
        // Called after spawn/placement for attached entities.
        // You can play a sound or validate here later.
    }

    @Override
    public void onBreak(ServerWorld world, @Nullable Entity breaker) {
        // Called when the hanging entity is broken “properly”.
        dropAsItem(world, null);
    }

    /** Drop the stored card stack */
    public void dropAsItem(ServerWorld world, @Nullable DamageSource source) {
        ItemStack st = getStack();
        if (!st.isEmpty()) {
            this.dropStack(world, st.copy());
        }
        setStack(ItemStack.EMPTY);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Break like a frame: drop and remove
        this.dropAsItem(world, source);
        this.discard();
        return true;
    }

    @Override
    public ActionResult interact(net.minecraft.entity.player.PlayerEntity player, Hand hand) {
        if (this.getEntityWorld().isClient()) return ActionResult.SUCCESS;

        if (player.isSneaking()) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                ItemStack st = this.getStack().copy();
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        sp,
                        new com.spider.mtgcard.net.payload.CardDisplayPayloads.OpenDisplayViewS2C(this.getId(), st)
                );
            }
            return ActionResult.CONSUME;
        }

        this.cycleRot();
        return ActionResult.CONSUME;
    }


    // ---- persistence ----
    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        ItemStack st = getStack();
        if (!st.isEmpty()) view.put("Card", ItemStack.CODEC, st);
        view.putInt("Rot", getRotStep());
        view.put("Facing", Direction.INDEX_CODEC, this.getFacing());
        view.putInt("FlatYaw", getFlatYawStep());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        setStack(view.read("Card", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        int r = view.getInt("Rot", 0);
        setRotStep(r == 2 ? 1 : r);
        setFlatYawStep(view.getInt("FlatYaw", 0));
        Direction f = view.read("Facing", Direction.INDEX_CODEC).orElse(this.getFacing());
        this.setFacing(f);
    }

}
