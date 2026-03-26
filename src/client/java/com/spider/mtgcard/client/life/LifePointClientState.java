package com.spider.mtgcard.client.life;

import com.spider.mtgcard.life.LifePointPackets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LifePointClientState {

    // ---------------- storage ----------------

    /** Per-LifePoint position snapshot (client-side). */
    public static final Map<BlockPos, CompoundTag> CACHE = new ConcurrentHashMap<>();

    /** origin -> nearby life points (positions) */
    public static final Map<BlockPos, List<BlockPos>> NEARBY_RESULTS = new HashMap<>();

    /** origin -> (lifePos -> display name) */
    public static final Map<BlockPos, Map<BlockPos, String>> NEARBY_NAMES = new HashMap<>();

    // groupId -> snapshot
    public static final Map<UUID, GroupView> GROUPS = new HashMap<>();
    public static final Map<UUID, String> GROUP_NAMES = new HashMap<>();
    public static final List<LifePointPackets.GroupsListS2C.Entry> GROUP_LIST = new ArrayList<>();

    public static final class GroupView {
        public final UUID id;
        public String name = "New Group";
        public boolean started = false;
        public int activeIndex = 0;

        public List<BlockPos> order = List.of();
        public List<String> memberNames = List.of(); // aligned to order
        public Set<BlockPos> dead = Set.of();

        public GroupView(UUID id) { this.id = id; }
    }

    private LifePointClientState() {}

    // ---------------- core cache ----------------

    public static void onSync(@Nullable BlockPos pos, @Nullable CompoundTag nbt) {
        if (pos == null) return;

        if (nbt == null || nbt.isEmpty()) {
            CACHE.remove(pos);
            return;
        }

        // store a defensive copy (NBT can be mutated elsewhere)
        CACHE.put(pos, nbt.copy());
    }

    /**
     * Returns cached NBT or null if not present.
     * (DisplayLinkedLifeClient.resolve() treats null/empty as "no cache".)
     */
    public static @Nullable CompoundTag get(@Nullable BlockPos pos) {
        if (pos == null) return null;
        return CACHE.get(pos);
    }

    private static CompoundTag safe(@Nullable BlockPos pos) {
        CompoundTag n = get(pos);
        return (n == null) ? new CompoundTag() : n;
    }

    public static int getIconSwapColor(@Nullable BlockPos pos) {
        // default red, clamp to 24-bit
        return safe(pos).getInt("IconSwapColor").orElse(0xFF0000) & 0xFFFFFF;
    }

    // ---------------- nearby scan ----------------

    public static void onNearby(@Nullable BlockPos origin, @Nullable List<LifePointPackets.NearbyResultS2C.Entry> found) {
        if (origin == null) return;

        if (found == null || found.isEmpty()) {
            NEARBY_RESULTS.remove(origin);
            NEARBY_NAMES.remove(origin);
            return;
        }

        var positions = new ArrayList<BlockPos>(found.size());
        var names = new HashMap<BlockPos, String>();

        for (var e : found) {
            if (e == null || e.pos() == null) continue;
            positions.add(e.pos());
            names.put(e.pos(), (e.name() == null) ? "" : e.name());
        }

        NEARBY_RESULTS.put(origin, List.copyOf(positions));
        NEARBY_NAMES.put(origin, names);
    }

    public static String nearbyName(@Nullable BlockPos origin, @Nullable BlockPos p) {
        if (origin == null || p == null) return "";
        var map = NEARBY_NAMES.get(origin);
        if (map == null) return "";
        String v = map.get(p);
        return (v == null) ? "" : v;
    }

    // ---------------- groups ----------------

    public static void onGroupsList(@Nullable List<LifePointPackets.GroupsListS2C.Entry> groups) {
        GROUP_LIST.clear();
        GROUP_NAMES.clear();

        if (groups == null || groups.isEmpty()) return;

        GROUP_LIST.addAll(groups);
        for (var e : groups) {
            if (e == null || e.id() == null) continue;
            GROUP_NAMES.put(e.id(), (e.name() == null || e.name().isBlank()) ? "New Group" : e.name());
        }
    }

    public static void onGroup(@Nullable UUID id, @Nullable String name, boolean started, int activeIndex,
                               @Nullable List<BlockPos> members, @Nullable List<String> memberNames, @Nullable List<BlockPos> dead) {
        if (id == null) return;

        var g = GROUPS.computeIfAbsent(id, GroupView::new);

        g.name = (name == null || name.isBlank()) ? "New Group" : name;
        g.started = started;
        g.activeIndex = Math.max(0, activeIndex);

        g.order = (members == null) ? List.of() : List.copyOf(members);

        if (memberNames == null) {
            g.memberNames = List.of();
        } else {
            // defensive copy + null-safe strings
            var copy = new ArrayList<String>(memberNames.size());
            for (String s : memberNames) copy.add(s == null ? "" : s);
            g.memberNames = List.copyOf(copy);
        }

        g.dead = (dead == null) ? Set.of() : new HashSet<>(dead);

        // keep name map synced too
        GROUP_NAMES.put(id, g.name);
    }

    public static void onGroupRemoved(@Nullable UUID id) {
        if (id == null) return;
        GROUPS.remove(id);
        GROUP_NAMES.remove(id);
        GROUP_LIST.removeIf(e -> e != null && id.equals(e.id()));
    }

    // ---------------- helpers for the screen ----------------

    public static @Nullable UUID getGroupIdFor(@Nullable BlockPos pos) {
        if (pos == null) return null;

        CompoundTag st = get(pos);
        if (st == null) return null;

        // NbtCompound.contains(String) is safe now because st != null
        if (!st.contains("GroupId")) return null;

        String gid = st.getString("GroupId").orElse("");
        if (gid == null || gid.isBlank()) return null;

        try { return UUID.fromString(gid); }
        catch (Exception ignored) { return null; }
    }

    public static @Nullable GroupView getGroup(@Nullable UUID id) {
        if (id == null) return null;
        return GROUPS.get(id);
    }

    public static String groupName(@Nullable UUID id) {
        if (id == null) return "New Group";
        String n = GROUP_NAMES.get(id);
        return (n == null || n.isBlank()) ? "New Group" : n;
    }

    /**
     * Returns the display name for a group member aligned to the snapshot order.
     * If unavailable, returns null (caller can fall back to per-block DisplayName or coords).
     */
    public static @Nullable String groupMemberName(@Nullable UUID groupId, @Nullable BlockPos pos) {
        if (groupId == null || pos == null) return null;

        var g = GROUPS.get(groupId);
        if (g == null || g.order == null || g.order.isEmpty()) return null;

        int idx = g.order.indexOf(pos);
        if (idx < 0) return null;

        if (g.memberNames != null && idx < g.memberNames.size()) {
            String nm = g.memberNames.get(idx);
            if (nm != null && !nm.isBlank()) return nm;
        }
        return null;
    }

    public static List<LifePointPackets.GroupsListS2C.Entry> groupsList() {
        return List.copyOf(GROUP_LIST);
    }

    // ---------------- appearance helpers (Edit modal) ----------------
    // These read from the synced NBT (LifePointPackets.toNbt)

    public static int getPlayerColor(@Nullable BlockPos pos) {
        return safe(pos).getInt("PlayerColor").orElse(0xE8E8E8);
    }

    public static int getLifeColor(@Nullable BlockPos pos) {
        return safe(pos).getInt("LifeColor").orElse(0xFFFFFF);
    }

    public static String getIconKey(@Nullable BlockPos pos) {
        String s = safe(pos).getString("IconKey").orElse("none");
        if (s == null || s.isBlank()) return "none";
        return s;
    }

    public static String getFormatKey(@Nullable BlockPos pos) {
        String s = safe(pos).getString("FormatKey").orElse("commander");
        if (s == null || s.isBlank()) return "commander";
        return s;
    }

    public static int getCommanderLethal(@Nullable BlockPos pos) {
        return safe(pos).getInt("CmdLethal").orElse(21);
    }

    // ---------------- optional generic getters ----------------

    public static String getString(@Nullable BlockPos pos, String key, String def) {
        if (key == null || key.isBlank()) return def;
        String v = safe(pos).getString(key).orElse(def);
        return (v == null) ? def : v;
    }

    public static int getInt(@Nullable BlockPos pos, String key, int def) {
        if (key == null || key.isBlank()) return def;
        return safe(pos).getInt(key).orElse(def);
    }

    public static boolean getBool(@Nullable BlockPos pos, String key, boolean def) {
        if (key == null || key.isBlank()) return def;
        return safe(pos).getBoolean(key).orElse(def);
    }

    public static boolean groupMemberDead(@Nullable UUID groupId, @Nullable BlockPos pos) {
        if (groupId == null || pos == null) return false;
        var g = GROUPS.get(groupId);
        if (g == null || g.dead == null || g.dead.isEmpty()) return false;
        return g.dead.contains(pos);
    }
}
