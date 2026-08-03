package com.spider.mtgcard.api;

import com.spider.mtgcard.util.StackData;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.UUID;

/**
 * Shared Card Database helpers for base cards and addon card items.
 */
public final class CardDatabaseCards {
    public static final String DB_COUNT_KEY = "mtg_db_count";
    public static final String DB_STACK_KEY = "mtg_db_key";

    private static final long MAX_DATABASE_COUNT = Long.MAX_VALUE;

    public static boolean canStore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        TcgCardMeta.DatabaseKeyInfo meta = TcgCardMeta.readDatabaseKeyInfo(stack);
        return !databaseKey(stack, meta).isBlank()
                && (!meta.set().isBlank() || !meta.collectorNumber().isBlank() || !meta.id().isBlank() || !meta.name().isBlank());
    }

    public static String databaseKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        CompoundTag root = StackData.readCustom(stack);
        if (root.getLong(DB_COUNT_KEY).orElse(0L) > 0L) {
            String storedKey = root.getString(DB_STACK_KEY).orElse("").trim();
            if (!storedKey.isBlank()) return storedKey;
        }

        return databaseKey(stack, TcgCardMeta.readDatabaseKeyInfo(stack));
    }

    private static String databaseKey(ItemStack stack, TcgCardMeta.DatabaseKeyInfo meta) {
        String itemId = itemId(stack);
        String game = normalize(TcgGameRegistry.normalizeFilterId(meta.game()));

        String set = normalize(meta.set());
        String collector = normalize(meta.collectorNumber());
        String stableId = normalize(meta.id());
        String name = normalize(meta.name());

        String identity;
        if (!set.isBlank() && !collector.isBlank()) {
            identity = "print:" + set + "#" + collector;
        } else if (!stableId.isBlank()) {
            identity = "id:" + stableId;
        } else if (!name.isBlank()) {
            identity = "name:" + name + "#set:" + set + "#collector:" + collector;
        } else {
            identity = "custom:" + canonicalCustomData(stack);
        }

        return itemId + "|game:" + game + "|" + identity + "|foil:" + isFoilVariant(stack, meta.foil());
    }

    public static boolean sameDatabaseCard(ItemStack a, ItemStack b) {
        String ak = databaseKey(a);
        return !ak.isBlank() && ak.equals(databaseKey(b));
    }

    public static long databaseCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        CompoundTag root = StackData.readCustom(stack);
        long stored = root.getLong(DB_COUNT_KEY).orElse(0L);
        if (stored > 0L) return stored;
        return Math.max(0L, stack.getCount());
    }

    public static ItemStack copyForDatabase(ItemStack source, long count) {
        if (source == null || source.isEmpty() || count <= 0L) return ItemStack.EMPTY;

        ItemStack copy = source.copy();
        clearInstanceUid(copy);
        setDatabaseCount(copy, count);
        return copy;
    }

    public static void setDatabaseCount(ItemStack stack, long count) {
        if (stack == null || stack.isEmpty()) return;

        long safeCount = Math.max(1L, count);
        CompoundTag root = StackData.readCustom(stack);
        root.putLong(DB_COUNT_KEY, safeCount);
        root.putString(DB_STACK_KEY, databaseKey(stack, TcgCardMeta.readDatabaseKeyInfo(stack)));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.setCount(1);
    }

    public static long saturatedAdd(long current, long add) {
        if (add <= 0L) return current;
        if (current >= MAX_DATABASE_COUNT - add) return MAX_DATABASE_COUNT;
        return current + add;
    }

    public static void clearDatabaseFields(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return;

        CompoundTag root = comp.copyTag();
        root.remove(DB_COUNT_KEY);
        root.remove(DB_STACK_KEY);
        writeOrRemoveCustomData(stack, root);
    }

    public static void clearInstanceUid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return;

        CompoundTag root = comp.copyTag();
        root.remove("mtg_uid");
        root.remove("tcg_uid");

        removeNestedUid(root, TcgCardMeta.MTG_META);
        removeNestedUid(root, TcgCardMeta.TCG_META);

        writeOrRemoveCustomData(stack, root);
    }

    public static ItemStack copyForExtraction(ItemStack stored) {
        if (stored == null || stored.isEmpty()) return ItemStack.EMPTY;

        ItemStack out = stored.copy();
        clearDatabaseFields(out);
        ensureUniqueUid(out);
        out.setCount(1);
        return out;
    }

    public static void ensureUniqueUid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        String uid = UUID.randomUUID().toString();
        CompoundTag root = StackData.readCustom(stack);
        root.putString("mtg_uid", uid);

        CompoundTag mtg = root.getCompound(TcgCardMeta.MTG_META).orElse(null);
        if (mtg != null) {
            mtg.putString("mtg_uid", uid);
            root.put(TcgCardMeta.MTG_META, mtg);
        }

        CompoundTag tcg = root.getCompound(TcgCardMeta.TCG_META).orElse(null);
        if (tcg != null) {
            tcg.putString("mtg_uid", uid);
            root.put(TcgCardMeta.TCG_META, tcg);
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void removeNestedUid(CompoundTag root, String compoundKey) {
        CompoundTag nested = root.getCompound(compoundKey).orElse(null);
        if (nested == null) return;

        nested.remove("mtg_uid");
        nested.remove("tcg_uid");
        root.put(compoundKey, nested);
    }

    private static void writeOrRemoveCustomData(ItemStack stack, CompoundTag root) {
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    private static boolean isFoilVariant(ItemStack stack, boolean metadataFoil) {
        if (metadataFoil) return true;
        Boolean glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        return glint != null && glint;
    }

    private static String itemId(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static String canonicalCustomData(ItemStack stack) {
        CompoundTag root = StackData.readCustom(stack);
        root.remove(DB_COUNT_KEY);
        root.remove(DB_STACK_KEY);
        root.remove("mtg_uid");
        root.remove("tcg_uid");
        removeNestedUid(root, TcgCardMeta.MTG_META);
        removeNestedUid(root, TcgCardMeta.TCG_META);
        return Integer.toHexString(root.toString().hashCode());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private CardDatabaseCards() {
    }
}
