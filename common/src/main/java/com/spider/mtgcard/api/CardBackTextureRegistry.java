package com.spider.mtgcard.api;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.util.StackData;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class CardBackTextureRegistry {
    private static final Identifier DEFAULT_BACK =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/item/card.png");

    private static final Map<String, Supplier<Identifier>> CARD_BACKS = new LinkedHashMap<>();
    private static final Map<Item, Supplier<Identifier>> ITEM_CARD_BACKS = new LinkedHashMap<>();

    public static void register(String game, Identifier texture) {
        register(game, () -> texture);
    }

    public static synchronized void register(String game, Supplier<Identifier> textureSupplier) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Card back game must not be blank");
        }
        if (textureSupplier == null) {
            throw new IllegalArgumentException("Card back texture supplier must not be null");
        }
        CARD_BACKS.put(normalized, () -> normalizeTexture(textureSupplier.get()).orElse(DEFAULT_BACK));
    }

    public static Identifier textureForStackOrDefault(ItemStack stack) {
        Identifier explicit = explicitTexture(stack);
        if (explicit != null) return explicit;

        Identifier itemTexture = textureForItem(stack);
        if (itemTexture != null) return itemTexture;

        return textureForGameOrDefault(TcgCardMeta.read(stack).game());
    }

    /** Add-on API: supplies a facedown texture for one concrete card item type. */
    public static synchronized void registerItem(Item item, Identifier texture) {
        registerItem(item, () -> texture);
    }

    /** Add-on API: supplies a lazily resolved facedown texture for one card item type. */
    public static synchronized void registerItem(Item item, Supplier<Identifier> textureSupplier) {
        if (item == null || item == Items.AIR) throw new IllegalArgumentException("Card back item must not be air");
        if (textureSupplier == null) throw new IllegalArgumentException("Card back texture supplier must not be null");
        ITEM_CARD_BACKS.put(item, () -> normalizeTexture(textureSupplier.get()).orElse(DEFAULT_BACK));
    }

    private static synchronized Identifier textureForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Supplier<Identifier> supplier = ITEM_CARD_BACKS.get(stack.getItem());
        return supplier == null ? null : supplier.get();
    }

    public static synchronized Identifier textureForGameOrDefault(String game) {
        String normalized = safeGame(game);
        if (!normalized.isBlank()) {
            Supplier<Identifier> supplier = CARD_BACKS.get(normalized);
            if (supplier != null) {
                Identifier texture = supplier.get();
                if (texture != null) return texture;
            }
        }
        return DEFAULT_BACK;
    }

    static synchronized void registerItemTextureIfAbsent(String game, Supplier<? extends Item> itemSupplier) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        if (normalized.isBlank() || itemSupplier == null) return;

        CARD_BACKS.putIfAbsent(normalized, () -> textureFromItem(itemSupplier.get()));
        Item item = itemSupplier.get();
        if (item != null && item != Items.AIR) {
            ITEM_CARD_BACKS.putIfAbsent(item, () -> textureFromItem(itemSupplier.get()));
        }
    }

    private static Identifier explicitTexture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CompoundTag root = StackData.readCustom(stack);
        Identifier texture = readTexture(root);
        if (texture != null) return texture;

        texture = root.getCompound(TcgCardMeta.TCG_META).map(CardBackTextureRegistry::readTexture).orElse(null);
        if (texture != null) return texture;

        return root.getCompound(TcgCardMeta.MTG_META).map(CardBackTextureRegistry::readTexture).orElse(null);
    }

    private static Identifier readTexture(CompoundTag tag) {
        if (tag == null) return null;

        for (String key : new String[]{"card_back_texture", "back_texture", "cardBackTexture"}) {
            Optional<Identifier> texture = normalizeTexture(tag.getString(key).orElse(""));
            if (texture.isPresent()) return texture.get();
        }
        return null;
    }

    private static Identifier textureFromItem(Item item) {
        if (item == null || item == Items.AIR) return DEFAULT_BACK;

        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null || itemId.getPath().isBlank()) return DEFAULT_BACK;

        return Identifier.fromNamespaceAndPath(
                itemId.getNamespace(),
                "textures/item/" + itemId.getPath() + ".png"
        );
    }

    private static Optional<Identifier> normalizeTexture(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        try {
            return normalizeTexture(Identifier.parse(raw.trim()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Identifier> normalizeTexture(Identifier texture) {
        if (texture == null || texture.getPath().isBlank()) return Optional.empty();

        String path = texture.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return Optional.of(Identifier.fromNamespaceAndPath(texture.getNamespace(), path));
    }

    private static String safeGame(String game) {
        try {
            return TcgGameRegistry.normalizeGameId(game);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private CardBackTextureRegistry() {
    }
}
