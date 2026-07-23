// com.spider.mtgcard.net.NetState.java
package com.spider.mtgcard.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;

public final class NetState extends SavedData {
    private final Map<String, String> art = new Object2ObjectOpenHashMap<>();

    /* -------------------- Public API -------------------- */
    public void registerArt(String key, String fileName, MinecraftServer server) {
        art.put(key, fileName);
        this.setDirty();
    }
    public Map<String, String> artView() { return art; }

    /* -------------------- NBT IO -------------------- */
    // No @Override (mapping signatures vary)
    public CompoundTag writeNbt(CompoundTag nbt) {
        CompoundTag a = new CompoundTag();
        for (var e : art.entrySet()) a.putString(e.getKey(), e.getValue());
        nbt.put("art", a);
        return nbt;
    }

    // Reader that does not depend on registries (since ctor wants a plain Codec)
    public static NetState fromNbt(CompoundTag nbt) {
        NetState s = new NetState();
        // Your getters return Optionals — unwrap safely:
        CompoundTag a = nbt.getCompound("art").orElse(new CompoundTag());
        for (String k : a.keySet()) {
            String v = a.getString(k).orElse("");
            s.art.put(k, v);
        }
        return s;
    }

    /* -------------------- Codec & Type -------------------- */
    private static final Codec<NetState> CODEC =
            CompoundTag.CODEC.xmap(
                    NetState::fromNbt,
                    state -> state.writeNbt(new CompoundTag())
            );

    private static final SavedDataType<NetState> TYPE =
            new SavedDataType<>(
                    Identifier.fromNamespaceAndPath("mtgcard", "mtgcard_net"),
                    NetState::new,             // supplier for new/empty state
                    CODEC,                     // how to (de)serialize
                    DataFixTypes.LEVEL         // pick LEVEL (or WORLD) in your mappings
            );

    /* -------------------- Access -------------------- */
    public static NetState get(MinecraftServer server) {
        var mgr = server.overworld().getDataStorage();
        return mgr.computeIfAbsent(TYPE);
    }
}
