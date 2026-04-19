package com.spider.mtgcard.db;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-scoped per-player storage for the Card Database intake list (unbounded).
 * Mirrors CardDBState’s pattern so getOrCreate(TYPE) compiles under your mappings.
 */
public final class PlayerCardDBState extends SavedData {
    public static final String NAME = "mtgcard_player_card_db";

    public record SortPreference(String order, boolean ascending, boolean enabled) {
        public static final SortPreference DEFAULT = new SortPreference("name", true, false);
    }

    /** player UUID -> serialized intake snapshot (compact list entry format) */
    private final Map<UUID, ListTag> intakeByPlayer = new HashMap<>();
    private final Map<UUID, SortPreference> sortPreferenceByPlayer = new HashMap<>();

    /* --------------------- Public API --------------------- */

    public ListTag getIntake(UUID id) { return intakeByPlayer.get(id); }

    public void putIntake(UUID id, ListTag list) {
        // shallow copy so callers can’t mutate our stored reference
        ListTag copy = new ListTag();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                var c = list.getCompound(i).orElse(null);
                if (c != null) copy.add(c.copy());
            }
        }
        intakeByPlayer.put(id, copy);
        setDirty();
    }

    public SortPreference getSortPreference(UUID id) {
        return sortPreferenceByPlayer.getOrDefault(id, SortPreference.DEFAULT);
    }

    public void putSortPreference(UUID id, String order, boolean ascending, boolean enabled) {
        String normalizedOrder = (order == null || order.isBlank()) ? "name" : order;
        sortPreferenceByPlayer.put(id, new SortPreference(normalizedOrder, ascending, enabled));
        setDirty();
    }

    /* --------------------- NBT write/read --------------------- */
    // NOTE: Don’t annotate with @Override in your mappings.

    /** Writes: players:[ {id:"<uuid>", intake:[...]} ] */
    public CompoundTag writeNbt(CompoundTag nbt) {
        var players = new ListTag();
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        ids.addAll(intakeByPlayer.keySet());
        ids.addAll(sortPreferenceByPlayer.keySet());

        for (UUID id : ids) {
            var c = new CompoundTag();
            c.putString("id", id.toString());  // store UUID as string
            var listCopy = new ListTag();
            var src = intakeByPlayer.get(id);
            if (src != null) {
                for (int i = 0; i < src.size(); i++) {
                    listCopy.add(src.getCompound(i).orElse(new CompoundTag()).copy());
                }
            }
            c.put("intake", listCopy);
            var pref = sortPreferenceByPlayer.getOrDefault(id, SortPreference.DEFAULT);
            c.putString("sort_order", pref.order());
            c.putBoolean("sort_ascending", pref.ascending());
            c.putBoolean("sort_enabled", pref.enabled());
            players.add(c);
        }
        nbt.put("players", players);
        return nbt;
    }

    /** Reader used by the TYPE codec. */
    public static PlayerCardDBState readFromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        var s = new PlayerCardDBState();
        var playersOpt = nbt.getList("players");
        if (playersOpt.isPresent()) {
            var players = playersOpt.get();
            for (int i = 0; i < players.size(); i++) {
                var c = players.getCompound(i).orElse(null);
                if (c == null) continue;

                String idStr = c.getString("id").orElse("");
                if (idStr.isEmpty()) continue;

                UUID id;
                try {
                    id = UUID.fromString(idStr);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                var intake = c.getList("intake").orElse(new ListTag());
                s.intakeByPlayer.put(id, intake);
                String order = c.getString("sort_order").orElse("name");
                boolean ascending = c.getBoolean("sort_ascending").orElse(true);
                boolean enabled = c.getBoolean("sort_enabled").orElse(false);
                s.sortPreferenceByPlayer.put(id, new SortPreference(order, ascending, enabled));
            }
        }
        return s;
    }

    /* --------------------- DFU Codec + TYPE --------------------- */

    /** Passthrough codec (store our whole state as an NBT blob), like CardDBState. */
    public static final Codec<PlayerCardDBState> CODEC = Codec.PASSTHROUGH.xmap(
            dyn -> {
                Object val = dyn.convert(NbtOps.INSTANCE).getValue();
                CompoundTag root;
                if (val instanceof CompoundTag c) {
                    root = c;
                } else if (val instanceof Tag el) {
                    root = new CompoundTag();
                } else {
                    root = new CompoundTag();
                }
                return readFromNbt(root, null);
            },
            state -> {
                CompoundTag out = state.writeNbt(new CompoundTag());
                return new Dynamic<>(NbtOps.INSTANCE, out);
            }
    );

    /** Use the same DataFixTypes bucket CardDBState used. */
    public static final SavedDataType<PlayerCardDBState> TYPE =
            new SavedDataType<>(
                    NAME,
                    PlayerCardDBState::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
            );

    /** Accessor used by server code (compatible with your getOrCreate signature). */
    public static PlayerCardDBState get(ServerLevel world) {
        var mgr = world.getDataStorage();
        return mgr.computeIfAbsent(TYPE);
    }
}
