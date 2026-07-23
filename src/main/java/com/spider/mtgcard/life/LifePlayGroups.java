package com.spider.mtgcard.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * World-saved play groups using PersistentState (1.21.11 style).
 * Named groups + member order + active turn tracking.
 */
public final class LifePlayGroups {
    private static final String KEY = "mtgcard_life_play_groups";

    public static final class Group {
        public UUID id;
        public String name = "New Group";

        public boolean rolling = false;
        public int rollTicksLeft = 0;
        public int rollStepDelay = 1;
        public int rollDelayCounter = 0;
        public int rollMinSteps = 0;
        public int rollStepsDone = 0;
        public int rollTargetIndex = -1;

        public final List<BlockPos> order = new ArrayList<>();
        public int activeIndex = -1;

        public boolean started = false;
        public final Set<BlockPos> dead = new HashSet<>();

        public Group(UUID id) { this.id = id; }
    }

    private static List<String> resolveMemberNames(ServerLevel world, List<BlockPos> order) {
        var names = new ArrayList<String>(order.size());
        for (var bp : order) {
            String nm = "";
            var be = world.getBlockEntity(bp);
            if (be instanceof LifePointBlockEntity lp) nm = lp.getDisplayName();
            if (nm == null || nm.isBlank()) nm = shortPos(bp);
            names.add(nm);
        }
        return names;
    }


    public static final class State extends SavedData {
        private final Map<UUID, Group> groups = new HashMap<>();
        public Map<UUID, Group> groups() { return groups; }

        public CompoundTag toNbt() {
            CompoundTag nbt = new CompoundTag();
            ListTag list = new ListTag();

            for (Group g : groups.values()) {
                CompoundTag gc = new CompoundTag();
                gc.putString("Id", g.id.toString());
                gc.putString("Name", g.name == null ? "" : g.name);

                gc.putInt("ActiveIndex", g.activeIndex);
                gc.putBoolean("Started", g.started);

                // members
                ListTag members = new ListTag();
                for (BlockPos p : g.order) {
                    CompoundTag pc = new CompoundTag();
                    pc.putInt("X", p.getX());
                    pc.putInt("Y", p.getY());
                    pc.putInt("Z", p.getZ());
                    members.add(pc);
                }
                gc.put("Members", members);

                // dead
                ListTag deadList = new ListTag();
                for (BlockPos p : g.dead) {
                    CompoundTag pc = new CompoundTag();
                    pc.putInt("X", p.getX());
                    pc.putInt("Y", p.getY());
                    pc.putInt("Z", p.getZ());
                    deadList.add(pc);
                }
                gc.put("Dead", deadList);

                list.add(gc);
            }

            nbt.put("Groups", list);
            return nbt;
        }

        public static State fromNbtCompound(CompoundTag nbt) {
            State s = new State();

            Optional<ListTag> listOpt = nbt.getList("Groups");
            if (listOpt.isEmpty()) return s;

            ListTag list = listOpt.get();
            for (int i = 0; i < list.size(); i++) {
                Tag e = list.get(i);
                if (!(e instanceof CompoundTag gc)) continue;

                String idStr = gc.getString("Id").orElse("");
                if (idStr.isEmpty()) continue;

                UUID id;
                try { id = UUID.fromString(idStr); }
                catch (Exception ex) { continue; }

                Group g = new Group(id);
                g.name = gc.getString("Name").orElse("New Group");
                if (g.name == null || g.name.isBlank()) g.name = "New Group";

                g.activeIndex = gc.getInt("ActiveIndex").orElse(-1);
                g.started = gc.getBoolean("Started").orElse(false);

                // members
                Optional<ListTag> memOpt = gc.getList("Members");
                if (memOpt.isPresent()) {
                    ListTag members = memOpt.get();
                    for (int mi = 0; mi < members.size(); mi++) {
                        Tag me = members.get(mi);
                        if (!(me instanceof CompoundTag pc)) continue;

                        int x = pc.getInt("X").orElse(0);
                        int y = pc.getInt("Y").orElse(0);
                        int z = pc.getInt("Z").orElse(0);
                        g.order.add(new BlockPos(x, y, z));
                    }
                }

                // dead
                Optional<ListTag> deadOpt = gc.getList("Dead");
                if (deadOpt.isPresent()) {
                    ListTag deadList = deadOpt.get();
                    for (int di = 0; di < deadList.size(); di++) {
                        Tag de = deadList.get(di);
                        if (!(de instanceof CompoundTag pc)) continue;

                        int x = pc.getInt("X").orElse(0);
                        int y = pc.getInt("Y").orElse(0);
                        int z = pc.getInt("Z").orElse(0);
                        g.dead.add(new BlockPos(x, y, z));
                    }
                }

                s.groups.put(id, g);
            }

            return s;
        }
    }

