package com.spider.mtgcard.cardstore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.deckbox.DeckboxInsertUtil;
import com.spider.mtgcard.registry.ModBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class CardStoreBlockEntity extends BlockEntity implements ExtendedMenuProvider<CardStoreScreenHandler.OpenData> {

    private static final Component TITLE = Component.literal("Card Store");
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    private @Nullable UUID activeBuyer;
    private final Deque<DeliveryEntry> queue = new ArrayDeque<>();
    private int nextPrintTicks = 0;
    private boolean delivering = false;

    private final Map<UUID, List<CardStoreScreenHandler.CartEntryData>> playerCarts = new HashMap<>();
    private final Random rng = new Random();

    public CardStoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARD_STORE, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public CardStoreScreenHandler.OpenData getScreenOpeningData(ServerPlayer player) {
        UUID playerId = player == null ? null : player.getUUID();
        return new CardStoreScreenHandler.OpenData(this.worldPosition, getSavedCart(playerId));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new CardStoreScreenHandler(syncId, inv, this.worldPosition);
    }

    public boolean isDelivering() {
        return delivering;
    }

    public List<CardStoreScreenHandler.CartEntryData> getSavedCart(@Nullable UUID playerId) {
        if (playerId == null) return List.of();
        return playerCarts.getOrDefault(playerId, List.of());
    }

    public void setSavedCart(@Nullable UUID playerId, List<CardStoreScreenHandler.CartEntryData> entries) {
        if (playerId == null) return;

        ArrayList<CardStoreScreenHandler.CartEntryData> clean = new ArrayList<>();
        if (entries != null) {
            for (var entry : entries) {
                if (entry == null || !entry.isValid()) continue;
                clean.add(entry);
            }
        }

        if (clean.isEmpty()) {
            playerCarts.remove(playerId);
        } else {
            playerCarts.put(playerId, List.copyOf(clean));
        }
        setChanged();
    }

    public void clearSavedCart(@Nullable UUID playerId) {
        if (playerId == null) return;
        if (playerCarts.remove(playerId) != null) {
            setChanged();
        }
    }

    public void beginPrinting(UUID buyer, ItemStack stackTemplate, int count) {
        if (stackTemplate == null || stackTemplate.isEmpty() || count <= 0) return;

        this.activeBuyer = buyer;
        this.queue.addLast(new DeliveryEntry(stackTemplate.copyWithCount(1), count));
        this.delivering = true;
        this.nextPrintTicks = 0;
        setChanged();
    }

    public void flushQueueOutFront() {
        if (level == null || level.isClientSide()) return;

        while (!queue.isEmpty()) {
            DeliveryEntry e = queue.pollFirst();
            if (e == null) continue;
            for (int i = 0; i < e.remaining; i++) {
                ejectOutFront(e.template.copyWithCount(1));
            }
        }
        stopDelivering();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CardStoreBlockEntity be) {
        if (level.isClientSide()) return;
        if (!be.delivering) return;

        if (be.queue.isEmpty()) {
            be.stopDelivering();
            return;
        }

        if (be.nextPrintTicks > 0) {
            be.nextPrintTicks--;
            return;
        }

        DeliveryEntry head = be.queue.peekFirst();
        if (head == null) {
            be.stopDelivering();
            return;
        }

        ItemStack one = head.template.copyWithCount(1);
        be.tryDeliverOne(one);

        head.remaining--;
        if (head.remaining <= 0) {
            be.queue.pollFirst();
        }

        be.nextPrintTicks = 2 + be.rng.nextInt(3);
        be.setChanged();

        if (be.queue.isEmpty()) {
            be.stopDelivering();
        }
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        playerCarts.clear();
        var saved = view.read("PlayerCarts", SavedCart.CODEC.listOf()).orElse(List.of());
        for (var cart : saved) {
            if (cart == null || cart.playerId() == null || cart.entries().isEmpty()) continue;
            playerCarts.put(cart.playerId(), List.copyOf(cart.entries()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        ArrayList<SavedCart> saved = new ArrayList<>();
        for (var entry : playerCarts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            saved.add(new SavedCart(entry.getKey(), entry.getValue()));
        }
        view.store("PlayerCarts", SavedCart.CODEC.listOf(), saved);
    }

    private void stopDelivering() {
        this.delivering = false;
        this.activeBuyer = null;
        this.nextPrintTicks = 0;
        this.queue.clear();
        setChanged();
    }

    private void tryDeliverOne(ItemStack one) {
        if (level == null || one.isEmpty()) return;

        if (tryInsertIntoAdjacentDeckboxes(one)) return;

        ejectOutFront(one);
    }

    private boolean tryInsertIntoAdjacentDeckboxes(ItemStack one) {
        if (level == null) return false;

        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof DeckboxBlockEntity deck) {
                if (DeckboxInsertUtil.tryInsertOneIntoMainGrid(deck, one)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ejectOutFront(ItemStack one) {
        if (level == null || one.isEmpty()) return;

        BlockState state = getBlockState();
        Direction front = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : Direction.NORTH;

        Vec3 base = Vec3.atCenterOf(worldPosition);
        Vec3 spawn = base.add(front.getStepX() * 0.6, 0.1, front.getStepZ() * 0.6);

        ItemEntity ent = new ItemEntity(level, spawn.x, spawn.y, spawn.z, one.copyWithCount(1));
        ent.setDeltaMovement(front.getStepX() * 0.20, 0.08, front.getStepZ() * 0.20);
        level.addFreshEntity(ent);
    }

    private record SavedCart(UUID playerId, List<CardStoreScreenHandler.CartEntryData> entries) {
        static final Codec<SavedCart> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                UUID_CODEC.fieldOf("PlayerId").forGetter(SavedCart::playerId),
                CardStoreScreenHandler.CartEntryData.CODEC.listOf().fieldOf("Entries").forGetter(SavedCart::entries)
        ).apply(inst, SavedCart::new));

        SavedCart {
            entries = entries == null || entries.isEmpty() ? List.of() : List.copyOf(entries);
        }
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
