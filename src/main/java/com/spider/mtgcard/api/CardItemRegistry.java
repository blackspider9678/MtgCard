package com.spider.mtgcard.api;

import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class CardItemRegistry {
    private static final Map<String, Supplier<? extends Item>> CARD_ITEMS = new LinkedHashMap<>();

    public static void register(String game, Item item) {
        register(game, () -> item);
    }

    public static synchronized void register(String game, Supplier<? extends Item> itemSupplier) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Card item game must not be blank");
        }
        if (itemSupplier == null) {
            throw new IllegalArgumentException("Card item supplier must not be null");
        }
        CARD_ITEMS.put(normalized, itemSupplier);
    }

    public static synchronized Optional<Item> get(String game) {
        String normalized = safeGame(game);
        if (normalized.isBlank()) return Optional.empty();

        Supplier<? extends Item> supplier = CARD_ITEMS.get(normalized);
        if (supplier == null) return Optional.empty();

        Item item = supplier.get();
        return item == null || item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    public static Item itemForGameOrDefault(String game) {
        return get(game).orElse(defaultCardItem());
    }

    public static Item itemForSerializedEntry(CompoundTag entry) {
        if (entry != null) {
            String itemId = entry.getString("item").orElse("");
            Item registeredItem = itemFromId(itemId);
            if (registeredItem != null) return registeredItem;

            CompoundTag customData = entry.getCompound("cd").orElse(null);
            String game = gameFromCustomData(customData);
            if (!game.isBlank()) return itemForGameOrDefault(game);
        }

        return defaultCardItem();
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static Item itemFromId(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId.trim()));
            return item == null || item == Items.AIR ? null : item;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String gameFromCustomData(CompoundTag customData) {
        if (customData == null) return "";

        CompoundTag tcg = customData.getCompound(TcgCardMeta.TCG_META).orElse(null);
        if (tcg != null) {
            String game = tcg.getString("game").orElse("");
            if (!game.isBlank()) return game.trim().toLowerCase(Locale.ROOT);
        }

        if (customData.getCompound(TcgCardMeta.MTG_META).isPresent()) {
            return TcgGameRegistry.MTG;
        }

        return "";
    }

    private static String safeGame(String game) {
        try {
            return TcgGameRegistry.normalizeGameId(game);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static Item defaultCardItem() {
        return ModItems.CARD;
    }

    private CardItemRegistry() {
    }
}