    private static final Codec<State> CODEC = Codec.PASSTHROUGH.xmap(
            dyn -> {
                Dynamic<?> nd = dyn.convert(NbtOps.INSTANCE);
                Object v = nd.getValue();
                if (v instanceof CompoundTag nbt) return State.fromNbtCompound(nbt);
                if (v instanceof Tag el && el instanceof CompoundTag nbt) return State.fromNbtCompound(nbt);
                return new State();
            },
            st -> new Dynamic<>(NbtOps.INSTANCE, st.toNbt())
    );

    private static final net.minecraft.world.level.saveddata.SavedDataType<State> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    KEY,
                    State::new,
                    CODEC,
                    null
            );

    private static State get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---------------- operations ----------------

    /** Returns a unique "New Group", "New Group (1)", "New Group (2)"... */
    private static String uniqueNewGroupName(State st) {
        String base = "New Group";
        boolean hasBase = st.groups().values().stream().anyMatch(g -> base.equalsIgnoreCase(g.name));
        if (!hasBase) return base;

        int i = 1;
        while (true) {
            String cand = base + " (" + i + ")";
            final String c = cand;
            boolean exists = st.groups().values().stream().anyMatch(g -> c.equalsIgnoreCase(g.name));
            if (!exists) return cand;
            i++;
        }
    }

    public static UUID createEmptyGroup(ServerLevel world) {
        State st = get(world);
        UUID id = UUID.randomUUID();

        Group g = new Group(id);
        g.name = uniqueNewGroupName(st);

        st.groups().put(id, g);
        st.setDirty();

        // send list update + snapshot
        LifePointPackets.groupsList(world, buildGroupsList(st));
        broadcastGroup(world, g);
        return id;
    }

    /** Update a group name + members/order. Removes those members from other groups first. */
    public static void saveGroup(ServerLevel world, UUID groupId, String newName, List<BlockPos> newOrder) {
        State st = get(world);
        Group g = st.groups().get(groupId);
        if (g == null) return;

        // normalize name (allow empty, but store a friendly default)
        String name = (newName == null) ? "" : newName.trim();
        if (name.isEmpty()) name = "New Group";
        g.name = name;

        // remove these members from ANY other group
        for (BlockPos p : newOrder) {
            dropGroupsContaining(world, p, groupId);
        }

        // clear previous members' BE links (those removed from this group)
        Set<BlockPos> prev = new HashSet<>(g.order);

        g.order.clear();
        g.order.addAll(newOrder);
        g.dead.retainAll(new HashSet<>(newOrder));

        // if previous members no longer in this group, clear them
        for (BlockPos old : prev) {
            if (!g.order.contains(old)) {
                var be = world.getBlockEntity(old);
                if (be instanceof LifePointBlockEntity lp) lp.clearGroup();
            }
        }

        // keep started flag as-is; fix active index if needed
        if (g.order.isEmpty()) {
            g.activeIndex = -1;
            g.started = false;
        } else {
            if (g.activeIndex >= g.order.size()) g.activeIndex = -1;
            if (g.started) {
                if (g.dead.size() >= g.order.size()) {
                    g.activeIndex = -1;
                } else if (g.activeIndex < 0 || g.dead.contains(g.order.get(g.activeIndex))) {
                    g.activeIndex = firstAliveIndex(g);
                }
            } else {
                g.activeIndex = -1;
            }
        }

        st.setDirty();

        applyGroupToMembers(world, g);
        LifePointPackets.groupsList(world, buildGroupsList(st));
        broadcastGroup(world, g);
    }

    public static void deleteGroup(ServerLevel world, UUID groupId) {
        State st = get(world);
        Group g = st.groups().remove(groupId);
        if (g == null) return;

        // clear all members
        for (BlockPos p : g.order) {
            var be = world.getBlockEntity(p);
            if (be instanceof LifePointBlockEntity lp) lp.clearGroup();
        }

        st.setDirty();
        LifePointPackets.groupRemoved(world, groupId);
        LifePointPackets.groupsList(world, buildGroupsList(st));
    }

    /** Remove member from its current group (used for block break safety). */
    public static void removeFromGroup(ServerLevel world, BlockPos member) {
        State st = get(world);

        Group g = null;
        UUID gid = null;
        for (var e : st.groups().entrySet()) {
            if (e.getValue().order.contains(member)) { gid = e.getKey(); g = e.getValue(); break; }
        }
        if (g == null || gid == null) return;

        removeMemberInternal(world, st, gid, g, member);
        st.setDirty();

        LifePointPackets.groupsList(world, buildGroupsList(st));
    }

    /**
     * Removes member from ANY groups it appears in.
     * If excludeGroup != null, it will NOT remove from that group (used when saving).
     */
    public static void dropGroupsContaining(ServerLevel world, BlockPos member, UUID excludeGroup) {
        State st = get(world);

        boolean changed = false;
        Iterator<Map.Entry<UUID, Group>> it = st.groups().entrySet().iterator();

        while (it.hasNext()) {
            var entry = it.next();
            UUID gid = entry.getKey();
            Group g = entry.getValue();

            if (excludeGroup != null && excludeGroup.equals(gid)) continue;
            if (!g.order.contains(member)) continue;

            removeMemberInternal(world, st, gid, g, member);

            changed = true;
            if (g.order.isEmpty()) {
                it.remove();
                LifePointPackets.groupRemoved(world, gid);
            }
        }

        if (changed) {
            st.setDirty();
            LifePointPackets.groupsList(world, buildGroupsList(st));
        }
    }

    /** Convenience for block break: remove from any group(s). */
    public static void dropGroupsContaining(ServerLevel world, BlockPos member) {
        dropGroupsContaining(world, member, null);
    }

    public static void startGame(ServerLevel world, UUID groupId) {
        State st = get(world);
        Group g = st.groups().get(groupId);
        if (g == null || g.order.isEmpty()) return;

        // if already started or already rolling, ignore
        if (g.started || g.rolling) return;

        // if everybody dead, do nothing meaningful
        if (g.dead.size() >= g.order.size()) {
            g.started = true;
            g.activeIndex = -1;
            st.setDirty();
            applyGroupToMembers(world, g);
            broadcastGroup(world, g);
            return;
        }

        applyStartingLifeForGroup(world, g);

        // Start roulette
        g.started = true;
        g.rolling = true;

        // pick a random alive target to land on
        g.rollTargetIndex = pickRandomAliveIndex(world, g);

        // seed starting position (first alive if none)
        if (g.activeIndex < 0) g.activeIndex = firstAliveTurnIndex(world, g);
        if (g.activeIndex < 0) g.activeIndex = 0;

        // 1–2 seconds total-ish:
        // 1–2 seconds total-ish:
        g.rollTicksLeft = 100;       // was 30 (now 45 = ~2.25s)
        g.rollStepDelay = 1;        // starts fast
        g.rollDelayCounter = 0;
        g.rollStepsDone = 0;
        g.rollMinSteps = 20;        // was 10 (spin a bit longer)


        st.setDirty();
        applyGroupToMembers(world, g);
        broadcastGroup(world, g);
    }


    public static void passTurn(ServerLevel world, BlockPos fromMember) {
        State st = get(world);
        Group g = findGroup(st, fromMember);
        if (g == null || g.order.isEmpty()) return;

        if (g.dead.size() >= g.order.size()) {
            g.activeIndex = -1;
            st.setDirty();
            applyGroupToMembers(world, g);
            broadcastGroup(world, g);
            return;
        }

        if (g.activeIndex < 0) g.activeIndex = firstAliveTurnIndex(world, g);
        advanceToNextAlive(world, g);

        st.setDirty();
        applyGroupToMembers(world, g);
        broadcastGroup(world, g);
    }

    public static void setDead(ServerLevel world, BlockPos member, boolean dead) {
        State st = get(world);
        Group g = findGroup(st, member);
        if (g == null) return;

        if (dead) g.dead.add(member);
        else g.dead.remove(member);

        if (g.dead.size() >= g.order.size()) {
            g.activeIndex = -1;
        } else {
            if (g.activeIndex < 0) g.activeIndex = firstAliveTurnIndex(world, g);
            if (g.activeIndex >= 0 && g.activeIndex < g.order.size()) {
                if (!activeTurnHasAlive(world, g)) {
                    advanceToNextAlive(world, g);
                } else if (hasTeamTurns(world, g)) {
                    g.activeIndex = teamStartIndex(g.activeIndex, turnGroupSize(world, g));
                }
            } else {
                g.activeIndex = firstAliveTurnIndex(world, g);
            }
        }

        st.setDirty();
        applyGroupToMembers(world, g);
        broadcastGroup(world, g);
    }

    public static UUID findGroupId(ServerLevel world, BlockPos member) {
        State st = get(world);
        for (Group g : st.groups().values()) {
            if (g.order.contains(member)) return g.id;
        }
        return null;
    }

    public static Group getGroup(ServerLevel world, UUID id) {
        return get(world).groups().get(id);
    }

    public static List<LifePointPackets.GroupsListS2C.Entry> getGroupsList(ServerLevel world) {
        return buildGroupsList(get(world));
    }

    // ---------------- scanning ----------------

    /** Finds LifePoint blocks near origin. */
    public static List<BlockPos> scan(ServerLevel world, BlockPos origin, int radius) {
        int r = Math.max(1, Math.min(radius, 96));
        int r2 = r * r;

        var out = new ArrayList<BlockPos>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx*dx + dy*dy + dz*dz;
                    if (d2 > r2) continue;

                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    // ✅ final absolute safety check
                    if (m.distSqr(origin) > r2) continue;

                    var be = world.getBlockEntity(m);
                    if (be instanceof LifePointBlockEntity) out.add(m.immutable());
                }
            }
        }

        out.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return out;
    }

    public static void resetGame(ServerLevel world, UUID groupId) {
        State st = get(world);
        Group g = st.groups().get(groupId);
        if (g == null) return;

        LifeFormat format = formatForGroup(world, g);

        g.started = false;
        g.activeIndex = -1;
        g.dead.clear();

        for (BlockPos p : g.order) {
            var be = world.getBlockEntity(p);
            if (be instanceof LifePointBlockEntity lp) {
                lp.setLife(format.startingLife());

                // reset all counters to 0 (keep keys)
                lp.resetCountersToZero();

                // clear commander damage
                lp.clearCommanderDamage();

                lp.setTurnActive(false);
            }
        }

        st.setDirty();
        applyGroupToMembers(world, g);
        broadcastGroup(world, g);
    }

    public static boolean canChangeFormat(ServerLevel world, BlockPos member) {
        State st = get(world);
        Group g = findGroup(st, member);
        return g == null || (!g.started && !g.rolling);
    }

    public static void applyFormatSelection(ServerLevel world, BlockPos member, String formatKey) {
        if (world == null || member == null) return;

        LifeFormat format = LifeFormat.fromKey(formatKey);
        State st = get(world);
        Group g = findGroup(st, member);

        if (g != null && (g.started || g.rolling)) return;

        if (g == null || g.order.isEmpty()) {
            applyFormatToMember(world, member, format, true);
            return;
        }

        for (BlockPos p : g.order) {
            applyFormatToMember(world, p, format, true);
        }

        st.setDirty();
        broadcastGroup(world, g);
    }

    public static void setLife(ServerLevel world, BlockPos member, int value) {
        for (BlockPos target : lifeChangeTargets(world, member)) {
            var be = world.getBlockEntity(target);
            if (be instanceof LifePointBlockEntity lp) lp.setLife(value);
        }
    }

    public static void addLife(ServerLevel world, BlockPos member, int delta) {
        if (delta == 0) return;

        for (BlockPos target : lifeChangeTargets(world, member)) {
            var be = world.getBlockEntity(target);
            if (be instanceof LifePointBlockEntity lp) lp.addLife(delta);
        }
    }

    private static void applyStartingLifeForGroup(ServerLevel world, Group g) {
        LifeFormat format = formatForGroup(world, g);
        for (BlockPos p : g.order) {
            applyFormatToMember(world, p, format, true);
        }
    }

    private static void applyFormatToMember(ServerLevel world, BlockPos member, LifeFormat format, boolean resetLife) {
        var be = world.getBlockEntity(member);
        if (!(be instanceof LifePointBlockEntity lp)) return;

        lp.setFormatKey(format.key());
        if (resetLife) lp.setLife(format.startingLife());
        if (!lp.getCommanderDamage().isEmpty()) lp.clearCommanderDamage();
    }

    private static LifeFormat formatForGroup(ServerLevel world, Group g) {
        if (g == null || g.order.isEmpty()) return LifeFormat.DEFAULT;

        for (BlockPos p : g.order) {
            var be = world.getBlockEntity(p);
            if (be instanceof LifePointBlockEntity lp) {
                return lp.getLifeFormat();
            }
        }

        return LifeFormat.DEFAULT;
    }

    private static List<BlockPos> lifeChangeTargets(ServerLevel world, BlockPos member) {
        if (world == null || member == null) return List.of();

        var be = world.getBlockEntity(member);
        if (!(be instanceof LifePointBlockEntity lp)) return List.of(member);
        int teamSize = lp.getLifeFormat().turnGroupSize();
        if (teamSize <= 1) return List.of(member);

        State st = get(world);
        Group g = findGroup(st, member);
        if (g == null || g.order == null || g.order.isEmpty()) return List.of(member);

        int idx = g.order.indexOf(member);
        if (idx < 0) return List.of(member);

        int teamStart = teamStartIndex(idx, teamSize);
        var out = new ArrayList<BlockPos>(teamSize);
        for (int i = teamStart; i < teamStart + teamSize && i < g.order.size(); i++) {
            BlockPos target = g.order.get(i);
            if (!out.contains(target)) out.add(target);
        }

        return List.copyOf(out);
    }



    /** Scan + include each block's DisplayName. */
    public static List<LifePointPackets.NearbyResultS2C.Entry> scanNamed(ServerLevel world, BlockPos origin, int radius) {
        List<BlockPos> positions = scan(world, origin, radius);
        var out = new ArrayList<LifePointPackets.NearbyResultS2C.Entry>(positions.size());

        for (BlockPos p : positions) {
            String name = "";
            var be = world.getBlockEntity(p);
            if (be instanceof LifePointBlockEntity lp) name = lp.getDisplayName();
            if (name == null || name.isBlank()) name = shortPos(p);
            out.add(new LifePointPackets.NearbyResultS2C.Entry(p, name));
        }
        return out;
    }

    // ---------------- internals ----------------

    private static Group findGroup(State st, BlockPos member) {
        for (Group gg : st.groups().values()) {
            if (gg.order.contains(member)) return gg;
        }
        return null;
    }

    private static int firstAliveIndex(Group g) {
        for (int i = 0; i < g.order.size(); i++) {
            if (!g.dead.contains(g.order.get(i))) return i;
        }
        return -1;
    }

    private static int firstAliveTurnIndex(ServerLevel world, Group g) {
        int teamSize = turnGroupSize(world, g);
        if (teamSize <= 1) return firstAliveIndex(g);

        for (int i = 0; i < g.order.size(); i += teamSize) {
            if (teamHasAlive(g, i, teamSize)) return i;
        }

        return -1;
    }

    private static int pickRandomAliveIndex(ServerLevel world, Group g) {
        if (g.order.isEmpty()) return -1;
        if (g.dead.size() >= g.order.size()) return -1;

        int teamSize = turnGroupSize(world, g);
        if (teamSize > 1) {
            var aliveTeams = new ArrayList<Integer>();
            for (int i = 0; i < g.order.size(); i += teamSize) {
                if (teamHasAlive(g, i, teamSize)) aliveTeams.add(i);
            }

            if (aliveTeams.isEmpty()) return -1;
            return aliveTeams.get(world.getRandom().nextInt(aliveTeams.size()));
        }

        for (int tries = 0; tries < 256; tries++) {
            int idx = world.getRandom().nextInt(g.order.size());
            if (!g.dead.contains(g.order.get(idx))) return idx;
        }
        return firstAliveIndex(g);
    }

    private static void advanceToNextAlive(ServerLevel world, Group g) {
        if (g.order.isEmpty()) { g.activeIndex = -1; return; }

        if (g.dead.size() >= g.order.size()) {
            g.activeIndex = -1;
            return;
        }

        int teamSize = turnGroupSize(world, g);
        if (teamSize > 1) {
            int teamCount = (g.order.size() + teamSize - 1) / teamSize;
            int activeStart = teamStartIndex(g.activeIndex, teamSize);
            int activeTeam = activeStart < 0 ? -1 : activeStart / teamSize;

            for (int step = 1; step <= teamCount; step++) {
                int team = Math.floorMod(activeTeam + step, teamCount);
                int idx = team * teamSize;
                if (teamHasAlive(g, idx, teamSize)) {
                    g.activeIndex = idx;
                    return;
                }
            }

            g.activeIndex = -1;
            return;
        }

        int start = g.activeIndex < 0 ? 0 : g.activeIndex;
        for (int step = 1; step <= g.order.size(); step++) {
            int idx = (start + step) % g.order.size();
            BlockPos p = g.order.get(idx);
            if (!g.dead.contains(p)) {
                g.activeIndex = idx;
                return;
            }
        }

        g.activeIndex = -1;
    }

    private static boolean hasTeamTurns(ServerLevel world, Group g) {
        return turnGroupSize(world, g) > 1;
    }

    private static int turnGroupSize(ServerLevel world, Group g) {
        return Math.max(1, formatForGroup(world, g).turnGroupSize());
    }

    private static int teamStartIndex(int index, int teamSize) {
        if (index < 0) return -1;
        int size = Math.max(1, teamSize);
        return index - (index % size);
    }

    private static boolean teamHasAlive(Group g, int teamStart, int teamSize) {
        if (g == null || teamStart < 0 || teamStart >= g.order.size()) return false;

        int size = Math.max(1, teamSize);
        for (int i = teamStart; i < teamStart + size && i < g.order.size(); i++) {
            if (!g.dead.contains(g.order.get(i))) return true;
        }

        return false;
    }

    private static boolean activeTurnHasAlive(ServerLevel world, Group g) {
        if (g == null || g.activeIndex < 0 || g.activeIndex >= g.order.size()) return false;

        int teamSize = turnGroupSize(world, g);
        if (teamSize > 1) {
            return teamHasAlive(g, teamStartIndex(g.activeIndex, teamSize), teamSize);
        }

        return !g.dead.contains(g.order.get(g.activeIndex));
    }

    /** Core removal that also handles "if active removed, pass to next". */
    private static void removeMemberInternal(ServerLevel world, State st, UUID gid, Group g, BlockPos member) {
        int removedIndex = g.order.indexOf(member);
        boolean removedWasActive = (removedIndex >= 0 && removedIndex == g.activeIndex);

        g.order.remove(member);
        g.dead.remove(member);

        // clear member BE immediately
        var mbe = world.getBlockEntity(member);
        if (mbe instanceof LifePointBlockEntity lp) lp.clearGroup();

        if (g.order.isEmpty()) {
            st.groups().remove(gid);
            LifePointPackets.groupRemoved(world, gid);
            return;
        }

        // adjust active index after removal
        if (removedIndex >= 0) {
            if (g.activeIndex > removedIndex) g.activeIndex--; // list shifted left
            if (removedWasActive && g.started) {
                // keep game going: active becomes "the next in order"
                if (g.dead.size() >= g.order.size()) {
                    g.activeIndex = -1;
                } else {
                    // activeIndex currently points at the "next" item after removal
                    if (g.activeIndex < 0 || g.activeIndex >= g.order.size()) g.activeIndex = 0;
                    // if next is dead, advance
                    if (!activeTurnHasAlive(world, g)) {
                        advanceToNextAlive(world, g);
                    } else if (hasTeamTurns(world, g)) {
                        g.activeIndex = teamStartIndex(g.activeIndex, turnGroupSize(world, g));
                    }
                }
            }
        }

        if (g.activeIndex >= g.order.size()) g.activeIndex = -1;

        applyGroupToMembers(world, g);
        broadcastGroup(world, g);
    }

    private static void applyGroupToMembers(ServerLevel world, Group g) {
        LifeFormat format = formatForGroup(world, g);
        int teamSize = Math.max(1, format.turnGroupSize());
        int activeTeamStart = teamSize > 1 ? teamStartIndex(g.activeIndex, teamSize) : -1;

        for (int i = 0; i < g.order.size(); i++) {
            BlockPos p = g.order.get(i);
            var be = world.getBlockEntity(p);
            if (be instanceof LifePointBlockEntity lp) {
                lp.setGroup(g.id, i);
                lp.setGameStarted(g.started);
                boolean active = teamSize > 1
                        ? activeTeamStart >= 0 && i >= activeTeamStart && i < activeTeamStart + teamSize
                        : i == g.activeIndex;
                lp.setTurnActive(active);
            }
        }
    }

    private static List<LifePointPackets.GroupsListS2C.Entry> buildGroupsList(State st) {
        var list = new ArrayList<LifePointPackets.GroupsListS2C.Entry>();
        for (Group g : st.groups().values()) {
            String nm = (g.name == null || g.name.isBlank()) ? "New Group" : g.name;
            list.add(new LifePointPackets.GroupsListS2C.Entry(g.id, nm));
        }
        list.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return list;
    }

    private static void broadcastGroup(ServerLevel world, Group g) {
        var members = List.copyOf(g.order);
        var names   = resolveMemberNames(world, members);

        LifePointPackets.groupSnapshot(
                world,
                g.id,
                (g.name == null || g.name.isBlank()) ? "New Group" : g.name,
                g.started,
                g.activeIndex,
                members,
                names,                    // ✅ memberNames
                new ArrayList<>(g.dead)
        );
    }


    public static void tickWorld(ServerLevel world) {
        State st = get(world);
        boolean changed = false;

        for (Group g : st.groups().values()) {
            if (!g.rolling) continue;
            if (g.order.isEmpty()) { g.rolling = false; continue; }

            // countdown overall time
            g.rollTicksLeft--;
            if (g.rollTicksLeft < 0) g.rollTicksLeft = 0;

            // delay counter controls "slowdown"
            g.rollDelayCounter++;
            if (g.rollDelayCounter < g.rollStepDelay) continue;
            g.rollDelayCounter = 0;

            // advance active index to next alive
            advanceToNextAlive(world, g);
            g.rollStepsDone++;
            changed = true;

            // slow down over time: increase delay as we near the end
            // (fast early, slower late)
            // slow down over time: increase delay as we near the end
            if (g.rollTicksLeft <= 30 && g.rollStepDelay < 2) g.rollStepDelay = 2;
            if (g.rollTicksLeft <= 18 && g.rollStepDelay < 3) g.rollStepDelay = 3;
            if (g.rollTicksLeft <= 10 && g.rollStepDelay < 4) g.rollStepDelay = 4;
            if (g.rollTicksLeft <= 4  && g.rollStepDelay < 5) g.rollStepDelay = 5;



            // stop condition:
            // must have spun a bit, AND either time is up OR we've reached target during slow phase
            boolean atTarget = (g.activeIndex == g.rollTargetIndex);
            boolean slowPhase = (g.rollTicksLeft <= 12);

            if (g.rollStepsDone >= g.rollMinSteps && (g.rollTicksLeft == 0 || (slowPhase && atTarget))) {
                g.rolling = false;
                // ensure activeIndex is alive (should be)
                if (!activeTurnHasAlive(world, g)) g.activeIndex = firstAliveTurnIndex(world, g);
                else if (hasTeamTurns(world, g)) g.activeIndex = teamStartIndex(g.activeIndex, turnGroupSize(world, g));
            }
        }

        if (changed) {
            st.setDirty();
            // push visuals to players: BE turnActive updates + group snapshot
            for (Group g : st.groups().values()) {
                if (!g.started) continue;
                applyGroupToMembers(world, g);
                broadcastGroup(world, g);
            }
        }
    }


    /** Called when a LifePoint block is broken/removed. Safely removes it from groups. */
    public static void onMemberBroken(ServerLevel world, BlockPos pos) {
        dropGroupsContaining(world, pos);
    }

    private static String shortPos(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private LifePlayGroups() {}
}
