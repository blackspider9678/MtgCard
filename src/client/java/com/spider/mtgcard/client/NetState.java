// com.spider.mtgcard.net.NetState.java
package com.spider.mtgcard.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.Map;

public final class NetState extends PersistentState {
    private final Map<String, String> art = new Object2ObjectOpenHashMap<>();

    /* -------------------- Public API -------------------- */
    public void registerArt(String key, String fileName, MinecraftServer server) {
        art.put(key, fileName);
        this.markDirty();
    }
    public Map<String, String> artView() { return art; }

    /* -------------------- NBT IO -------------------- */
    // No @Override (mapping signatures vary)
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound a = new NbtCompound();
        for (var e : art.entrySet()) a.putString(e.getKey(), e.getValue());
        nbt.put("art", a);
        return nbt;
    }

    // Reader that does not depend on registries (since ctor wants a plain Codec)
    public static NetState fromNbt(NbtCompound nbt) {
        NetState s = new NetState();
        // Your getters return Optionals — unwrap safely:
        NbtCompound a = nbt.getCompound("art").orElse(new NbtCompound());
        for (String k : a.getKeys()) {
            String v = a.getString(k).orElse("");
            s.art.put(k, v);
        }
        return s;
    }

    /* -------------------- Codec & Type -------------------- */
    private static final Codec<NetState> CODEC =
            NbtCompound.CODEC.xmap(
                    NetState::fromNbt,
                    state -> state.writeNbt(new NbtCompound())
            );

    private static final PersistentStateType<NetState> TYPE =
            new PersistentStateType<>(
                    "mtgcard_net",
                    NetState::new,             // supplier for new/empty state
                    CODEC,                     // how to (de)serialize
                    DataFixTypes.LEVEL         // pick LEVEL (or WORLD) in your mappings
            );

    /* -------------------- Access -------------------- */
    public static NetState get(MinecraftServer server) {
        PersistentStateManager mgr = server.getOverworld().getPersistentStateManager();
        return mgr.getOrCreate(TYPE);
    }
}
