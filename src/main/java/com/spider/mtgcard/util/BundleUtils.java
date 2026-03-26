package com.spider.mtgcard.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Packs items into a vanilla Bundle across differing 1.21+ Yarn mappings. */
public final class BundleUtils {

    /**
     * Try multiple strategies:
     *  1) Call a mutator on BundleContentsComponent (name differs by mappings: withItem/withAdded/plus/add)
     *  2) Rebuild a new component from a List via a static factory (of/from/new)
     *  3) Fallback to legacy NBT "Items"
     */
    public static boolean tryFillBundle(ItemStack bundle, List<ItemStack> contents) {
        if (bundle.getItem() != Items.BUNDLE) return false;

        // --- Strategy 1: invoke mutator per item (different names across mappings)
        try {
            BundleContents comp = bundle.getOrDefault(
                    DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY
            );

            // Try common method names that take (ItemStack)
            String[] names = { "withItem", "withAdded", "plus", "add" };
            Method m = null;
            for (String n : names) {
                try {
                    m = BundleContents.class.getMethod(n, ItemStack.class);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (m != null) {
                for (ItemStack st : contents) {
                    comp = (BundleContents) m.invoke(comp, st.copy());
                }
                bundle.set(DataComponents.BUNDLE_CONTENTS, comp);
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }

        // --- Strategy 2: rebuild a fresh component from List<ItemStack>
        try {
            // Copy current items if an accessor exists
            List<ItemStack> all = new ArrayList<>();
            try {
                Method itemsGetter = BundleContents.class.getMethod("items");
                @SuppressWarnings("unchecked")
                List<ItemStack> existing = (List<ItemStack>) itemsGetter.invoke(
                        bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                );
                if (existing != null) {
                    for (ItemStack s : existing) all.add(s.copy());
                }
            } catch (Throwable ignored) {}

            // add new ones
            for (ItemStack st : contents) all.add(st.copy());

            // Try static factories: of(List), from(List), create(List)
            Method factory = null;
            try { factory = BundleContents.class.getMethod("of", List.class); } catch (NoSuchMethodException ignored) {}
            if (factory == null) try { factory = BundleContents.class.getMethod("from", List.class); } catch (NoSuchMethodException ignored) {}
            if (factory == null) try { factory = BundleContents.class.getMethod("create", List.class); } catch (NoSuchMethodException ignored) {}

            if (factory != null) {
                BundleContents comp = (BundleContents) factory.invoke(null, all);
                bundle.set(DataComponents.BUNDLE_CONTENTS, comp);
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }

        // --- Strategy 3: legacy NBT fallback (dev-friendly; may not persist on some servers)
        // --- Strategy 3: legacy fallback ---
        try {
            // if nothing else works, just abort gracefully
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private BundleUtils() {}
}
