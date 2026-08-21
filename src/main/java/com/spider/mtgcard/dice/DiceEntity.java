package com.spider.mtgcard.dice;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.item.DiceItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public final class DiceEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> VALUE = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PLACED = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ANNOUNCED = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SIDES = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROT_X = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROT_Y = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROT_Z = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROT_W = SynchedEntityData.defineId(DiceEntity.class, EntityDataSerializers.FLOAT);
    private Component roller = Component.literal("Someone");
    private int stillTicks;
    private boolean settled;
    private boolean sleeping;
    private double sleepX;
    private double sleepY;
    private double sleepZ;
    private final Vector3f angularVelocity = new Vector3f(5.2f, 2.8f, 4.1f);
    private final Quaternionf clientPreviousRotation = new Quaternionf();
    private final Quaternionf clientRenderRotation = new Quaternionf();
    private boolean clientRotationInitialized;
    private int playerContactCooldown;
    private int diceCollisionGraceTicks;
    private int placedRerollTicks;
    private int placedRerollResult = 1;
    private boolean placedNeedsSeating = true;
    private static final float HALF_SIZE = 0.25f;
    private static final float INV_INERTIA = 24.0f; // 1 / (m*s^2/6), m=1 and s=.5
    private static final int PHYSICS_STEPS = 4;

    public DiceEntity(EntityType<? extends DiceEntity> type, Level level) {
        super(type, level);
        entityData.set(SIDES, sidesForType(type));
    }

    public DiceEntity(ServerLevel level, ItemStack stack, Component roller, int sides) {
        this(typeForSides(sides), level);
        entityData.set(SIDES, sides);
        setStack(stack);
        this.roller = roller.copy();
        setValue(level.random.nextInt(sides) + 1);
        diceCollisionGraceTicks = 8;
    }

    private static EntityType<DiceEntity> typeForSides(int sides) {
        return switch (sides) {
            case 4 -> ModEntities.D4_DICE; case 8 -> ModEntities.D8_DICE; case 10 -> ModEntities.D10_DICE;
            case 12 -> ModEntities.D12_DICE; case 20 -> ModEntities.D20_DICE; case 100 -> ModEntities.D100_DICE;
            default -> ModEntities.D6_DICE;
        };
    }

    private static int sidesForType(EntityType<?> type) {
        if (type == ModEntities.D4_DICE) return 4; if (type == ModEntities.D8_DICE) return 8;
        if (type == ModEntities.D10_DICE) return 10; if (type == ModEntities.D12_DICE) return 12;
        if (type == ModEntities.D20_DICE) return 20; if (type == ModEntities.D100_DICE) return 100;
        return 6;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(STACK, ItemStack.EMPTY);
        b.define(VALUE, 1);
        b.define(PLACED, false);
        b.define(ANNOUNCED, false);
        b.define(SIDES, 6);
        b.define(ROT_X, 0f); b.define(ROT_Y, 0f); b.define(ROT_Z, 0f); b.define(ROT_W, 1f);
    }

    public ItemStack getStack() { return entityData.get(STACK); }
    public void setStack(ItemStack stack) { entityData.set(STACK, stack.copyWithCount(1)); }
    public int getValue() { return entityData.get(VALUE); }
    public int getSides() { return entityData.get(SIDES); }
    private void setValue(int value) {
        int safe = Math.clamp(value, 1, getSides());
        entityData.set(VALUE, safe);
    }
    public boolean isPlaced() { return entityData.get(PLACED); }
    public Quaternionf getDiceRotation() { return new Quaternionf(entityData.get(ROT_X), entityData.get(ROT_Y), entityData.get(ROT_Z), entityData.get(ROT_W)); }
    public Quaternionf getRenderRotation(float partialTick) {
        if (!level().isClientSide()) return getDiceRotation();
        return new Quaternionf(clientPreviousRotation).slerp(clientRenderRotation, Math.clamp(partialTick, 0f, 1f));
    }
    private void setDiceRotation(Quaternionf q) {
        q.normalize();
        entityData.set(ROT_X, q.x); entityData.set(ROT_Y, q.y); entityData.set(ROT_Z, q.z); entityData.set(ROT_W, q.w);
    }

    public void place(float yaw) {
        entityData.set(PLACED, true);
        entityData.set(ANNOUNCED, true);
        setYRot(Math.round(yaw / 90f) * 90f);
        setXRot(0);
        setDiceRotation(rotationForTop(getValue(), yaw));
        float lowest=Float.MAX_VALUE;Quaternionf placedRotation=getDiceRotation();
        for(Vector3f vertex:localCorners())lowest=Math.min(lowest,placedRotation.transform(new Vector3f(vertex)).y);
        setPos(getX(),getY()-(HALF_SIZE+lowest),getZ());
        setDeltaMovement(Vec3.ZERO);
    }

    public void shootFromRotation(Entity thrower, float pitch, float yaw, float roll, float speed, float divergence) {
        float ry = (float)Math.toRadians(yaw);
        float rp = (float)Math.toRadians(pitch + roll);
        Vec3 direction = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        direction = direction.normalize().add(random.triangle(0, 0.0172275 * divergence), random.triangle(0, 0.0172275 * divergence), random.triangle(0, 0.0172275 * divergence)).scale(speed);
        setDeltaMovement(direction.add(thrower.getDeltaMovement()));
    }

    @Override public void tick() {
        super.tick();
        // Rigid-body state is authoritative on the server. Running this solver on clients as
        // well caused tiny divergent movements followed by visible server correction snaps.
        if (level().isClientSide()) {
            Quaternionf target = getDiceRotation();
            if (!clientRotationInitialized) {
                clientPreviousRotation.set(target);
                clientRenderRotation.set(target);
                clientRotationInitialized = true;
            } else {
                clientPreviousRotation.set(clientRenderRotation);
                // Smooth network quantization while still converging quickly after impacts.
                clientRenderRotation.slerp(target, 0.82f).normalize();
            }
            return;
        }
        if (playerContactCooldown > 0) playerContactCooldown--;
        if (diceCollisionGraceTicks > 0) diceCollisionGraceTicks--;
        resolveLivingEntityPenetration();
        handlePlayerContacts();
        if (isPlaced()) {
            setDeltaMovement(Vec3.ZERO);
            tickPlacedReroll();
            if (placedRerollTicks == 0 && placedNeedsSeating) seatPlacedShapeOnSurface();
            return;
        }
        if (sleeping) {
            boolean displaced = distanceToSqr(sleepX, sleepY, sleepZ) > 1.0E-8;
            boolean pushed = getDeltaMovement().lengthSqr() > 1.0E-8;
            if (!displaced && !pushed) {
                setDeltaMovement(Vec3.ZERO);
                setOnGround(true);
                return;
            }
            sleeping = false;
            settled = false;
            stillTicks = 0;
        }
        Vec3 prePhysicsCenter = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        simulateRigidCube();
        resolveDiceContacts(prePhysicsCenter);
        applyTetrahedronSupportTorque();
        SupportState support = groundSupport();
        if (onGround() && !support.centerSupported && support.outward.lengthSqr() > 1.0E-8) {
            Vec3 outward = support.outward.normalize();
            // Gravity acting around the remaining supported edge tips the center of mass
            // toward the unsupported side. This is continuous torque, not an orientation snap.
            angularVelocity.add((float)outward.z * 0.16f, 0f, (float)-outward.x * 0.16f);
            setDeltaMovement(getDeltaMovement().add(outward.x * 0.004, 0, outward.z * 0.004));
            sleeping = false;
            settled = false;
        }
        Vec3 velocity = getDeltaMovement();
        // A resting contact continually receives a tiny gravity impulse, so an almost-zero
        // velocity test never reliably sleeps. Recognize an already-flat face geometrically
        // and treat the remaining low-energy motion as solver noise (without rotating it).
        boolean quiet = onGround()
                && support.centerSupported
                && faceAlignment(getDiceRotation()) > 0.9995f
                && velocity.lengthSqr() < 0.0064
                && angularVelocity.lengthSquared() < 0.16f;
        if (!quiet) { stillTicks = 0; settled = false; }
        else if (++stillTicks >= 1 && !settled && level() instanceof ServerLevel server) settle(server);
    }

    private void simulateRigidCube() {
        Vec3 center = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        Vec3 linear = getDeltaMovement();
        Quaternionf rotation = getDiceRotation();
        boolean grounded = false;
        float dt = 1f / PHYSICS_STEPS;

        for (int stepIndex = 0; stepIndex < PHYSICS_STEPS; stepIndex++) {
            if (!isNoGravity()) linear = linear.add(0, -0.055 * dt, 0);
            if (isInWater()) linear = linear.scale(1.0 - 0.14 * dt).add(0, 0.022 * dt, 0);
            center = center.add(linear.scale(dt));

            float spin = angularVelocity.length();
            if (spin > 1.0E-5f) {
                Quaternionf increment = new Quaternionf().fromAxisAngleRad(new Vector3f(angularVelocity).div(spin), spin * dt);
                rotation = increment.mul(rotation, new Quaternionf()).normalize();
            }

            ContactResult contacts = resolveBlockContacts(center, linear, rotation);
            center = contacts.center;
            linear = contacts.velocity;
            // Only the final substep determines whether the body ends this tick grounded.
            // Accumulating an earlier point contact let a rotating d4 sleep after it had
            // already lifted its newly-downward face away from the surface.
            grounded = contacts.grounded;
        }

        if (grounded && linear.y > 0.08) grounded = false;
        if (grounded) {
            double horizontalSpeed = Math.sqrt(linear.x * linear.x + linear.z * linear.z);
            if (horizontalSpeed > 1.0E-7) {
                double reduced = Math.max(0.0, horizontalSpeed - 0.012) * 0.82;
                double scale = reduced / horizontalSpeed;
                linear = new Vec3(linear.x * scale, Math.abs(linear.y) < 0.012 ? 0.0 : linear.y * 0.72, linear.z * scale);
            } else {
                linear = new Vec3(0.0, Math.abs(linear.y) < 0.012 ? 0.0 : linear.y * 0.72, 0.0);
            }
            // Preserve enough angular response for gravity/contact torque to finish tipping
            // an edge-supported cube onto a face. The rigid body sleeps as soon as truly flat.
            angularVelocity.mul(0.88f);
        } else {
            linear = linear.scale(0.985);
            angularVelocity.mul(0.992f);
        }
        if (grounded && linear.lengthSqr() < 0.00002 && angularVelocity.lengthSquared() < 0.002f) {
            linear = Vec3.ZERO;
            angularVelocity.zero();
        }
        setOnGround(grounded);
        setPos(center.x, center.y - HALF_SIZE, center.z);
        setDeltaMovement(linear);
        setDiceRotation(rotation);
    }

    private ContactResult resolveBlockContacts(Vec3 center, Vec3 velocity, Quaternionf rotation) {
        AABB broadphase = new AABB(center.x - 0.44, center.y - 0.44, center.z - 0.44,
                center.x + 0.44, center.y + 0.44, center.z + 0.44);
        boolean grounded = false;
        for (VoxelShape shape : level().getBlockCollisions(this, broadphase)) {
            for (AABB box : shape.toAabbs()) {
                List<Vec3> corners = worldCorners(center, rotation);
                double lowest = Double.POSITIVE_INFINITY;
                for (Vec3 point : corners) {
                    if (point.x >= box.minX - 1.0E-5 && point.x <= box.maxX + 1.0E-5
                            && point.z >= box.minZ - 1.0E-5 && point.z <= box.maxZ + 1.0E-5) {
                        lowest = Math.min(lowest, point.y);
                    }
                }

                // A cube approaching a block from above gets one shared floor manifold.
                // Correcting every corner independently was able to choose a side exit and ratchet downward.
                if (center.y >= box.maxY - HALF_SIZE - 0.05 && lowest < box.maxY) {
                    double correction = box.maxY - lowest + 2.0E-5;
                    center = center.add(0, correction, 0);
                    grounded = true;
                    Vec3 normal = new Vec3(0, 1, 0);
                    int contactCount = 0;
                    for (Vec3 point : corners) if (point.y <= lowest + 0.012) contactCount++;
                    float share = 1f / Math.max(1, contactCount);
                    for (Vec3 point : corners) {
                        if (point.y > lowest + 0.012) continue;
                        Vec3 correctedPoint = point.add(0, correction, 0);
                        velocity = applyContactImpulse(center, correctedPoint, velocity, normal, share);
                    }
                    continue;
                }

                for (Vector3f local : localCorners()) {
                    Vector3f rotated = rotation.transform(new Vector3f(local));
                    Vec3 point = center.add(rotated.x, rotated.y, rotated.z);
                    if (!inside(point, box)) continue;
                    CollisionFace face = nearestFace(point, box);
                    center = center.add(face.normal.scale(face.depth + 1.0E-5));
                    point = point.add(face.normal.scale(face.depth + 1.0E-5));
                    grounded |= face.normal.y > 0.65;

                    velocity = applyContactImpulse(center, point, velocity, face.normal, 1f);
                }

                // Corner containment alone misses edge/face intersections on inset and
                // multipart shapes (chests, fences, trapdoors, and similar cutout blocks).
                // This fallback represents the six-sided die's oriented cube faces. Applying
                // it to every polyhedron creates an invisible 0.5-block cube around the die:
                // tetrahedra float above their real resting face, while rounded d10/d100
                // shapes receive persistent phantom floor contacts and never go to sleep.
                ObbContact remaining = getSides() == 6 ? cubeAabbContact(center, rotation, box) : null;
                if (remaining != null && remaining.penetration > 1.0E-5) {
                    center = center.add(remaining.normal.scale(remaining.penetration + 1.0E-5));
                    Vec3 contactPoint = center.subtract(remaining.normal.scale(HALF_SIZE));
                    velocity = applyContactImpulse(center, contactPoint, velocity, remaining.normal, 1f);
                    grounded |= remaining.normal.y > 0.65;
                }
            }
        }
        return new ContactResult(center, velocity, grounded);
    }

    private Vec3 applyContactImpulse(Vec3 center, Vec3 point, Vec3 velocity, Vec3 contactNormal, float impulseShare) {
        Vector3f r = new Vector3f((float)(point.x - center.x), (float)(point.y - center.y), (float)(point.z - center.z));
        Vector3f contactVelocity = new Vector3f(angularVelocity).cross(r).add((float)velocity.x, (float)velocity.y, (float)velocity.z);
        Vector3f normal = new Vector3f((float)contactNormal.x, (float)contactNormal.y, (float)contactNormal.z);
        float normalSpeed = contactVelocity.dot(normal);
        if (normalSpeed >= 0) return velocity;

        Vector3f rxn = new Vector3f(r).cross(normal);
        float denominator = 1f + INV_INERTIA * rxn.lengthSquared();
        SurfaceResponse surface = surfaceResponse(point, contactNormal);
        float restitution = normalSpeed < -0.10f ? surface.restitution : 0f;
        float impulseMagnitude = -(1.0f + restitution) * normalSpeed / denominator * impulseShare;
        Vector3f impulse = new Vector3f(normal).mul(impulseMagnitude);
        velocity = velocity.add(impulse.x, impulse.y, impulse.z);
        angularVelocity.add(new Vector3f(r).cross(impulse).mul(INV_INERTIA));

        Vector3f after = new Vector3f(angularVelocity).cross(r).add((float)velocity.x, (float)velocity.y, (float)velocity.z);
        Vector3f tangent = after.sub(new Vector3f(normal).mul(after.dot(normal)));
        if (tangent.lengthSquared() > 1.0E-7f) {
            tangent.normalize();
            // impulseMagnitude has already been distributed across the manifold contacts.
            // Applying impulseShare here again made four-corner floor friction about 16x too weak.
            float frictionMagnitude = Math.min(impulseMagnitude * surface.friction, Math.abs(after.dot(tangent)) / denominator);
            Vector3f friction = tangent.mul(-frictionMagnitude);
            velocity = velocity.add(friction.x, friction.y, friction.z);
            angularVelocity.add(new Vector3f(r).cross(friction).mul(INV_INERTIA));
        }
        return velocity;
    }

    private SurfaceResponse surfaceResponse(Vec3 contactPoint, Vec3 outwardNormal) {
        Vec3 insideSurface = contactPoint.subtract(outwardNormal.scale(0.035));
        var state = level().getBlockState(BlockPos.containing(insideSurface));
        if (state.is(Blocks.SLIME_BLOCK)) return new SurfaceResponse(0.88f, 0.16f);
        if (state.is(Blocks.HONEY_BLOCK)) return new SurfaceResponse(0.0f, 0.96f);
        return new SurfaceResponse(0.30f, 0.68f);
    }

    private record SurfaceResponse(float restitution, float friction) {}

    private List<Vec3> worldCorners(Vec3 center, Quaternionf rotation) {
        List<Vec3> result = new ArrayList<>(8);
        for (Vector3f local : localCorners()) {
            Vector3f p = rotation.transform(new Vector3f(local));
            result.add(center.add(p.x, p.y, p.z));
        }
        return result;
    }

    private Vector3f[] localCorners() {
        DicePolyhedra.Shape polyhedron = DicePolyhedra.get(getSides());
        if (polyhedron != null) return polyhedron.vertices();
        if (getSides() == 4) return new Vector3f[] {
                new Vector3f( HALF_SIZE, HALF_SIZE, HALF_SIZE), new Vector3f( HALF_SIZE,-HALF_SIZE,-HALF_SIZE),
                new Vector3f(-HALF_SIZE, HALF_SIZE,-HALF_SIZE), new Vector3f(-HALF_SIZE,-HALF_SIZE, HALF_SIZE)
        };
        if (getSides() == 8) return new Vector3f[] {
                new Vector3f(HALF_SIZE * (float)Math.sqrt(3),0,0), new Vector3f(-HALF_SIZE * (float)Math.sqrt(3),0,0),
                new Vector3f(0,HALF_SIZE * (float)Math.sqrt(3),0), new Vector3f(0,-HALF_SIZE * (float)Math.sqrt(3),0),
                new Vector3f(0,0,HALF_SIZE * (float)Math.sqrt(3)), new Vector3f(0,0,-HALF_SIZE * (float)Math.sqrt(3))
        };
        return new Vector3f[] {
                new Vector3f(-HALF_SIZE,-HALF_SIZE,-HALF_SIZE), new Vector3f(HALF_SIZE,-HALF_SIZE,-HALF_SIZE),
                new Vector3f(-HALF_SIZE,-HALF_SIZE, HALF_SIZE), new Vector3f(HALF_SIZE,-HALF_SIZE, HALF_SIZE),
                new Vector3f(-HALF_SIZE, HALF_SIZE,-HALF_SIZE), new Vector3f(HALF_SIZE, HALF_SIZE,-HALF_SIZE),
                new Vector3f(-HALF_SIZE, HALF_SIZE, HALF_SIZE), new Vector3f(HALF_SIZE, HALF_SIZE, HALF_SIZE)
        };
    }

    private static boolean inside(Vec3 p, AABB b) {
        return p.x > b.minX && p.x < b.maxX && p.y > b.minY && p.y < b.maxY && p.z > b.minZ && p.z < b.maxZ;
    }

    private static CollisionFace nearestFace(Vec3 p, AABB b) {
        double[] depths = {p.x-b.minX, b.maxX-p.x, p.y-b.minY, b.maxY-p.y, p.z-b.minZ, b.maxZ-p.z};
        Vec3[] normals = {new Vec3(-1,0,0),new Vec3(1,0,0),new Vec3(0,-1,0),new Vec3(0,1,0),new Vec3(0,0,-1),new Vec3(0,0,1)};
        int best = 0;
        for (int i = 1; i < depths.length; i++) if (depths[i] < depths[best]) best = i;
        return new CollisionFace(normals[best], depths[best]);
    }

    private record ContactResult(Vec3 center, Vec3 velocity, boolean grounded) {}
    private record CollisionFace(Vec3 normal, double depth) {}

    private void settle(ServerLevel server) {
        settled = true;
        sleeping = true;
        setValue(topFace(getDiceRotation()));
        seatRestingShapeOnSurface();
        setDeltaMovement(Vec3.ZERO);
        angularVelocity.zero();
        sleepX = getX(); sleepY = getY(); sleepZ = getZ();
        if (entityData.get(ANNOUNCED)) return;
        entityData.set(ANNOUNCED, true);
        Component message = Component.literal("🎲 ").append(roller.copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" rolled a d" + getSides() + " → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(getValue())).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer player : server.getPlayers(p -> p.distanceToSqr(this) <= 2500)) player.sendSystemMessage(message, false);
    }

    private void seatRestingShapeOnSurface() {
        Vec3 center = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        double lowest = worldCorners(center, getDiceRotation()).stream().mapToDouble(v -> v.y).min().orElse(getY());
        AABB search = new AABB(center.x - 0.35, lowest - 0.30, center.z - 0.35,
                center.x + 0.35, lowest + 0.02, center.z + 0.35);
        double supportTop = -Double.MAX_VALUE;
        for (VoxelShape shape : level().getBlockCollisions(this, search)) for (AABB box : shape.toAabbs()) {
            if (box.maxY <= lowest + 0.025) supportTop = Math.max(supportTop, box.maxY);
        }
        if (supportTop > -Double.MAX_VALUE) setPos(getX(), getY() + supportTop - lowest, getZ());
    }

    private void seatPlacedShapeOnSurface() {
        Vec3 center = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        AABB search = new AABB(center.x - 0.48, center.y - 0.75, center.z - 0.48,
                center.x + 0.48, center.y + 0.08, center.z + 0.48);
        double supportTop = -Double.MAX_VALUE;
        for (VoxelShape shape : level().getBlockCollisions(this, search)) for (AABB box : shape.toAabbs()) {
            if (box.maxY <= center.y + 0.06) supportTop = Math.max(supportTop, box.maxY);
        }
        if (supportTop == -Double.MAX_VALUE) return;
        Quaternionf rotation = getDiceRotation();
        float lowestOffset = Float.MAX_VALUE;
        for (Vector3f vertex : localCorners()) {
            lowestOffset = Math.min(lowestOffset, rotation.transform(new Vector3f(vertex)).y);
        }
        setPos(getX(), supportTop - HALF_SIZE - lowestOffset + 1.0E-3, getZ());
        placedNeedsSeating = false;
    }

    private void handlePlayerContacts() {
        if (playerContactCooldown > 0 || isPlaced()) return;
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.10), p -> !p.isSpectator())) {
            Vec3 travel = player.getDeltaMovement();
            Vec3 horizontal = new Vec3(travel.x, 0, travel.z);
            if (horizontal.lengthSqr() < 1.0E-5) {
                horizontal = new Vec3(getX() - player.getX(), 0, getZ() - player.getZ());
            }
            if (horizontal.lengthSqr() < 1.0E-6) continue;
            Vec3 direction = horizontal.normalize();
            double impulse;
            float spin;
            if (player.isShiftKeyDown()) {
                impulse = 0.016;
                spin = 0.45f;
            } else if (player.isSprinting()) {
                impulse = 0.14;
                spin = 5.5f;
            } else {
                impulse = 0.055;
                spin = 2.2f;
            }
            sleeping = false; settled = false; stillTicks = 0;
            Vec3 pushed = direction.scale(impulse);
            setDeltaMovement(pushed.x, Math.max(getDeltaMovement().y, 0.015), pushed.z);
            angularVelocity.set((float)direction.z * spin, angularVelocity.y * 0.25f, (float)-direction.x * spin);
            playerContactCooldown = player.isShiftKeyDown() ? 8 : 5;
            break;
        }
    }

    private void resolveLivingEntityPenetration() {
        if (isPlaced()) return;
        for (LivingEntity other : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.12), e -> e.isAlive())) {
            AABB bounds = other.getBoundingBox();
            double minX = bounds.minX - HALF_SIZE - 0.002;
            double maxX = bounds.maxX + HALF_SIZE + 0.002;
            double minZ = bounds.minZ - HALF_SIZE - 0.002;
            double maxZ = bounds.maxZ + HALF_SIZE + 0.002;
            if (getX() <= minX || getX() >= maxX || getZ() <= minZ || getZ() >= maxZ) continue;
            if (getY() + HALF_SIZE * 2 <= bounds.minY || getY() >= bounds.maxY) continue;

            double toMinX = getX() - minX;
            double toMaxX = maxX - getX();
            double toMinZ = getZ() - minZ;
            double toMaxZ = maxZ - getZ();
            double smallest = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
            double newX = getX();
            double newZ = getZ();
            Vec3 outward;
            if (smallest == toMinX) { newX = minX; outward = new Vec3(-1, 0, 0); }
            else if (smallest == toMaxX) { newX = maxX; outward = new Vec3(1, 0, 0); }
            else if (smallest == toMinZ) { newZ = minZ; outward = new Vec3(0, 0, -1); }
            else { newZ = maxZ; outward = new Vec3(0, 0, 1); }

            setPos(newX, getY(), newZ);
            sleeping = false; settled = false; stillTicks = 0;
            Vec3 inherited = other.getDeltaMovement();
            double transfer = other instanceof Player player && player.isSprinting() ? 0.12 : 0.035;
            setDeltaMovement(getDeltaMovement().add(
                    outward.x * transfer + inherited.x * 0.35,
                    Math.max(0.01, inherited.y * 0.2),
                    outward.z * transfer + inherited.z * 0.35));
            angularVelocity.add((float)outward.z * 1.4f, 0, (float)-outward.x * 1.4f);
        }
    }

    private int topFace(Quaternionf rotation) {
        Vector3f[] normals = localFaceNormals();
        /*Vector3f[] normals = getSides() == 4 ? new Vector3f[] {
                new Vector3f(-1,-1,-1).normalize(), new Vector3f(-1,1,1).normalize(),
                new Vector3f(1,-1,1).normalize(), new Vector3f(1,1,-1).normalize()
        } : getSides() == 8 ? octahedronNormals() : new Vector3f[] { new Vector3f(0,1,0), new Vector3f(0,0,1), new Vector3f(1,0,0),
                new Vector3f(-1,0,0), new Vector3f(0,0,-1), new Vector3f(0,-1,0) };*/
        int best = 1; float highest = -Float.MAX_VALUE;
        for (int i = 0; i < normals.length; i++) {
            float y = rotation.transform(normals[i]).y * (getSides() == 4 ? -1f : 1f);
            if (y > highest) { highest = y; best = i + 1; }
        }
        return best;
    }

    private float faceAlignment(Quaternionf rotation) {
        Vector3f[] normals = localFaceNormals();
        /*Vector3f[] normals = getSides() == 4 ? new Vector3f[] {
                new Vector3f(-1,-1,-1).normalize(), new Vector3f(-1,1,1).normalize(),
                new Vector3f(1,-1,1).normalize(), new Vector3f(1,1,-1).normalize()
        } : getSides() == 8 ? octahedronNormals() : new Vector3f[] { new Vector3f(0,1,0), new Vector3f(0,-1,0), new Vector3f(1,0,0),
                new Vector3f(-1,0,0), new Vector3f(0,0,1), new Vector3f(0,0,-1) };*/
        float best = 0f;
        for (Vector3f normal : normals) {
            float alignment = -rotation.transform(normal).y;
            best = Math.max(best, alignment);
        }
        return best;
    }

    private SupportState groundSupport() {
        Vec3 center = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        List<Vec3> corners = worldCorners(center, getDiceRotation());
        double lowest = corners.stream().mapToDouble(p -> p.y).min().orElse(getY());
        AABB search = new AABB(center.x - 0.45, lowest - 0.03, center.z - 0.45,
                center.x + 0.45, lowest + 0.03, center.z + 0.45);
        Vec3 nearestOutward = Vec3.ZERO;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (VoxelShape shape : level().getBlockCollisions(this, search)) {
            for (AABB box : shape.toAabbs()) {
                if (Math.abs(box.maxY - lowest) > 0.008) continue;
                if (center.x >= box.minX && center.x <= box.maxX && center.z >= box.minZ && center.z <= box.maxZ) {
                    return new SupportState(true, Vec3.ZERO);
                }
                double supportX = Math.clamp(center.x, box.minX, box.maxX);
                double supportZ = Math.clamp(center.z, box.minZ, box.maxZ);
                Vec3 outward = new Vec3(center.x - supportX, 0, center.z - supportZ);
                if (outward.lengthSqr() < nearestDistanceSq) {
                    nearestDistanceSq = outward.lengthSqr();
                    nearestOutward = outward;
                }
            }
        }
        return new SupportState(false, nearestOutward);
    }

    private record SupportState(boolean centerSupported, Vec3 outward) {}

    private void applyTetrahedronSupportTorque() {
        if (getSides() != 4 || !onGround() || faceAlignment(getDiceRotation()) > 0.9995f) return;
        Vec3 center = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        List<Vec3> vertices = worldCorners(center, getDiceRotation());
        double lowest = vertices.stream().mapToDouble(v -> v.y).min().orElse(center.y);
        double supportX = 0, supportZ = 0;
        int contacts = 0;
        for (Vec3 vertex : vertices) {
            if (vertex.y <= lowest + 0.018) {
                supportX += vertex.x; supportZ += vertex.z; contacts++;
            }
        }
        if (contacts >= 3) return;
        supportX /= Math.max(1, contacts); supportZ /= Math.max(1, contacts);
        Vec3 tipping = new Vec3(center.x - supportX, 0, center.z - supportZ);
        if (tipping.lengthSqr() < 1.0E-7) {
            float sign = (getId() & 1) == 0 ? 1f : -1f;
            tipping = new Vec3(sign, 0, 0.37).normalize().scale(0.01);
        }
        Vec3 direction = tipping.normalize();
        angularVelocity.add((float)direction.z * 0.22f, 0, (float)-direction.x * 0.22f);
        setDeltaMovement(getDeltaMovement().add(direction.x * 0.0025, 0, direction.z * 0.0025));
        sleeping = false; settled = false; stillTicks = 0;
    }

    private void resolveDiceContacts(Vec3 startCenter) {
        if (isPlaced() || diceCollisionGraceTicks > 0) return;
        Vec3 currentCenter = new Vec3(getX(), getY() + HALF_SIZE, getZ());
        AABB sweptBounds = new AABB(
                Math.min(startCenter.x, currentCenter.x), Math.min(startCenter.y, currentCenter.y), Math.min(startCenter.z, currentCenter.z),
                Math.max(startCenter.x, currentCenter.x), Math.max(startCenter.y, currentCenter.y), Math.max(startCenter.z, currentCenter.z)
        ).inflate(0.55);
        for (DiceEntity other : level().getEntitiesOfClass(DiceEntity.class, sweptBounds,
                die -> die != this && !die.isPlaced() && die.diceCollisionGraceTicks <= 0
                        && (die.sleeping || die.getId() > getId()))) {
            Vec3 endCenter = new Vec3(getX(), getY() + HALF_SIZE, getZ());
            Vec3 collisionCenter = endCenter;
            ObbContact contact = cubeContact(other, endCenter);
            if (contact == null && startCenter.distanceToSqr(endCenter) > 1.0E-8) {
                // Continuous collision guard: test the path through the tick so a fast die
                // cannot pass from one side of a sleeping die to the other between updates.
                for (int sample = 1; sample <= 8; sample++) {
                    double t = sample / 8.0;
                    Vec3 candidate = startCenter.lerp(endCenter, t);
                    contact = cubeContact(other, candidate);
                    if (contact != null) {
                        collisionCenter = candidate;
                        setPos(candidate.x, candidate.y - HALF_SIZE, candidate.z);
                        break;
                    }
                }
            }
            if (contact == null) continue;
            Vec3 normal = contact.normal;
            Vec3 thisCenter = collisionCenter;
            Vec3 otherCenter = new Vec3(other.getX(), other.getY() + HALF_SIZE, other.getZ());
            Vec3 correction = normal.scale(contact.penetration * 0.5 + 1.0E-4);
            setPos(thisCenter.x + correction.x, thisCenter.y + correction.y - HALF_SIZE, thisCenter.z + correction.z);
            other.setPos(otherCenter.x - correction.x, otherCenter.y - correction.y - HALF_SIZE, otherCenter.z - correction.z);

            Vec3 relative = getDeltaMovement().subtract(other.getDeltaMovement());
            double closingSpeed = relative.dot(normal);
            if (closingSpeed < 0) {
                double magnitude = -(1.0 + 0.34) * closingSpeed * 0.5;
                Vec3 impulse = normal.scale(magnitude);
                setDeltaMovement(getDeltaMovement().add(impulse));
                other.setDeltaMovement(other.getDeltaMovement().subtract(impulse));

                Vec3 centers = thisCenter.subtract(otherCenter);
                Vec3 lever = centers.subtract(normal.scale(centers.dot(normal))).scale(0.5);
                Vector3f torque = new Vector3f((float)lever.x, (float)lever.y, (float)lever.z)
                        .cross(new Vector3f((float)impulse.x, (float)impulse.y, (float)impulse.z)).mul(INV_INERTIA);
                angularVelocity.add(torque);
                other.angularVelocity.sub(torque);
            }
            sleeping = false; settled = false; stillTicks = 0;
            other.sleeping = false; other.settled = false; other.stillTicks = 0;
        }
    }

    private ObbContact cubeContact(DiceEntity other, Vec3 centerA) {
        Vec3 centerB = new Vec3(other.getX(), other.getY() + HALF_SIZE, other.getZ());
        Vec3 delta = centerA.subtract(centerB);
        Vector3f[] a = shapeNormals(getDiceRotation());
        Vector3f[] b = other.shapeNormals(other.getDiceRotation());
        List<Vector3f> axes = new ArrayList<>(15);
        for (Vector3f axis : a) axes.add(new Vector3f(axis));
        for (Vector3f axis : b) axes.add(new Vector3f(axis));
        for (Vector3f aa : shapeEdges(getDiceRotation())) for (Vector3f bb : other.shapeEdges(other.getDiceRotation())) {
            Vector3f cross = new Vector3f(aa).cross(bb);
            if (cross.lengthSquared() > 1.0E-8f) axes.add(cross.normalize());
        }

        double smallest = Double.POSITIVE_INFINITY;
        Vec3 best = null;
        for (Vector3f axisF : axes) {
            Vec3 axis = new Vec3(axisF.x, axisF.y, axisF.z);
            double radiusA = projectionRadius(axis, getDiceRotation());
            double radiusB = other.projectionRadius(axis, other.getDiceRotation());
            double overlap = radiusA + radiusB - Math.abs(delta.dot(axis));
            if (overlap <= 0) return null;
            if (overlap < smallest) {
                smallest = overlap;
                best = delta.dot(axis) < 0 ? axis.scale(-1) : axis;
            }
        }
        return best == null ? null : new ObbContact(best, smallest);
    }

    private static Vector3f[] cubeAxes(Quaternionf rotation) {
        return new Vector3f[] {
                rotation.transform(new Vector3f(1,0,0)).normalize(),
                rotation.transform(new Vector3f(0,1,0)).normalize(),
                rotation.transform(new Vector3f(0,0,1)).normalize()
        };
    }

    private Vector3f[] shapeNormals(Quaternionf rotation) {
        DicePolyhedra.Shape polyhedron = DicePolyhedra.get(getSides());
        if (polyhedron != null) {
            Vector3f[] result = new Vector3f[polyhedron.normals().length];
            for(int i=0;i<result.length;i++) result[i]=rotation.transform(new Vector3f(polyhedron.normals()[i]));
            return result;
        }
        if (getSides() == 4) return new Vector3f[] {
                rotation.transform(new Vector3f(-1,-1,-1)).normalize(), rotation.transform(new Vector3f(-1,1,1)).normalize(),
                rotation.transform(new Vector3f(1,-1,1)).normalize(), rotation.transform(new Vector3f(1,1,-1)).normalize()
        };
        if (getSides() == 8) {
            Vector3f[] normals = octahedronNormals();
            for (Vector3f normal : normals) rotation.transform(normal);
            return normals;
        }
        return cubeAxes(rotation);
    }

    private Vector3f[] shapeEdges(Quaternionf rotation) {
        DicePolyhedra.Shape polyhedron = DicePolyhedra.get(getSides());
        if (polyhedron != null) {
            Vector3f[] result=new Vector3f[polyhedron.edges().length];
            for(int i=0;i<result.length;i++){int[] e=polyhedron.edges()[i];result[i]=rotation.transform(new Vector3f(polyhedron.vertices()[e[1]]).sub(polyhedron.vertices()[e[0]])).normalize();}
            return result;
        }
        if (getSides() != 4 && getSides() != 8) return cubeAxes(rotation);
        Vector3f[] v = localCorners();
        int[][] pairs = getSides() == 4
                ? new int[][] {{0,1},{0,2},{0,3},{1,2},{1,3},{2,3}}
                : new int[][] {{0,2},{0,3},{0,4},{0,5},{1,2},{1,3},{1,4},{1,5},{2,4},{2,5},{3,4},{3,5}};
        Vector3f[] result = new Vector3f[pairs.length];
        for (int i=0;i<pairs.length;i++) result[i] = rotation.transform(new Vector3f(v[pairs[i][1]]).sub(v[pairs[i][0]])).normalize();
        return result;
    }

    private static Vector3f[] octahedronNormals() {
        return new Vector3f[] {
                new Vector3f(1,1,1).normalize(), new Vector3f(-1,1,1).normalize(),
                new Vector3f(-1,1,-1).normalize(), new Vector3f(1,1,-1).normalize(),
                new Vector3f(-1,-1,1).normalize(), new Vector3f(1,-1,1).normalize(),
                new Vector3f(1,-1,-1).normalize(), new Vector3f(-1,-1,-1).normalize()
        };
    }

    private Vector3f[] localFaceNormals() {
        DicePolyhedra.Shape polyhedron=DicePolyhedra.get(getSides());
        if(polyhedron!=null){Vector3f[] n=new Vector3f[polyhedron.normals().length];for(int i=0;i<n.length;i++)n[i]=new Vector3f(polyhedron.normals()[i]);return n;}
        if(getSides()==4)return new Vector3f[]{new Vector3f(-1,-1,-1).normalize(),new Vector3f(-1,1,1).normalize(),new Vector3f(1,-1,1).normalize(),new Vector3f(1,1,-1).normalize()};
        if(getSides()==8)return octahedronNormals();
        return new Vector3f[]{new Vector3f(0,1,0),new Vector3f(0,0,1),new Vector3f(1,0,0),new Vector3f(-1,0,0),new Vector3f(0,0,-1),new Vector3f(0,-1,0)};
    }

    private double projectionRadius(Vec3 axis, Quaternionf rotation) {
        double radius = 0;
        for (Vector3f local : localCorners()) {
            Vector3f point = rotation.transform(new Vector3f(local));
            radius = Math.max(radius, Math.abs(axis.x * point.x + axis.y * point.y + axis.z * point.z));
        }
        return radius;
    }

    private static double projectionRadius(Vec3 axis, Vector3f[] cubeAxes) {
        return HALF_SIZE * (Math.abs(axis.dot(new Vec3(cubeAxes[0].x, cubeAxes[0].y, cubeAxes[0].z)))
                + Math.abs(axis.dot(new Vec3(cubeAxes[1].x, cubeAxes[1].y, cubeAxes[1].z)))
                + Math.abs(axis.dot(new Vec3(cubeAxes[2].x, cubeAxes[2].y, cubeAxes[2].z))));
    }

    private record ObbContact(Vec3 normal, double penetration) {}

    private ObbContact cubeAabbContact(Vec3 cubeCenter, Quaternionf rotation, AABB box) {
        Vector3f[] cube = shapeNormals(rotation);
        Vector3f[] block = {new Vector3f(1,0,0), new Vector3f(0,1,0), new Vector3f(0,0,1)};
        List<Vector3f> axes = new ArrayList<>(15);
        for (Vector3f axis : cube) axes.add(new Vector3f(axis));
        for (Vector3f axis : block) axes.add(new Vector3f(axis));
        for (Vector3f a : shapeEdges(rotation)) for (Vector3f b : block) {
            Vector3f cross = new Vector3f(a).cross(b);
            if (cross.lengthSquared() > 1.0E-8f) axes.add(cross.normalize());
        }
        Vec3 boxCenter = box.getCenter();
        Vec3 delta = cubeCenter.subtract(boxCenter);
        double hx = box.getXsize() * 0.5, hy = box.getYsize() * 0.5, hz = box.getZsize() * 0.5;
        double smallest = Double.POSITIVE_INFINITY;
        Vec3 best = null;
        for (Vector3f axisF : axes) {
            Vec3 axis = new Vec3(axisF.x, axisF.y, axisF.z);
            double cubeRadius = projectionRadius(axis, rotation);
            double boxRadius = hx * Math.abs(axis.x) + hy * Math.abs(axis.y) + hz * Math.abs(axis.z);
            double overlap = cubeRadius + boxRadius - Math.abs(delta.dot(axis));
            if (overlap <= 0) return null;
            if (overlap < smallest) {
                smallest = overlap;
                best = delta.dot(axis) < 0 ? axis.scale(-1) : axis;
            }
        }
        return best == null ? null : new ObbContact(best, smallest);
    }

    private Quaternionf rotationForTop(int face, float yawDegrees) {
        if (getSides() == 4) {
            Vector3f[] normals = { new Vector3f(-1,-1,-1).normalize(), new Vector3f(-1,1,1).normalize(),
                    new Vector3f(1,-1,1).normalize(), new Vector3f(1,1,-1).normalize() };
            Quaternionf tilt = new Quaternionf().rotationTo(normals[Math.clamp(face, 1, 4) - 1], new Vector3f(0,-1,0));
            return new Quaternionf().rotationY((float)Math.toRadians(yawDegrees)).mul(tilt);
        }
        if (getSides() == 8) {
            Vector3f normal = octahedronNormals()[Math.clamp(face, 1, 8) - 1];
            Quaternionf tilt = new Quaternionf().rotationTo(normal, new Vector3f(0,1,0));
            return new Quaternionf().rotationY((float)Math.toRadians(yawDegrees)).mul(tilt);
        }
        DicePolyhedra.Shape polyhedron=DicePolyhedra.get(getSides());
        if(polyhedron!=null){Vector3f normal=polyhedron.normals()[Math.clamp(face,1,polyhedron.faces().length)-1];Quaternionf tilt=new Quaternionf().rotationTo(normal,new Vector3f(0,1,0));return new Quaternionf().rotationY((float)Math.toRadians(yawDegrees)).mul(tilt);}
        Quaternionf tilt = switch (face) {
            case 2 -> new Quaternionf().rotationX((float)-Math.PI / 2f);
            case 3 -> new Quaternionf().rotationZ((float)Math.PI / 2f);
            case 4 -> new Quaternionf().rotationZ((float)-Math.PI / 2f);
            case 5 -> new Quaternionf().rotationX((float)Math.PI / 2f);
            case 6 -> new Quaternionf().rotationX((float)Math.PI);
            default -> new Quaternionf();
        };
        float textureYawCorrection = switch (face) {
            case 3 -> -90f;
            case 4 -> 90f;
            case 5 -> 180f;
            default -> 0f;
        };
        return new Quaternionf().rotationY((float)Math.toRadians(yawDegrees + textureYawCorrection)).mul(tilt);
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) { pickup(player); return InteractionResult.SUCCESS; }
        if (isPlaced() && !player.isShiftKeyDown()) {
            setValue(getValue() == getSides() ? 1 : getValue() + 1);
            setDiceRotation(rotationForTop(getValue(), getYRot()));
            placedNeedsSeating = true;
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isPlaced() && source.getEntity() instanceof Player player) {
            if (player.isShiftKeyDown()) {
                beginPlacedReroll();
                return true;
            }
            setValue(getValue() == 1 ? getSides() : getValue() - 1);
            setDiceRotation(rotationForTop(getValue(), getYRot()));
            placedNeedsSeating = true;
            return true;
        }
        return false;
    }

    private void beginPlacedReroll() {
        placedRerollTicks = 14;
        placedRerollResult = random.nextInt(getSides()) + 1;
        setDeltaMovement(Vec3.ZERO);
        angularVelocity.set(
                random.nextFloat() * 2.8f - 1.4f,
                random.nextFloat() * 2.4f - 1.2f,
                random.nextFloat() * 2.8f - 1.4f);
        if (angularVelocity.lengthSquared() < 2.0f) angularVelocity.add(2.1f, 0.8f, -1.7f);
    }

    private void tickPlacedReroll() {
        if (placedRerollTicks <= 0) return;
        Quaternionf rotation = getDiceRotation();
        Quaternionf target = rotationForTop(placedRerollResult, getYRot());
        if (placedRerollTicks > 5) {
            float spin = angularVelocity.length();
            if (spin > 1.0E-5f) {
                Quaternionf step = new Quaternionf().fromAxisAngleRad(
                        new Vector3f(angularVelocity).div(spin), spin * 0.22f);
                rotation = step.mul(rotation, new Quaternionf()).normalize();
            }
            angularVelocity.mul(0.94f);
        } else {
            rotation.slerp(target, 0.48f).normalize();
        }
        placedRerollTicks--;
        if (placedRerollTicks == 0) {
            setValue(placedRerollResult);
            rotation.set(target);
            angularVelocity.zero();
            placedNeedsSeating = true;
        }
        setDiceRotation(rotation);
    }

    private void pickup(Player player) {
        ItemStack stack = getStack().copy();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        discard();
    }

    @Override public boolean isPushable() { return true; }
    @Override public boolean isPickable() { return true; }

    @Override public void move(MoverType type, Vec3 movement) {
        // The rigid-body integrator uses setPos directly, so move() represents an outside
        // displacement: pistons, slime blocks, shulkers, or vanilla entity mechanics.
        if (!level().isClientSide() && type != MoverType.SELF && movement.lengthSqr() > 1.0E-10) {
            wakeFromExternalMotion(movement, type == MoverType.PISTON ? 1.15 : 0.8);
        }
        super.move(type, movement);
    }

    @Override public void push(double x, double y, double z) {
        // During a classified player contact, suppress vanilla's extra generic shove so the
        // crouch/walk/sprint transfer chosen above remains authoritative.
        if (playerContactCooldown > 0) return;
        super.push(x, y, z);
        if (!level().isClientSide()) wakeFromExternalMotion(new Vec3(x, y, z), 1.0);
    }

    private void wakeFromExternalMotion(Vec3 motion, double strength) {
        sleeping = false;
        settled = false;
        stillTicks = 0;
        Vec3 impulse = motion.scale(strength);
        setDeltaMovement(getDeltaMovement().add(impulse));
        float horizontal = (float)Math.sqrt(impulse.x * impulse.x + impulse.z * impulse.z);
        if (horizontal > 1.0E-5f) {
            Vector3f rollingAxis = new Vector3f((float)impulse.z, 0f, (float)-impulse.x).normalize();
            angularVelocity.add(rollingAxis.mul(Math.min(10f, horizontal * 16f + 1.2f)));
        }
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        out.store("stack", ItemStack.CODEC, getStack());
        out.putInt("value", getValue()); out.putBoolean("placed", isPlaced()); out.putBoolean("announced", entityData.get(ANNOUNCED));
        out.putInt("placed_reroll_ticks", placedRerollTicks);
        out.putInt("placed_reroll_result", placedRerollResult);
        out.putInt("sides", getSides());
        out.putString("roller", roller.getString());
        Quaternionf q = getDiceRotation(); out.putFloat("rot_x", q.x); out.putFloat("rot_y", q.y); out.putFloat("rot_z", q.z); out.putFloat("rot_w", q.w);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        in.read("stack", ItemStack.CODEC).ifPresent(this::setStack);
        entityData.set(SIDES, in.getIntOr("sides", sidesForType(getType())));
        setValue(in.getIntOr("value", 1)); entityData.set(PLACED, in.getBooleanOr("placed", false)); entityData.set(ANNOUNCED, in.getBooleanOr("announced", false));
        placedRerollTicks = in.getIntOr("placed_reroll_ticks", 0);
        placedRerollResult = Math.clamp(in.getIntOr("placed_reroll_result", getValue()), 1, getSides());
        roller = Component.literal(in.getStringOr("roller", "Someone"));
        setDiceRotation(new Quaternionf(in.getFloatOr("rot_x", 0f), in.getFloatOr("rot_y", 0f), in.getFloatOr("rot_z", 0f), in.getFloatOr("rot_w", 1f)));
    }
}
