// src/main/java/com/spider/mtgcard/cardstore/CardStoreBlockEntity.java
package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.deckbox.DeckboxInsertUtil;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.UUID;

public class CardStoreBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    private static final Text TITLE = Text.literal("Card Store");

    // Printing/delivery state
    private @Nullable UUID activeBuyer;
    private final Deque<DeliveryEntry> queue = new ArrayDeque<>();
    private int nextPrintTicks = 0;
    private boolean delivering = false;

    private final Random rng = new Random();

    public CardStoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_STORE, pos, state);
    }

    // -------- Screen stuff --------

    @Override
    public Text getDisplayName() {
        return TITLE;
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return this.pos;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new CardStoreScreenHandler(syncId, inv, this.pos);
    }

    // -------- API used by CardStorePurchaseService --------

    public boolean isDelivering() {
        return delivering;
    }

    public void beginPrinting(UUID buyer, ItemStack stackTemplate, int count) {
        if (stackTemplate == null || stackTemplate.isEmpty() || count <= 0) return;

        this.activeBuyer = buyer;
        this.queue.addLast(new DeliveryEntry(stackTemplate.copyWithCount(1), count));
        this.delivering = true;
        this.nextPrintTicks = 0;
        markDirty();
    }

    // Called when broken mid-print: flush remaining out front
    public void flushQueueOutFront() {
        if (world == null || world.isClient()) return;

        while (!queue.isEmpty()) {
            DeliveryEntry e = queue.pollFirst();
            if (e == null) continue;
            for (int i = 0; i < e.remaining; i++) {
                ejectOutFront(e.template.copyWithCount(1));
            }
        }
        stopDelivering();
    }

    // -------- Tick loop (called by CardStoreBlock.getTicker) --------

    public static void tick(World world, BlockPos pos, BlockState state, CardStoreBlockEntity be) {
        if (world.isClient()) return;
        if (!be.delivering) return;

        if (be.queue.isEmpty()) {
            be.stopDelivering();
            return;
        }

        if (be.nextPrintTicks > 0) {
            be.nextPrintTicks--;
            return;
        }

        // Print exactly 1 card now
        DeliveryEntry head = be.queue.peekFirst();
        if (head == null) {
            be.stopDelivering();
            return;
        }

        ItemStack one = head.template.copyWithCount(1);
        be.tryDeliverOne(one);

        // decrement remaining
        head.remaining--;
        if (head.remaining <= 0) be.queue.pollFirst();

        be.nextPrintTicks = 2 + be.rng.nextInt(3); // 2..4 ticks
        be.markDirty();

        if (be.queue.isEmpty()) be.stopDelivering();
    }

    private void stopDelivering() {
        this.delivering = false;
        this.activeBuyer = null;
        this.nextPrintTicks = 0;
        this.queue.clear();
        markDirty();
    }

    // -------- Delivery logic --------

    private void tryDeliverOne(ItemStack one) {
        if (world == null || one.isEmpty()) return;

        // 1) Adjacent deckboxes (slots 0–98)
        if (tryInsertIntoAdjacentDeckboxes(one)) return;

        // 2) Otherwise: eject into world out front
        ejectOutFront(one);
    }

    private boolean tryInsertIntoAdjacentDeckboxes(ItemStack one) {
        if (world == null) return false;

        for (Direction d : Direction.values()) {
            BlockPos p = pos.offset(d);
            BlockEntity be = world.getBlockEntity(p);
            if (be instanceof DeckboxBlockEntity deck) {
                if (DeckboxInsertUtil.tryInsertOneIntoMainGrid(deck, one)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ejectOutFront(ItemStack one) {
        if (world == null || one.isEmpty()) return;

        Direction front = getCachedState().contains(Properties.HORIZONTAL_FACING)
                ? getCachedState().get(Properties.HORIZONTAL_FACING)
                : Direction.NORTH;

        Vec3d base = Vec3d.ofCenter(pos);
        Vec3d spawn = base.add(front.getOffsetX() * 0.6, 0.1, front.getOffsetZ() * 0.6);

        ItemEntity ent = new ItemEntity(world, spawn.x, spawn.y, spawn.z, one.copyWithCount(1));
        ent.setVelocity(front.getOffsetX() * 0.20, 0.08, front.getOffsetZ() * 0.20);
        world.spawnEntity(ent);
    }

    private static final class DeliveryEntry {
        final ItemStack template;
        int remaining;

        DeliveryEntry(ItemStack template, int remaining) {
            this.template = template;
            this.remaining = remaining;
        }
    }
}
