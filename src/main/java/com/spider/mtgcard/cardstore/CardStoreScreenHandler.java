// src/main/java/com/spider/mtgcard/cardstore/CardStoreScreenHandler.java
package com.spider.mtgcard.cardstore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.spider.mtgcard.api.CardStoreProviderRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.config.MtgcardConfig;
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
    private final String priceItemId;
    private final String priceBasis;
    private final String selectedGame;

    // Layout constants (must match what your Screen expects visually)
    public static final int MARGIN = 10;
    public static final int INV_BLOCK_H = (3 * 18) + 4 + 18; // 3 rows + gap + hotbar

    public CardStoreScreenHandler(int syncId, Inventory playerInv) {
        this(syncId, playerInv, new OpenData(BlockPos.ZERO, List.of(), defaultPriceItemId(), defaultPriceBasis(), TcgGameRegistry.ALL_GAMES));
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, BlockPos blockPos) {
        this(syncId, playerInv, new OpenData(blockPos, List.of(), defaultPriceItemId(), defaultPriceBasis(), TcgGameRegistry.ALL_GAMES));
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, BlockPos blockPos, List<CartEntryData> initialCart) {
        this(syncId, playerInv, new OpenData(blockPos, initialCart, defaultPriceItemId(), defaultPriceBasis(), TcgGameRegistry.ALL_GAMES));
    }

    public CardStoreScreenHandler(int syncId, Inventory playerInv, OpenData openData) {
        super(ModScreenHandlers.CARD_STORE, syncId);
        OpenData data = openData == null
                ? new OpenData(BlockPos.ZERO, List.of(), defaultPriceItemId(), defaultPriceBasis(), TcgGameRegistry.ALL_GAMES)
                : openData;
        this.blockPos = data.blockPos();
        this.initialCart = data.cartEntries();
        this.priceItemId = data.priceItemId();
        this.priceBasis = data.priceBasis();
        this.selectedGame = data.selectedGame();

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

    public String priceItemId() {
        return priceItemId;
    }

    public String priceBasis() {
        return priceBasis;
    }

    public String selectedGame() {
        return selectedGame;
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

    public static String defaultPriceItemId() {
        MtgcardConfig cfg = MtgcardConfig.get();
        return sanitizePriceItemId(cfg == null ? null : cfg.Price_Item);
    }

    public static String defaultPriceBasis() {
        MtgcardConfig cfg = MtgcardConfig.get();
        return sanitizePriceBasis(cfg == null ? null : cfg.Price_Basis);
    }

    public static String sanitizePriceItemId(String raw) {
        return raw == null || raw.isBlank() ? "minecraft:diamond" : raw.trim();
    }

    public static String sanitizePriceBasis(String raw) {
        return raw == null || raw.isBlank() ? "USD" : raw.trim();
    }

    public record CartEntryData(String game, String set, String cn, ItemStack stack, long priceItems, int qty) {
        public static final Codec<CartEntryData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("Game", TcgGameRegistry.MTG).forGetter(CartEntryData::game),
                Codec.STRING.fieldOf("Set").forGetter(CartEntryData::set),
                Codec.STRING.fieldOf("Cn").forGetter(CartEntryData::cn),
                ItemStack.CODEC.fieldOf("Stack").forGetter(CartEntryData::stack),
                Codec.LONG.fieldOf("PriceItems").forGetter(CartEntryData::priceItems),
                Codec.INT.fieldOf("Qty").forGetter(CartEntryData::qty)
        ).apply(inst, CartEntryData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CartEntryData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, entry) -> {
                            buf.writeUtf(entry.game(), 128);
                            buf.writeUtf(entry.set());
                            buf.writeUtf(entry.cn(), 64);
                            ItemStack.STREAM_CODEC.encode(buf, entry.stack());
                            buf.writeVarLong(entry.priceItems());
                            buf.writeVarInt(entry.qty());
                        },
                        (buf) -> new CartEntryData(
                                buf.readUtf(128),
                                buf.readUtf(),
                                buf.readUtf(64),
                                ItemStack.STREAM_CODEC.decode(buf),
                                buf.readVarLong(),
                                buf.readVarInt()
                        )
                );

        public CartEntryData(String set, String cn, ItemStack stack, long priceItems, int qty) {
            this(TcgGameRegistry.MTG, set, cn, stack, priceItems, qty);
        }

        public CartEntryData {
            game = CardStoreProviderRegistry.sanitizeGameId(game);
            set = set == null ? "" : set.trim();
            cn = cn == null ? "" : cn.trim();
            stack = sanitizeStack(stack);
            priceItems = Math.max(0L, priceItems);
            qty = Math.max(1, qty);
        }

        public boolean isValid() {
            return !game.isBlank() && !set.isBlank() && !cn.isBlank() && !stack.isEmpty() && qty > 0;
        }

        private static ItemStack sanitizeStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            return stack.copyWithCount(1);
        }
    }

    public record OpenData(BlockPos blockPos, List<CartEntryData> cartEntries, String priceItemId, String priceBasis, String selectedGame) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC =
                StreamCodec.of(
                        (buf, data) -> {
                            buf.writeBlockPos(data.blockPos());
                            buf.writeUtf(data.priceItemId());
                            buf.writeUtf(data.priceBasis());
                            buf.writeUtf(data.selectedGame(), 128);
                            buf.writeVarInt(data.cartEntries().size());
                            for (var entry : data.cartEntries()) {
                                CartEntryData.STREAM_CODEC.encode(buf, entry);
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String priceItemId = buf.readUtf();
                            String priceBasis = buf.readUtf();
                            String selectedGame = buf.readUtf(128);
                            int count = buf.readVarInt();
                            ArrayList<CartEntryData> entries = new ArrayList<>(count);
                            for (int i = 0; i < count; i++) {
                                entries.add(CartEntryData.STREAM_CODEC.decode(buf));
                            }
                            return new OpenData(pos, entries, priceItemId, priceBasis, selectedGame);
                        }
                );

        public OpenData(BlockPos blockPos, List<CartEntryData> cartEntries, String priceItemId, String priceBasis) {
            this(blockPos, cartEntries, priceItemId, priceBasis, TcgGameRegistry.ALL_GAMES);
        }

        public OpenData {
            blockPos = blockPos == null ? BlockPos.ZERO : blockPos.immutable();
            cartEntries = sanitizeCart(cartEntries);
            priceItemId = sanitizePriceItemId(priceItemId);
            priceBasis = sanitizePriceBasis(priceBasis);
            selectedGame = CardStoreProviderRegistry.sanitizeFilterId(selectedGame);
        }
    }
}
