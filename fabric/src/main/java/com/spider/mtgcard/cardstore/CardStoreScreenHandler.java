// src/main/java/com/spider/mtgcard/cardstore/CardStoreScreenHandler.java
package com.spider.mtgcard.cardstore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CardStoreScreenHandler extends AbstractContainerMenu {

    public final BlockPos blockPos;
    private final List<CartEntryData> initialCart;

    // Layout constants (must match what your Screen expects visually)
    public static final int MARGIN = 10;
    public static final int INV_BLOCK_H = (3 * 18) + 4 + 18; // 3 rows + gap + hotbar

    public CardStoreScreenHandler(int syncId, Inventory playerInv) {
        this(syncId, playerInv, BlockPos.ZERO, List.of());
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, OpenData openData) {
        this(syncId, playerInv,
                openData == null ? BlockPos.ZERO : openData.blockPos(),
                openData == null ? List.of() : openData.cartEntries());
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, BlockPos blockPos) {
        this(syncId, playerInv, blockPos, List.of());
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, BlockPos blockPos, List<CartEntryData> initialCart) {
        super(ModScreenHandlers.CARD_STORE, syncId);
        this.blockPos = blockPos == null ? BlockPos.ZERO : blockPos;
        this.initialCart = sanitizeCart(initialCart);

        // Put inventory at the bottom of a fixed virtual fullscreen GUI.
        int guiW = 426;
        int guiH = 240;

        int invTopY = guiH - MARGIN - INV_BLOCK_H;
        int invX = MARGIN + 8;

        addPlayerInventory(playerInv, invX, invTopY);
        addHotbar(playerInv, invX, invTopY + (3 * 18) + 4);
    }

    public List<CartEntryData> initialCart() {
        return initialCart;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    private void addPlayerInventory(Inventory inv, int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
    }

    private void addHotbar(Inventory inv, int x, int y) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, x + col * 18, y));
        }
    }

    private static List<CartEntryData> sanitizeCart(List<CartEntryData> cart) {
        if (cart == null || cart.isEmpty()) return List.of();

        ArrayList<CartEntryData> out = new ArrayList<>();
        for (var entry : cart) {
            if (entry == null || !entry.isValid()) continue;
            out.add(entry);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public record CartEntryData(String set, String cn, ItemStack stack, long priceItems, int qty) {
        public static final Codec<CartEntryData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("Set").forGetter(CartEntryData::set),
                Codec.STRING.fieldOf("Cn").forGetter(CartEntryData::cn),
                ItemStack.CODEC.fieldOf("Stack").forGetter(CartEntryData::stack),
                Codec.LONG.fieldOf("PriceItems").forGetter(CartEntryData::priceItems),
                Codec.INT.fieldOf("Qty").forGetter(CartEntryData::qty)
        ).apply(inst, CartEntryData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CartEntryData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, entry) -> {
                            buf.writeUtf(entry.set());
                            buf.writeUtf(entry.cn(), 64);
                            ItemStack.STREAM_CODEC.encode(buf, entry.stack());
                            buf.writeVarLong(entry.priceItems());
                            buf.writeVarInt(entry.qty());
                        },
                        (buf) -> new CartEntryData(
                                buf.readUtf(),
                                buf.readUtf(64),
                                ItemStack.STREAM_CODEC.decode(buf),
                                buf.readVarLong(),
                                buf.readVarInt()
                        )
                );

        public CartEntryData {
            set = set == null ? "" : set.trim();
            cn = cn == null ? "" : cn.trim();
            stack = sanitizeStack(stack);
            priceItems = Math.max(0L, priceItems);
            qty = Math.max(1, qty);
        }

        public boolean isValid() {
            return !set.isBlank() && !cn.isBlank() && !stack.isEmpty() && qty > 0;
        }

        private static ItemStack sanitizeStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            return stack.copyWithCount(1);
        }
    }

    public record OpenData(BlockPos blockPos, List<CartEntryData> cartEntries) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, data) -> {
                            buf.writeBlockPos(data.blockPos());
                            buf.writeVarInt(data.cartEntries().size());
                            for (var entry : data.cartEntries()) {
                                CartEntryData.STREAM_CODEC.encode(buf, entry);
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int count = buf.readVarInt();
                            ArrayList<CartEntryData> entries = new ArrayList<>(count);
                            for (int i = 0; i < count; i++) {
                                entries.add(CartEntryData.STREAM_CODEC.decode(buf));
                            }
                            return new OpenData(pos, entries);
                        }
                );

        public OpenData {
            blockPos = blockPos == null ? BlockPos.ZERO : blockPos.immutable();
            cartEntries = sanitizeCart(cartEntries);
        }
    }
}
