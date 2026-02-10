package com.spider.mtgcard.db;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-scoped per-player storage for the Card Database intake list (unbounded).
 * Mirrors CardDBState’s pattern so getOrCreate(TYPE) compiles under your mappings.
 */
public final class PlayerCardDBState extends PersistentState {
    public static final String NAME = "mtgcard_player_card_db";

    /** player UUID -> serialized intake snapshot (compact list entry format) */
    private final Map<UUID, NbtList> intakeByPlayer = new HashMap<>();

    /* --------------------- Public API --------------------- */

    public NbtList getIntake(UUID id) { return intakeByPlayer.get(id); }

    public void putIntake(UUID id, NbtList list) {
        // shallow copy so callers can’t mutate our stored reference
        NbtList copy = new NbtList();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                var c = list.getCompound(i).orElse(null);
                if (c != null) copy.add(c.copy());
            }
        }
        intakeByPlayer.put(id, copy);
        markDirty();
    }

    /* --------------------- NBT write/read --------------------- */
    // NOTE: Don’t annotate with @Override in your mappings.

    /** Writes: players:[ {id:"<uuid>", intake:[...]} ] */
    public NbtCompound writeNbt(NbtCompound nbt) {
        var players = new NbtList();
        for (var e : intakeByPlayer.entrySet()) {
            var c = new NbtCompound();
            c.putString("id", e.getKey().toString());  // store UUID as string
            var listCopy = new NbtList();
            var src = e.getValue();
            if (src != null) {
                for (int i = 0; i < src.size(); i++) {
                    listCopy.add(src.getCompound(i).orElse(new NbtCompound()).copy());
                }
            }
            c.put("intake", listCopy);
            players.add(c);
        }
        nbt.put("players", players);
        return nbt;
    }

    /** Reader used by the TYPE codec. */
    public static PlayerCardDBState readFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
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

                var intake = c.getList("intake").orElse(new NbtList());
                s.intakeByPlayer.put(id, intake);
            }
        }
        return s;
    }

    /* --------------------- DFU Codec + TYPE --------------------- */

    /** Passthrough codec (store our whole state as an NBT blob), like CardDBState. */
    public static final Codec<PlayerCardDBState> CODEC = Codec.PASSTHROUGH.xmap(
            dyn -> {
                Object val = dyn.convert(NbtOps.INSTANCE).getValue();
                NbtCompound root;
                if (val instanceof NbtCompound c) {
                    root = c;
                } else if (val instanceof NbtElement el) {
                    root = new NbtCompound();
                } else {
                    root = new NbtCompound();
                }
                return readFromNbt(root, null);
            },
            state -> {
                NbtCompound out = state.writeNbt(new NbtCompound());
                return new Dynamic<>(NbtOps.INSTANCE, out);
            }
    );

    /** Use the same DataFixTypes bucket CardDBState used. */
    public static final PersistentStateType<PlayerCardDBState> TYPE =
            new PersistentStateType<>(NAME, PlayerCardDBState::new, CODEC, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    /** Accessor used by server code (compatible with your getOrCreate signature). */
    public static PlayerCardDBState get(ServerWorld world) {
        PersistentStateManager mgr = world.getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }
}
