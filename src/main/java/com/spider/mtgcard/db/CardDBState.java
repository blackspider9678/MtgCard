package com.spider.mtgcard.db;

import com.spider.mtgcard.api.CardItemRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * World-scoped persistent storage for all Card Database blocks.
 * Persists both:
 *  - per-block virtual store totals (variantKey -> count)
 *  - per-block intake snapshot (either a 54-slot inventory snapshot OR an unbounded list snapshot)
 *
 * This stays mapping-agnostic by using a passthrough DFU codec that stores an NBT blob.
 */
public final class CardDBState extends net.minecraft.world.level.saveddata.SavedData {
    public static final String NAME = "mtgcard_card_db";

    /** pos -> (variantKey -> count) */
    private final Map<BlockPos, Map<String, Long>> storeData = new HashMap<>();
    /** pos -> serialized intake snapshot (list of slot compounds) */
    private final Map<BlockPos, ListTag> intakeData = new HashMap<>();

    /* ---------- Store (virtual) ---------- */

    public Map<String, Long> getStore(BlockPos pos) {
        return storeData.computeIfAbsent(pos.immutable(), p -> new HashMap<>());
    }

    public void putStore(BlockPos pos, Map<String, Long> snapshot) {
        storeData.put(pos.immutable(), new HashMap<>(snapshot));
        setDirty();
    }

    /* ---------- Intake (inventory/list snapshot) ---------- */

    /** Save a snapshot of the intake inventory (or list) for a block. */
    public void putIntake(BlockPos pos, ListTag intakeList) {
        // store a shallow copy to avoid outside mutations
        ListTag copy = new ListTag();
        if (intakeList != null) {
            for (int i = 0; i < intakeList.size(); i++) {
                var c = intakeList.getCompound(i).orElse(null);
                if (c != null) copy.add(c.copy());
            }
        }
        intakeData.put(pos.immutable(), copy);
        setDirty();
    }

    /** Retrieve previously saved intake list for a block, or null. */
    public ListTag getIntake(BlockPos pos) {
        return intakeData.get(pos.immutable());
    }

    /** Remove everything for a block (when block is broken). */
    public void remove(BlockPos pos) {
        var p = pos.immutable();
        storeData.remove(p);
        intakeData.remove(p);
        setDirty();
    }

    /* ---------- NBT I/O (no @Override to keep mapping-agnostic) ---------- */

    /** Writes both store and intake maps under a single "blocks" list. */
    public CompoundTag writeNbt(CompoundTag nbt) {
        ListTag blocks = new ListTag();

        // union of all positions present in either map
        Map<BlockPos, Boolean> allPositions = new HashMap<>();
        storeData.keySet().forEach(p -> allPositions.put(p, true));
        intakeData.keySet().forEach(p -> allPositions.put(p, true));

        for (var pos : allPositions.keySet()) {
            CompoundTag be = new CompoundTag();
            be.putInt("x", pos.getX());
            be.putInt("y", pos.getY());
            be.putInt("z", pos.getZ());

            // store entries
            var map = storeData.get(pos);
            if (map != null && !map.isEmpty()) {
                ListTag entries = new ListTag();
                for (var se : map.entrySet()) {
                    CompoundTag c = new CompoundTag();
                    c.putString("k", se.getKey());
                    c.putLong("c", se.getValue());
                    entries.add(c);
                }
                be.put("entries", entries);
            }

            // intake entries
            var intake = intakeData.get(pos);
            if (intake != null && !intake.isEmpty()) {
                ListTag copy = new ListTag();
                for (int i = 0; i < intake.size(); i++) {
                    copy.add(intake.getCompound(i).orElse(new CompoundTag()).copy());
                }
                be.put("intake", copy);
            }

            blocks.add(be);
        }

        var ui = new ListTag();
        for (var e : uiPrefs.entrySet()) {
            var p = e.getKey(); var t = e.getValue();
            var c = new CompoundTag();
            c.putInt("x", p.getX()); c.putInt("y", p.getY()); c.putInt("z", p.getZ());
            c.put("v", t.copy());
            ui.add(c);
        }
        nbt.put("ui", ui);

        nbt.put("blocks", blocks);
        return nbt;
    }

