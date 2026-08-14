package com.spider.mtgcard.display;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.item.CardItem;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.util.StackData;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
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
import net.minecraft.server.level.ServerPlayer;
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
    public static final double ATTACHMENT_STRIP = 0.38D;
    public static final double PICK_HALF_WIDTH = 0.40D;
    public static final double PICK_HALF_HEIGHT = 0.53D;
    private static final double STACK_BOUNDS_HALF_EXTENT = 0.56D;
    private static final double CARD_SIZE = 1.0D;
    private static final double CARD_THICKNESS = 1.0D / 16.0D;
    private static final double FACE_OFFSET = 0.46875D;
    private static final double SURFACE_SEPARATION = 1.0E-4D;
    private static final double SURFACE_MATCH_EPSILON = 1.0E-3D;
    private static final double RAY_EPSILON = 1.0E-6D;

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

    private static final EntityDataAccessor<Long> STACK_KEY_MOST =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<Long> STACK_KEY_LEAST =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<Long> STACK_VERSION =
            SynchedEntityData.defineId(CardDisplayEntity.class, EntityDataSerializers.LONG);

    public CardDisplayEntity(EntityType<? extends CardDisplayEntity> type, Level world) {
        super(type, world);
        ensureStackKey();
    }

    public CardDisplayEntity(Level world, BlockPos attachmentPos, Direction facing) {
        super(ModEntities.CARD_DISPLAY, world, attachmentPos);
        ensureStackKey();
        this.setDirection(facing);
    }

    public CardDisplayEntity(Level world, BlockPos supportPos, Direction facing,
                             double surfaceOffset, double surfaceX, double surfaceY, double surfaceZ) {
        super(ModEntities.CARD_DISPLAY, world, supportPos);
        ensureStackKey();
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
        builder.define(STACK_KEY_MOST, 0L);
        builder.define(STACK_KEY_LEAST, 0L);
        builder.define(STACK_VERSION, 0L);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data.equals(SURFACE_ANCHORED)
                || data.equals(SURFACE_OFFSET)
                || data.equals(SURFACE_X)
                || data.equals(SURFACE_Y)
                || data.equals(SURFACE_Z)
                || data.equals(FLAT_YAW_STEP)
                || data.equals(STACK)) {
            this.recalculateBoundingBox();
        }
    }

    private void ensureStackKey() {
        if (entityData.get(STACK_KEY_MOST) != 0L || entityData.get(STACK_KEY_LEAST) != 0L) {
            return;
        }
        setStackKey(UUID.randomUUID());
    }

    public UUID getStackKey() {
        ensureStackKey();
        return new UUID(entityData.get(STACK_KEY_MOST), entityData.get(STACK_KEY_LEAST));
    }

    private void setStackKey(UUID key) {
        UUID safe = key == null ? UUID.randomUUID() : key;
        entityData.set(STACK_KEY_MOST, safe.getMostSignificantBits());
        entityData.set(STACK_KEY_LEAST, safe.getLeastSignificantBits());
    }

    public boolean hasStackKey(UUID key) {
        return key != null && getStackKey().equals(key);
    }

    public long getStackVersion() {
        return entityData.get(STACK_VERSION);
    }

    private void setStackVersion(long version) {
        entityData.set(STACK_VERSION, Math.max(0L, version));
    }

    private void bumpStackVersion() {
        setStackVersion(getStackVersion() + 1L);
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
        this.recalculateBoundingBox();
    }

    public int getAttachmentCount() {
        return CardDisplayAttachmentData.readAttachments(getStack()).size();
    }

    public List<CardDisplayAttachmentData.Attachment> getCardAttachments() {
        return CardDisplayAttachmentData.readAttachments(getStack());
    }

    public ItemStack getCleanHostStack() {
        return CardDisplayAttachmentData.stripDisplayData(getStack());
    }

    public ItemStack getDisplayCardStack(UUID cardId) {
        if (cardId == null || cardId.equals(getStackKey())) {
            return getCleanHostStack();
        }

        for (CardDisplayAttachmentData.Attachment attachment : getCardAttachments()) {
            if (attachment.id().equals(cardId)) {
                return attachment.stack().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public UUID getDisplayCardId(int displayIndex) {
        if (displayIndex <= 0) {
            return getStackKey();
        }

        List<CardDisplayAttachmentData.Attachment> attachments = getCardAttachments();
        int attachmentIndex = displayIndex - 1;
        if (attachmentIndex < 0 || attachmentIndex >= attachments.size()) {
            return getStackKey();
        }
        return attachments.get(attachmentIndex).id();
    }

    public int getDisplayCardRotStep(int displayIndex) {
        if (displayIndex <= 0) {
            return getRotStep();
        }

        List<CardDisplayAttachmentData.Attachment> attachments = getCardAttachments();
        int attachmentIndex = displayIndex - 1;
        if (attachmentIndex < 0 || attachmentIndex >= attachments.size()) {
            return 0;
        }
        return attachments.get(attachmentIndex).rotStep();
    }

    public boolean attachFromPlayer(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isCardStack(held)) {
            return false;
        }
        if (getStack().isEmpty()) {
            return false;
        }

        List<CardDisplayAttachmentData.Attachment> attachments = new ArrayList<>(getCardAttachments());
        if (attachments.size() >= CardDisplayAttachmentData.MAX_ATTACHMENTS) {
            player.sendSystemMessage(Component.literal("Attachment stack is full (16 cards)."));
            return false;
        }

        ItemStack one = CardDisplayAttachmentData.normalizeCardStack(held);
        if (one.isEmpty()) {
            return false;
        }

        attachments.add(new CardDisplayAttachmentData.Attachment(UUID.randomUUID(), one, 0));
        if (!writeAttachmentList(attachments, true)) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    public boolean cycleDisplayCardRot(UUID cardId) {
        if (cardId == null || cardId.equals(getStackKey())) {
            this.cycleRot();
            bumpStackVersion();
            return true;
        }

        List<CardDisplayAttachmentData.Attachment> attachments = new ArrayList<>(getCardAttachments());
        for (int i = 0; i < attachments.size(); i++) {
            CardDisplayAttachmentData.Attachment attachment = attachments.get(i);
            if (attachment.id().equals(cardId)) {
                attachments.set(i, attachment.withRotStep(attachment.rotStep() ^ 1));
                return writeAttachmentList(attachments, true);
            }
        }
        return false;
    }

    public boolean mutateDisplayCardStack(UUID cardId, java.util.function.Consumer<ItemStack> edit) {
        if (edit == null) {
            return false;
        }

        if (cardId == null || cardId.equals(getStackKey())) {
            ItemStack host = getStack().copy();
            if (host.isEmpty()) {
                return false;
            }
            edit.accept(host);
            setStack(host);
            return true;
        }

        List<CardDisplayAttachmentData.Attachment> attachments = new ArrayList<>(getCardAttachments());
        for (int i = 0; i < attachments.size(); i++) {
            CardDisplayAttachmentData.Attachment attachment = attachments.get(i);
            if (attachment.id().equals(cardId)) {
                ItemStack stack = attachment.stack().copy();
                edit.accept(stack);
                attachments.set(i, attachment.withStack(stack));
                return writeAttachmentList(attachments, false);
            }
        }
        return false;
    }

    public boolean detachAttachment(ServerPlayer player, UUID attachmentId, long expectedVersion, List<UUID> orderBeforeDetach) {
        return detachAttachment(player, attachmentId, expectedVersion, orderBeforeDetach, true);
    }

    private boolean detachAttachment(ServerPlayer player, UUID attachmentId, long expectedVersion,
                                     List<UUID> orderBeforeDetach, boolean reopenScreen) {
        if (player == null || attachmentId == null || attachmentId.equals(getStackKey())) {
            return false;
        }
        if (getStackVersion() != expectedVersion) {
            player.sendSystemMessage(Component.literal("Attachment stack changed; resynced current order."));
            if (reopenScreen) {
                sendAttachmentScreen(player, attachmentId);
            }
            return false;
        }

        List<CardDisplayAttachmentData.Attachment> attachments = new ArrayList<>(getCardAttachments());
        List<CardDisplayAttachmentData.Attachment> ordered = orderBeforeDetach == null || orderBeforeDetach.isEmpty()
                ? attachments
                : orderedAttachmentsOrNull(attachments, orderBeforeDetach);
        if (ordered == null) {
            player.sendSystemMessage(Component.literal("Attachment order was stale; resynced current order."));
            if (reopenScreen) {
                sendAttachmentScreen(player, attachmentId);
            }
            return false;
        }

        int removeIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(attachmentId)) {
                removeIndex = i;
                break;
            }
        }
        if (removeIndex < 0) {
            player.sendSystemMessage(Component.literal("That attachment no longer exists."));
            if (reopenScreen) {
                sendAttachmentScreen(player, getStackKey());
            }
            return false;
        }

        CardDisplayAttachmentData.Attachment removed = ordered.remove(removeIndex);
        UUID nextSelection = getStackKey();
        if (!ordered.isEmpty()) {
            int nextIndex = Math.min(removeIndex, ordered.size() - 1);
            nextSelection = ordered.get(nextIndex).id();
        }

        if (!writeAttachmentList(ordered, true)) {
            return false;
        }
        giveOrDrop(player, removed.stack());
        if (reopenScreen) {
            sendAttachmentScreen(player, nextSelection);
        }
        return true;
    }

    public boolean reorderAttachments(ServerPlayer player, long expectedVersion, List<UUID> orderedIds, UUID selectedCardId, boolean returnToLargeView) {
        if (player == null) {
            return false;
        }
        if (getStackVersion() != expectedVersion) {
            player.sendSystemMessage(Component.literal("Attachment stack changed; resynced current order."));
            if (returnToLargeView) {
                sendLargeView(player, selectedCardId);
            } else {
                sendAttachmentScreen(player, selectedCardId);
            }
            return false;
        }

        List<CardDisplayAttachmentData.Attachment> attachments = new ArrayList<>(getCardAttachments());
        List<CardDisplayAttachmentData.Attachment> ordered = orderedAttachmentsOrNull(attachments, orderedIds);
        if (ordered == null) {
            player.sendSystemMessage(Component.literal("Invalid attachment order; resynced current order."));
            if (returnToLargeView) {
                sendLargeView(player, selectedCardId);
            } else {
                sendAttachmentScreen(player, selectedCardId);
            }
            return false;
        }

        if (!sameAttachmentOrder(attachments, ordered)) {
            if (!writeAttachmentList(ordered, true)) {
                return false;
            }
        }

        if (returnToLargeView) {
            sendLargeView(player, selectedCardId);
        } else {
            sendAttachmentScreen(player, selectedCardId);
        }
        return true;
    }

    private boolean writeAttachmentList(List<CardDisplayAttachmentData.Attachment> attachments, boolean structuralChange) {
        ItemStack host = getStack().copy();
        if (host.isEmpty()) {
            return false;
        }

        CardDisplayAttachmentData.writeAttachments(host, attachments);
        setStack(host);
        if (structuralChange) {
            bumpStackVersion();
        }
        return true;
    }

    private static List<CardDisplayAttachmentData.Attachment> orderedAttachmentsOrNull(
            List<CardDisplayAttachmentData.Attachment> current,
            List<UUID> orderedIds
    ) {
        if (orderedIds == null || orderedIds.size() != current.size()) {
            return null;
        }

        Map<UUID, CardDisplayAttachmentData.Attachment> byId = new HashMap<>();
        for (CardDisplayAttachmentData.Attachment attachment : current) {
            if (byId.put(attachment.id(), attachment) != null) {
                return null;
            }
        }

        ArrayList<CardDisplayAttachmentData.Attachment> ordered = new ArrayList<>(current.size());
        for (UUID id : orderedIds) {
            if (id == null || byId.get(id) == null) {
                return null;
            }
            CardDisplayAttachmentData.Attachment attachment = byId.remove(id);
            if (attachment == null) {
                return null;
            }
            ordered.add(attachment);
        }

        return byId.isEmpty() ? ordered : null;
    }

    private static boolean sameAttachmentOrder(
            List<CardDisplayAttachmentData.Attachment> a,
            List<CardDisplayAttachmentData.Attachment> b
    ) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).id().equals(b.get(i).id())) {
                return false;
            }
        }
        return true;
    }

    private static List<UUID> attachmentIds(List<CardDisplayAttachmentData.Attachment> attachments) {
        ArrayList<UUID> ids = new ArrayList<>(attachments.size());
        for (CardDisplayAttachmentData.Attachment attachment : attachments) {
            ids.add(attachment.id());
        }
        return ids;
    }

    private static boolean isCardStack(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof CardItem || stack.is(ModItemTags.TCG_CARD));
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        ItemStack clean = CardDisplayAttachmentData.normalizeCardStack(stack);
        if (clean.isEmpty()) {
            return;
        }

        ItemStack toInsert = clean.copy();
        if (!player.getInventory().add(toInsert)) {
            this.spawnAtLocation((ServerLevel) this.level(), clean.copy());
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public void sendLargeView(ServerPlayer player, UUID selectedCardId) {
        if (player == null) {
            return;
        }

        UUID selected = normalizeSelectedCardId(selectedCardId);
        ItemStack selectedStack = getDisplayCardStack(selected);
        if (selectedStack.isEmpty()) {
            selected = getStackKey();
            selectedStack = getCleanHostStack();
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new CardDisplayPayloads.OpenDisplayViewS2C(
                        this.getId(),
                        getStackKey(),
                        getStackVersion(),
                        selected,
                        selectedStack,
                        getAttachmentCount()
                )
        );
    }

    public void sendAttachmentScreen(ServerPlayer player, UUID selectedCardId) {
        if (player == null) {
            return;
        }

        UUID selected = normalizeSelectedCardId(selectedCardId);
        List<CardDisplayAttachmentData.Attachment> attachments = getCardAttachments();
        ArrayList<CardDisplayPayloads.AttachmentSyncEntry> entries = new ArrayList<>(attachments.size());
        for (CardDisplayAttachmentData.Attachment attachment : attachments) {
            entries.add(new CardDisplayPayloads.AttachmentSyncEntry(
                    attachment.id(),
                    attachment.stack().copy(),
                    attachment.rotStep()
            ));
        }

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new CardDisplayPayloads.OpenAttachmentsS2C(
                        this.getId(),
                        getStackKey(),
                        getStackVersion(),
                        selected,
                        getCleanHostStack(),
                        entries
                )
        );
    }

    private UUID normalizeSelectedCardId(UUID selectedCardId) {
        if (selectedCardId == null || selectedCardId.equals(getStackKey())) {
            return getStackKey();
        }

        for (CardDisplayAttachmentData.Attachment attachment : getCardAttachments()) {
            if (attachment.id().equals(selectedCardId)) {
                return selectedCardId;
            }
        }
        return getStackKey();
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

    private AABB calculateStackBoundingBox(BlockPos pos, Direction side, int attachmentCount) {
        Vec3 baseCenter = getBasePlaneCenter(pos, side);
        VisualAxes axes = axesFor(side, getFlatYawStep());

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int i = 0; i <= attachmentCount; i++) {
            Vec3 center = baseCenter.add(axes.up().scale(stackLocalYOffset(i, attachmentCount)));
            Vec3[] corners = stackCardCorners(center, axes, STACK_BOUNDS_HALF_EXTENT, STACK_BOUNDS_HALF_EXTENT, CARD_THICKNESS * 0.5D);
            for (Vec3 corner : corners) {
                minX = Math.min(minX, corner.x);
                minY = Math.min(minY, corner.y);
                minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x);
                maxY = Math.max(maxY, corner.y);
                maxZ = Math.max(maxZ, corner.z);
            }
        }

        if (!Double.isFinite(minX)) {
            Vec3 center = Vec3.atCenterOf(pos).relative(side, -FACE_OFFSET);
            return AABB.ofSize(center,
                    sizeForAxis(side.getAxis(), Direction.Axis.X),
                    sizeForAxis(side.getAxis(), Direction.Axis.Y),
                    sizeForAxis(side.getAxis(), Direction.Axis.Z));
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3 getBasePlaneCenter(BlockPos pos, Direction side) {
        if (!isSurfaceAnchored()) {
            return Vec3.atCenterOf(pos).relative(side, -FACE_OFFSET);
        }

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
        return new Vec3(centerX, centerY, centerZ);
    }

    public Vec3 getVisualCardCenter(int displayIndex) {
        int attachmentCount = getAttachmentCount();
        int safeIndex = Math.max(0, Math.min(displayIndex, attachmentCount));
        VisualAxes axes = axesFor(getDirection(), getFlatYawStep());
        return getBasePlaneCenter(this.getPos(), getDirection())
                .add(axes.up().scale(stackLocalYOffset(safeIndex, attachmentCount)));
    }

    public static double stackLocalYOffset(int displayIndex, int attachmentCount) {
        int safeAttachments = Math.max(0, attachmentCount);
        int safeIndex = Math.max(0, Math.min(displayIndex, safeAttachments));
        return (safeAttachments * ATTACHMENT_STRIP * 0.5D) - (safeIndex * ATTACHMENT_STRIP);
    }

    public static VisualAxes axesFor(Direction facing, int flatYawStep) {
        Vec3 normal = directionVector(facing);
        Vec3 up;
        Vec3 right;

        if (facing.getAxis().isHorizontal()) {
            up = new Vec3(0.0D, 1.0D, 0.0D);
            right = up.cross(normal).normalize();
        } else {
            right = new Vec3(1.0D, 0.0D, 0.0D);
            up = facing == Direction.UP ? new Vec3(0.0D, 0.0D, -1.0D) : new Vec3(0.0D, 0.0D, 1.0D);
            double radians = -(((flatYawStep % 4) + 4) % 4) * (Math.PI / 2.0D);
            right = rotateAroundAxis(right, normal, radians).normalize();
            up = rotateAroundAxis(up, normal, radians).normalize();
        }

        return new VisualAxes(right, up, normal);
    }

    private static VisualAxes axesForRotStep(VisualAxes baseAxes, int rotStep) {
        Vec3 right = baseAxes.right();
        Vec3 up = baseAxes.up();
        if ((rotStep & 1) == 1) {
            double radians = -Math.PI / 2.0D;
            right = rotateAroundAxis(right, baseAxes.normal(), radians).normalize();
            up = rotateAroundAxis(up, baseAxes.normal(), radians).normalize();
        }
        return new VisualAxes(right, up, baseAxes.normal());
    }

    private static Vec3[] stackCardCorners(Vec3 center, VisualAxes axes, double halfWidth, double halfHeight, double halfDepth) {
        ArrayList<Vec3> corners = new ArrayList<>(8);
        for (double z : new double[]{-halfDepth, halfDepth}) {
            for (double y : new double[]{-halfHeight, halfHeight}) {
                for (double x : new double[]{-halfWidth, halfWidth}) {
                    corners.add(center
                            .add(axes.right().scale(x))
                            .add(axes.up().scale(y))
                            .add(axes.normal().scale(z)));
                }
            }
        }
        return corners.toArray(Vec3[]::new);
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Vec3 rotateAroundAxis(Vec3 value, Vec3 axis, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        Vec3 n = axis.normalize();
        return value.scale(cos)
                .add(n.cross(value).scale(sin))
                .add(n.scale(n.dot(value) * (1.0D - cos)));
    }

    public record VisualAxes(Vec3 right, Vec3 up, Vec3 normal) {
    }

    public int findSelectedDisplayIndex(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return -1;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 ray = player.getViewVector(1.0F).normalize();
        double maxDistance = Math.max(6.0D, player.entityInteractionRange() + 1.0D);
        VisualAxes baseAxes = axesFor(getDirection(), getFlatYawStep());
        int attachmentCount = getAttachmentCount();

        for (int displayIndex = 0; displayIndex <= attachmentCount; displayIndex++) {
            Vec3 center = getVisualCardCenter(displayIndex);
            double denom = ray.dot(baseAxes.normal());
            if (Math.abs(denom) < RAY_EPSILON) {
                continue;
            }

            double t = center.subtract(eye).dot(baseAxes.normal()) / denom;
            if (t < 0.0D || t > maxDistance) {
                continue;
            }

            Vec3 hit = eye.add(ray.scale(t));
            Vec3 delta = hit.subtract(center);
            VisualAxes cardAxes = axesForRotStep(baseAxes, getDisplayCardRotStep(displayIndex));

            double localX = delta.dot(cardAxes.right());
            double localY = delta.dot(cardAxes.up());
            if (Math.abs(localX) <= PICK_HALF_WIDTH && Math.abs(localY) <= PICK_HALF_HEIGHT) {
                return displayIndex;
            }
        }

        return -1;
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
        ItemStack host = getCleanHostStack();
        if (!host.isEmpty()) {
            this.spawnAtLocation(world, CardDisplayAttachmentData.normalizeCardStack(host));
        }

        for (CardDisplayAttachmentData.Attachment attachment : getCardAttachments()) {
            ItemStack stack = CardDisplayAttachmentData.normalizeCardStack(attachment.stack());
            if (!stack.isEmpty()) {
                this.spawnAtLocation(world, stack);
            }
        }
        setStack(ItemStack.EMPTY);
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            int selected = findSelectedDisplayIndex(player);
            if (selected > 0) {
                UUID attachmentId = getDisplayCardId(selected);
                detachAttachment(player, attachmentId, getStackVersion(), attachmentIds(getCardAttachments()), false);
                return true;
            }
        }

        this.dropAsItem(world, source);
        this.discard();
        return true;
    }

    @Override
    public InteractionResult interact(net.minecraft.world.entity.player.Player player, InteractionHand hand, Vec3 location) {
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;

        int selected = findSelectedDisplayIndex(player);
        if (selected < 0 && getAttachmentCount() == 0) {
            selected = 0;
        }
        if (selected < 0) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResult.CONSUME;
            }

            ItemStack held = player.getItemInHand(hand);
            if (isCardStack(held)) {
                if (selected == 0) {
                    attachFromPlayer(sp, hand);
                } else {
                    sp.sendSystemMessage(Component.literal("Aim at the host card to attach another card."));
                }
                return InteractionResult.CONSUME;
            }

            sendLargeView(sp, getDisplayCardId(selected));
            return InteractionResult.CONSUME;
        }

        this.cycleDisplayCardRot(getDisplayCardId(selected));
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
        UUID stackKey = getStackKey();
        view.putLong("StackKeyMost", stackKey.getMostSignificantBits());
        view.putLong("StackKeyLeast", stackKey.getLeastSignificantBits());
        view.putLong("StackVersion", getStackVersion());
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
        long most = view.getLongOr("StackKeyMost", 0L);
        long least = view.getLongOr("StackKeyLeast", 0L);
        if (most != 0L || least != 0L) {
            setStackKey(new UUID(most, least));
        } else {
            setStackKey(UUID.randomUUID());
        }
        setStackVersion(view.getLongOr("StackVersion", 0L));
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
