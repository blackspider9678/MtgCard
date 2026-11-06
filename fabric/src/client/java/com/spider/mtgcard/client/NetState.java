// com.spider.mtgcard.net.NetState.java
package com.spider.mtgcard.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

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
    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag a = new CompoundTag();
        for (var e : art.entrySet()) a.putString(e.getKey(), e.getValue());
        nbt.put("art", a);
        return nbt;
    }

    // Reader that does not depend on registries (since ctor wants a plain Codec)
    public static NetState fromNbt(CompoundTag nbt) {
        NetState s = new NetState();
        // Your getters return Optionals — unwrap safely:
        CompoundTag a = nbt.getCompound("art");
        for (String k : a.getAllKeys()) {
            String v = a.getString(k);
            s.art.put(k, v);
        }
        return s;
    }

    private static final SavedData.Factory<NetState> FACTORY =
            new SavedData.Factory<>(NetState::new, (tag, registries) -> NetState.fromNbt(tag), DataFixTypes.LEVEL);

    /* -------------------- Access -------------------- */
    public static NetState get(MinecraftServer server) {
        var mgr = server.overworld().getDataStorage();
        return mgr.computeIfAbsent(FACTORY, "mtgcard_net");
    }
}