    /** Reader used by the PersistentStateType. */
    public static CardDBState readFromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        CardDBState s = new CardDBState();

        var blocksOpt = nbt.getList("blocks");
        if (blocksOpt.isPresent()) {
            var blocks = blocksOpt.get();
            for (int i = 0; i < blocks.size(); i++) {
                var be = blocks.getCompound(i).orElse(null);
                if (be == null) continue;

                int x = be.getInt("x").orElse(0);
                int y = be.getInt("y").orElse(0);
                int z = be.getInt("z").orElse(0);
                BlockPos pos = new BlockPos(x, y, z).immutable();

                // store
                Map<String, Long> map = new HashMap<>();
                var entriesOpt = be.getList("entries");
                if (entriesOpt.isPresent()) {
                    var entries = entriesOpt.get();
                    for (int j = 0; j < entries.size(); j++) {
                        var c = entries.getCompound(j).orElse(null);
                        if (c == null) continue;
                        String k = c.getString("k").orElse("");
                        long v   = c.getLong("c").orElse(0L);
                        if (!k.isEmpty() && v > 0) map.put(k, v);
                    }
                }
                if (!map.isEmpty()) s.storeData.put(pos, map);

                // intake
                var intakeOpt = be.getList("intake");
                if (intakeOpt.isPresent()) {
                    s.intakeData.put(pos, intakeOpt.get());
                }

            }

        }
        var uiOpt = nbt.getList("ui");
        if (uiOpt.isPresent()) {
            var lst = uiOpt.get();
            for (int i = 0; i < lst.size(); i++) {
                var c = lst.getCompound(i).orElse(null);
                if (c == null) continue;
                int x = c.getInt("x").orElse(0);
                int y = c.getInt("y").orElse(0);
                int z = c.getInt("z").orElse(0);
                var v = c.getCompound("v").orElse(null);
                if (v != null) uiPrefs.put(new BlockPos(x,y,z).immutable(), v.copy());
            }
        }
        return s;
    }

    /* ---------- DFU Codec + TYPE wiring ---------- */

    /**
     * Minimal passthrough codec: DFU stores our whole state as an NBT blob.
     * This keeps us resilient to minor mapping changes in PersistentState.
     */
    public static final Codec<CardDBState> CODEC = Codec.PASSTHROUGH.xmap(
            dyn -> {
                // Convert whatever DFU hands us into NBT, then read
                Object val = dyn.convert(NbtOps.INSTANCE).getValue();
                CompoundTag root;
                if (val instanceof CompoundTag c) {
                    root = c;
                } else if (val instanceof Tag el) {
                    // not expected, but stay safe
                    root = new CompoundTag();
                } else {
                    root = new CompoundTag();
                }
                return CardDBState.readFromNbt(root, null);
            },
            state -> {
                CompoundTag out = state.writeNbt(new CompoundTag());
                return new Dynamic<>(NbtOps.INSTANCE, out);
            }
    );

    /**
     * PersistentStateType constructor in 1.21.10:
     * (name, Supplier<T>, Codec<T>, DataFixTypes)
     */
    public static final SavedDataType<CardDBState> TYPE =
            new SavedDataType<>(
                    NAME,
                    CardDBState::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
            );

    /** Accessor used by server code. */
    public static CardDBState get(ServerLevel world) {
        var mgr = world.getDataStorage();
        return mgr.computeIfAbsent(TYPE);
    }

    /* ---------- Convenience helpers for BlockEntity ---------- */
    // A) 54-slot inventory snapshot (legacy style; safe to keep)

    /**
     * Take a lightweight snapshot of a 54-slot intake inventory.
     * Each entry contains:
     *  - "i" (int) slot index
     *  - "c" (int) stack count
     *  - "cd" (compound, optional) CUSTOM_DATA component
     *  - "name_raw" (string, optional) plain custom name
     *  - "gl" (boolean, optional) enchantment glint override
     */
    public static ListTag snapshotIntake(net.minecraft.world.Container inv) {
        ListTag list = new ListTag();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (st.isEmpty()) continue;

            CompoundTag e = new CompoundTag();
            e.putInt("i", i);
            e.putInt("c", st.getCount());
            e.putString("item", CardItemRegistry.itemId(st));

            var cd = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (cd != null) e.put("cd", cd.copyTag());

            var name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (name != null) e.putString("name_raw", name.getString());

            var gl = st.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            if (gl != null && gl) e.putBoolean("gl", true);

            list.add(e);
        }
        return list;
    }

    /** Restore a snapshot into the given inventory (clears it first). */
    public static void applyIntakeSnapshot(net.minecraft.world.Container inv, ListTag list) {
        for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        if (list == null) return;

        for (int k = 0; k < list.size(); k++) {
            var e = list.getCompound(k).orElse(null);
            if (e == null) continue;

            int slot  = Math.max(0, Math.min(inv.getContainerSize() - 1, e.getInt("i").orElse(0)));
            int count = Math.max(1, e.getInt("c").orElse(1));

            var st = new net.minecraft.world.item.ItemStack(CardItemRegistry.itemForSerializedEntry(e), count);

            var cd = e.getCompound("cd").orElse(null);
            if (cd != null) {
                st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(cd));
            }

            String nameRaw = e.getString("name_raw").orElse("");
            if (!nameRaw.isEmpty()) {
                st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(nameRaw));
            }

            if (e.getBoolean("gl").orElse(false)) {
                st.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            inv.setItem(slot, st);
        }
    }

    // B) Unbounded list snapshot (used by the new virtual/paged intake)

    /** Serialize an arbitrary List<ItemStack> into the same compact entry format. */
    public static ListTag snapshotFromList(java.util.List<net.minecraft.world.item.ItemStack> list) {
        ListTag out = new ListTag();
        if (list == null) return out;

        for (int i = 0; i < list.size(); i++) {
            var st = list.get(i);
            if (st == null || st.isEmpty()) continue;

            var e = new CompoundTag();
            e.putInt("i", i);
            e.putInt("c", st.getCount());
            e.putString("item", CardItemRegistry.itemId(st));

            var cd = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (cd != null) e.put("cd", cd.copyTag());

            var name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (name != null) e.putString("name_raw", name.getString());

            var gl = st.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            if (gl != null && gl) e.putBoolean("gl", true);

            out.add(e);
        }
        return out;
    }

    private static final Map<BlockPos, CompoundTag> uiPrefs = new HashMap<>();

    public void putUiPrefs(BlockPos pos, String q, String order, String dir) {
        var tag = new CompoundTag();
        tag.putString("q", q == null ? "" : q);
        tag.putString("order", order == null ? "name" : order);
        tag.putString("dir", dir == null ? "asc" : dir);
        uiPrefs.put(pos.immutable(), tag);
        setDirty();
    }
    public CompoundTag getUiPrefs(BlockPos pos) {
        return uiPrefs.get(pos.immutable());
    }

    /** Populate a List<ItemStack> from our compact entry format. */
    public static void applyIntakeToList(java.util.List<net.minecraft.world.item.ItemStack> dst, ListTag list) {
        dst.clear();
        if (list == null) return;

        for (int k = 0; k < list.size(); k++) {
            var e = list.getCompound(k).orElse(null);
            if (e == null) continue;

            int i = Math.max(0, e.getInt("i").orElse(0));
            int count = Math.max(1, e.getInt("c").orElse(1));

            while (dst.size() <= i) dst.add(net.minecraft.world.item.ItemStack.EMPTY);

            var st = new net.minecraft.world.item.ItemStack(CardItemRegistry.itemForSerializedEntry(e), count);

            var cd = e.getCompound("cd").orElse(null);
            if (cd != null) {
                st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(cd));
            }

            String nameRaw = e.getString("name_raw").orElse("");
            if (!nameRaw.isEmpty()) {
                st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal(nameRaw));
            }

            if (e.getBoolean("gl").orElse(false)) {
                st.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            dst.set(i, st);
        }
    }
}
