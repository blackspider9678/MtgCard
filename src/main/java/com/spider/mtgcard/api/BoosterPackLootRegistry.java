package com.spider.mtgcard.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Registration point for add-on booster packs that share MTGCard's natural chest spawns. */
public final class BoosterPackLootRegistry {
    public record Entry(Supplier<? extends Item> item, float chance) { }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void register(Item item, float chance) { register(() -> item, chance); }

    public static synchronized void register(Supplier<? extends Item> item, float chance) {
        if (item == null) throw new IllegalArgumentException("Booster pack supplier must not be null");
        float normalizedChance = Math.max(0.0f, Math.min(1.0f, chance));
        Item resolved = item.get();
        if (resolved == null || resolved == Items.AIR || normalizedChance <= 0.0f) return;
        for (Entry entry : ENTRIES) if (entry.item().get() == resolved) return;
        ENTRIES.add(new Entry(item, normalizedChance));
    }

    public static synchronized List<Entry> entries() { return List.copyOf(ENTRIES); }

    private BoosterPackLootRegistry() { }
}
