package com.spider.mtgcard.life;

import com.spider.mtgcard.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * LifePointBlockEntity
 * - Stores life + counters + appearance + group/turn state
 * - Server authoritative; sync() broadcasts to tracking players
 * - No max-caps on life/counters (overflow-safe adds)
 */
public class LifePointBlockEntity extends BlockEntity {

    // ----- identity / display -----
    private String displayName = "";
    private boolean nameInitialized = false;

    // ----- core life -----
    private int life = 40;
    private int lifeColor = 0xFFFFFF;

    // ----- appearance -----
    private int playerColor = 0xE8E8E8;       // default light gray
    private String iconKey = "none";
    private String formatKey = "commander";   // "commander" or "standard"
    private int commanderLethal = 21;         // keep >= 1

    // ----- counters -----
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final Map<String, String> counterIcons = new HashMap<>();

    // ----- groups / turn system -----
    private UUID groupId = null;
    private int groupOrderIndex = -1;
    private boolean turnActive = false;
    private boolean lastPowered = false;
    private boolean gameStarted = false;

    // Commander Damage (key = "x,y,z" of source commander block)
    private final Long2IntOpenHashMap commanderDamage = new Long2IntOpenHashMap();

    // Marker entity UUID
    private UUID glowEntityId = null;

    private int iconSwapColor = 0xFF0000;

    public int getIconSwapColor() { return iconSwapColor; }

    public void setIconSwapColor(int rgb) {
        this.iconSwapColor = rgb & 0xFFFFFF;
    }

    public LifePointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIFE_POINT, pos, state);

        // core counters (always exist)
        counters.put("poison", 0);
        counters.put("experience", 0);
        counters.put("energy", 0);

        // core counter icons (locked)
        counterIcons.put("poison", "poison");
        counterIcons.put("experience", "experience");
        counterIcons.put("energy", "energy");
    }

    public void unlinkAllDisplaysOnBreak(ServerWorld world) {
        if (world == null) return;

        // copy first so we don't mutate while iterating
        var displays = getLinkedDisplaysCopy();

        for (BlockPos dpos : displays) {
            if (dpos == null) continue;
            if (!world.isChunkLoaded(dpos)) continue;

            var dbe = world.getBlockEntity(dpos);
            if (dbe instanceof com.spider.mtgcard.displayblock.DisplayBlockEntity display) {
                // only clear if it is actually pointing at THIS life block
                boolean matches =
                        display.getLinkedDimId().map(world.getRegistryKey().getValue()::equals).orElse(false) &&
                                display.getLinkedLifePos().map(this.pos::equals).orElse(false);

                if (matches) display.clearLink();
            }
        }

        clearLinkedDisplays();
    }

    // LifePointBlockEntity.java (fields)
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet linkedDisplays =
            new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public void addLinkedDisplay(BlockPos displayPos) {
        if (displayPos == null) return;
        if (linkedDisplays.add(displayPos.asLong())) {
            markDirty();
        }
    }

    public void removeLinkedDisplay(BlockPos displayPos) {
        if (displayPos == null) return;
        if (linkedDisplays.remove(displayPos.asLong())) {
            markDirty();
        }
    }

    public java.util.List<BlockPos> getLinkedDisplaysCopy() {
        var out = new java.util.ArrayList<BlockPos>(linkedDisplays.size());
        for (long l : linkedDisplays) out.add(BlockPos.fromLong(l));
        return out;
    }

    public void clearLinkedDisplays() {
        if (!linkedDisplays.isEmpty()) {
            linkedDisplays.clear();
            markDirty();
        }
    }

    // -------------------- getters --------------------

    public String getDisplayName() { return displayName; }
    public int getLife() { return life; }
    public int getLifeColor() { return lifeColor; }

    public int getPlayerColor() { return playerColor; }
    public String getIconKey() { return iconKey; }
    public String getFormatKey() { return formatKey; }
    public int getCommanderLethal() { return commanderLethal; }

    public Map<String, Integer> getCounters() { return counters; }
    public Map<String, String> getCounterIcons() { return counterIcons; }
    public Long2IntMap getCommanderDamage() { return commanderDamage; }


    public UUID getGroupId() { return groupId; }
    public int getGroupOrderIndex() { return groupOrderIndex; }
    public boolean isTurnActive() { return turnActive; }

    public boolean getLastPowered() { return lastPowered; }
    public void setLastPowered(boolean v) { lastPowered = v; markDirty(); }

    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean v) { gameStarted = v; sync(); }

    public UUID getGlowEntityId() { return glowEntityId; }

    // -------------------- setters (syncing) --------------------

    public void setDisplayName(String s) {
        displayName = (s == null ? "" : s);
        nameInitialized = true;
        sync();
    }

    // No cap on life; overflow-safe adds used in addLife()
    public void setLife(int v) { life = v; sync(); }
    public void addLife(int d) { life = safeAddInt(life, d); sync(); }

    public void setLifeColor(int rgb) { lifeColor = rgb & 0xFFFFFF; sync(); }

    public void setPlayerColor(int rgb) { playerColor = rgb & 0xFFFFFF; sync(); }

    public void setIconKey(String k) {
        iconKey = (k == null ? "none" : k.trim());
        if (iconKey.isEmpty()) iconKey = "none";
        sync();
    }

    public void setFormatKey(String k) {
        formatKey = (k == null ? "standard" : k.trim().toLowerCase(Locale.ROOT));
        if (formatKey.isEmpty()) formatKey = "standard";
        sync();
    }

    // Keep >= 1 (no upper cap)
    public void setCommanderLethal(int v) {
        commanderLethal = Math.max(1, v);
        sync();
    }

    public void resetAppearance() {
        playerColor = 0xE8E8E8;
        lifeColor = 0xFFFFFF;
        iconKey = "none";
        formatKey = "commander";
        commanderLethal = 21;
        sync();
    }

    // -------------------- counters --------------------

    public void setCounter(String key, int v) {
        String k = normalizeKey(key);
        if (k == null) return;

        counters.put(k, v);
        ensureCounterIconEntry(k);
        sync();
    }

    public void addCounter(String key, int d) {
        String k = normalizeKey(key);
        if (k == null) return;

        int cur = counters.getOrDefault(k, 0);
        counters.put(k, safeAddInt(cur, d));
        ensureCounterIconEntry(k);
        sync();
    }

    /** Sets the icon key for a counter (stored in NBT as CounterIcons.<counterKey> = <iconKey>). */
    public void setCounterIcon(String counterKey, String icon) {
        String k = normalizeKey(counterKey);
        if (k == null) return;

        // lock defaults
        if (k.equals("poison")) icon = "poison";
        else if (k.equals("energy")) icon = "energy";
        else if (k.equals("experience")) icon = "experience";
        else {
            icon = (icon == null ? "none" : icon.trim().toLowerCase(Locale.ROOT));
            if (icon.isEmpty()) icon = "none";
        }

        counterIcons.put(k, icon);
        counters.putIfAbsent(k, 0);

        markDirty();
        if (world != null && !world.isClient()) {
            LifePointPackets.syncToTracking(world, pos, this);
        }
    }

    public void removeCounter(String key) {
        String k = normalizeKey(key);
        if (k == null) return;

        // Don't allow removal of core counters
        if (k.equals("poison") || k.equals("experience") || k.equals("energy")) return;

        counterIcons.remove(k);
        counters.remove(k);
        sync();
    }

    public void resetCountersToZero() {
        for (var k : new ArrayList<>(counters.keySet())) counters.put(k, 0);
        sync();
    }

    private void ensureCounterIconEntry(String k) {
        // locked defaults
        if (k.equals("poison")) counterIcons.put("poison", "poison");
        else if (k.equals("energy")) counterIcons.put("energy", "energy");
        else if (k.equals("experience")) counterIcons.put("experience", "experience");
        else counterIcons.putIfAbsent(k, "none");
    }

    // -------------------- commander damage --------------------

    public void addCommanderDamage(BlockPos source, int delta) {
        if (source == null || delta == 0) return;

        long k = source.asLong();
        int cur = commanderDamage.get(k);
        int v = safeAddInt(cur, delta);
        if (v < 0) v = 0;

        if (v == 0) commanderDamage.remove(k);
        else commanderDamage.put(k, v);

        sync();
    }

    public void clearCommanderDamage() {
        commanderDamage.clear();
        sync();
    }

    // -------------------- group / turn --------------------

    public void setGroup(UUID id, int orderIndex) {
        groupId = id;
        groupOrderIndex = orderIndex;
        sync();
    }

    public void clearGroup() {
        groupId = null;
        groupOrderIndex = -1;
        setTurnActive(false); // ensures marker removed
        sync();
    }

    public void setTurnActive(boolean v) {
        turnActive = v;

        if (world instanceof ServerWorld sw) {
            if (turnActive) ensureGlowEntity(sw);
            else removeGlowEntity(sw);
        }

        sync();
    }

    // -------------------- tick --------------------

    public static void tick(World world, BlockPos pos, BlockState state, LifePointBlockEntity be) {
        if (world.isClient()) return;

        if (be.firstTick) {
            be.firstTick = false;
            be.sync();
        }

        // Name init (server-authoritative)
        if (!be.nameInitialized) {
            be.nameInitialized = true;

            if (be.displayName == null || be.displayName.isBlank()) {
                int n = world.random.nextInt(10000);
                be.displayName = String.format("%04d", n);
                be.sync(); // push name to clients right away
            }
        }

        // Marker maintenance
        if (world instanceof ServerWorld sw) {
            if (be.turnActive) be.ensureGlowEntity(sw);
            else if (be.glowEntityId != null) be.removeGlowEntity(sw);
        }
    }

    // -------------------- sync --------------------

    public void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            LifePointPackets.syncToTracking(world, pos, this);
            world.updateComparators(pos, getCachedState().getBlock());
        }
    }

    // -------------------- default name helper --------------------

    public void ensureDefaultName(ServerWorld world) {
        String cur = getDisplayName();
        if (cur != null && !cur.isBlank()) return;

        int n = 1000 + world.random.nextInt(9000);
        setDisplayName(Integer.toString(n));
        markDirty();

        LifePointPackets.syncToTracking(world, getPos(), this);
    }

    // -------------------- marker entity (glow) --------------------

    /**
     * Spawn / maintain marker entity.
     * Uses an invisible glowing shulker so the outline looks blocky (not armor-stand shaped).
     */
    private void ensureGlowEntity(ServerWorld sw) {
        final double size = 0.99D;

        if (glowEntityId != null) {
            Entity e = sw.getEntity(glowEntityId);
            if (e instanceof ShulkerEntity sh) {
                configureGlowShulker(sh, size);
                return;
            }
            glowEntityId = null;
        }

        ShulkerEntity sh = new ShulkerEntity(EntityType.SHULKER, sw);

        sh.refreshPositionAndAngles(
                pos.getX() + 0.5,
                pos.getY() + 0.05,
                pos.getZ() + 0.5,
                0.0f,
                0.0f
        );

        configureGlowShulker(sh, size);

        sh.setPersistent();
        sw.spawnEntity(sh);

        glowEntityId = sh.getUuid();
        markDirty();
    }

    private static void configureGlowShulker(ShulkerEntity sh, double size) {
        sh.setSilent(true);
        sh.setInvulnerable(true);
        sh.setNoGravity(true);
        sh.setAiDisabled(true);

        sh.setInvisible(true);
        sh.setGlowing(true);

        sh.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                true,
                false
        ));

        var attr = sh.getAttributeInstance(EntityAttributes.SCALE);
        if (attr != null) attr.setBaseValue(size);
    }

    private void removeGlowEntity(ServerWorld sw) {
        if (glowEntityId == null) return;
        Entity e = sw.getEntity(glowEntityId);
        if (e != null) e.discard();
        glowEntityId = null;
        markDirty();
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (world instanceof ServerWorld sw) {
            removeGlowEntity(sw);
        }
    }

    // -------------------- NBT --------------------

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        // appearance
        view.putInt("PlayerColor", playerColor);
        view.putString("IconKey", iconKey == null ? "none" : iconKey);
        view.putString("FormatKey", formatKey == null ? "standard" : formatKey);
        view.putInt("CmdLethal", commanderLethal);

        view.putInt("IconSwapColor", iconSwapColor);

        // core
        view.putInt("Life", life);
        view.putInt("LifeColor", lifeColor);
        view.putBoolean("NameInit", nameInitialized);
        view.putString("DisplayName", displayName == null ? "" : displayName);

        // counters
        if (counters.isEmpty()) {
            view.putString("CounterKeys", "");
        } else {
            view.putString("CounterKeys", String.join(",", counters.keySet()));
            for (var e : counters.entrySet()) {
                view.putInt("Counter_" + e.getKey(), e.getValue());
            }
        }

        var ci = new NbtCompound();
        for (var e : counterIcons.entrySet()) ci.putString(e.getKey(), e.getValue());
        view.put("CounterIcons", NbtCompound.CODEC, ci);

        // commander damage (new long-key format)
        if (commanderDamage.isEmpty()) {
            view.putString("CmdLKeys", "");
        } else {
            // store comma-separated longs (safe, simple, and your style matches CounterKeys)
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            for (long k : commanderDamage.keySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(k);

                view.putInt("CmdL_" + k, commanderDamage.get(k));
            }

            view.putString("CmdLKeys", sb.toString());
        }

        // reverse links (displays pointing at this life block)
        if (linkedDisplays.isEmpty()) {
            view.putString("LinkedDisplayKeys", "");
        } else {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (long l : linkedDisplays) {
                if (!first) sb.append(',');
                first = false;
                sb.append(l);
            }
            view.putString("LinkedDisplayKeys", sb.toString());
        }

        // group/turn
        view.putString("GroupId", groupId != null ? groupId.toString() : "");
        view.putInt("GroupOrderIndex", groupOrderIndex);
        view.putBoolean("TurnActive", turnActive);
        view.putBoolean("LastPowered", lastPowered);

        // marker entity
        view.putString("GlowEntityId", glowEntityId != null ? glowEntityId.toString() : "");

        // game
        view.putBoolean("GameStarted", gameStarted);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);

        // appearance
        playerColor = view.getInt("PlayerColor", 0xE8E8E8);
        iconKey = view.getString("IconKey", "none");
        formatKey = view.getString("FormatKey", "standard");
        commanderLethal = view.getInt("CmdLethal", 21);

        if (iconKey == null || iconKey.isBlank()) iconKey = "none";
        if (formatKey == null || formatKey.isBlank()) formatKey = "standard";
        if (commanderLethal < 1) commanderLethal = 1;

        // core
        displayName = view.getString("DisplayName", "");
        life = view.getInt("Life", 40);
        lifeColor = view.getInt("LifeColor", 0xFFFFFF);
        nameInitialized = view.getBoolean("NameInit", false);
        iconSwapColor = view.getInt("IconSwapColor", 0xFF0000) & 0xFFFFFF;

        // ensure deterministic default name if missing
        if (!nameInitialized) {
            nameInitialized = true;
            if (displayName == null || displayName.isBlank()) {
                int seed = (getPos().getX() * 73428767) ^ (getPos().getZ() * 912931) ^ getPos().getY();
                int n = Math.floorMod(seed, 10000);
                displayName = String.format("%04d", n);
            }
        }

        // counters
        counters.clear();

        String keys = view.getString("CounterKeys", "");
        if (!keys.isEmpty()) {
            var parts = Arrays.stream(keys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            var pref = List.of("poison", "experience", "energy");

            // keep pref order first if present
            for (String k : pref) {
                if (parts.contains(k)) counters.put(k, view.getInt("Counter_" + k, 0));
            }
            // then remaining in saved order
            for (String k : parts) {
                if (!counters.containsKey(k)) counters.put(k, view.getInt("Counter_" + k, 0));
            }
        } else {
            // legacy fallback
            counters.put("poison", view.getInt("Counter_poison", 0));
            counters.put("experience", view.getInt("Counter_experience", 0));
            counters.put("energy", view.getInt("Counter_energy", 0));
        }

        if (counters.isEmpty()) {
            counters.put("poison", 0);
            counters.put("experience", 0);
            counters.put("energy", 0);
        }

        // counter icons
        counterIcons.clear();
        var ci = view.read("CounterIcons", NbtCompound.CODEC).orElse(null);
        if (ci != null) {
            for (String k : ci.getKeys()) {
                String v = ci.getString(k).orElse("none");
                String kk = (k == null ? "" : k.toLowerCase(Locale.ROOT));
                if (!kk.isEmpty()) {
                    counterIcons.put(
                            kk,
                            (v == null ? "none" : v.toLowerCase(Locale.ROOT))
                    );
                }
            }
        }

        // ensure core counter icons always exist + locked
        counterIcons.put("poison", "poison");
        counterIcons.put("experience", "experience");
        counterIcons.put("energy", "energy");

        // ensure every counter has an icon entry
        for (String k : counters.keySet()) {
            if (k.equals("poison") || k.equals("energy") || k.equals("experience")) continue;
            counterIcons.putIfAbsent(k, "none");
        }

        // group
        String gid = view.getString("GroupId", "");
        groupId = null;
        if (!gid.isEmpty()) {
            try { groupId = UUID.fromString(gid); } catch (Exception ignored) {}
        }
        groupOrderIndex = view.getInt("GroupOrderIndex", -1);

        // turn/power
        turnActive = view.getBoolean("TurnActive", false);
        lastPowered = view.getBoolean("LastPowered", false);

        // marker entity
        String glow = view.getString("GlowEntityId", "");
        glowEntityId = null;
        if (!glow.isEmpty()) {
            try { glowEntityId = UUID.fromString(glow); } catch (Exception ignored) {}
        }

        // commander damage
        commanderDamage.clear();

// 1) NEW FORMAT: CmdLKeys = "long,long,long" and each CmdL_<long> = value
        String cmdLKeys = view.getString("CmdLKeys", "");
        if (!cmdLKeys.isEmpty()) {
            var parts = Arrays.stream(cmdLKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            for (String s : parts) {
                try {
                    long k = Long.parseLong(s);
                    int v = view.getInt("CmdL_" + k, 0);
                    if (v < 0) v = 0;
                    if (v > 0) commanderDamage.put(k, v);
                } catch (Exception ignored) {}
            }
        } else {
            // 2) LEGACY FORMAT: CmdKeys = "x,y,z,x,y,z..." + Cmd_<x,y,z> = value
            String cmdKeys = view.getString("CmdKeys", "");
            if (!cmdKeys.isEmpty()) {
                var parts = Arrays.stream(cmdKeys.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                for (String kStr : parts) {
                    int v = view.getInt("Cmd_" + kStr, 0);
                    if (v < 0) v = 0;
                    if (v <= 0) continue;

                    BlockPos bp = parseLegacyPosKey(kStr);
                    if (bp != null) commanderDamage.put(bp.asLong(), v);
                }
            }
        }

        linkedDisplays.clear();
        String ld = view.getString("LinkedDisplayKeys", "");
        if (ld != null && !ld.isEmpty()) {
            var parts = java.util.Arrays.stream(ld.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            for (String s : parts) {
                try { linkedDisplays.add(Long.parseLong(s)); } catch (Exception ignored) {}
            }
        }

        // game
        gameStarted = view.getBoolean("GameStarted", false);

        // NOTE: don't spawn/remove marker here; tick() will reconcile on server.
    }

    public static BlockPos parseLegacyPosKey(String k) {
        if (k == null) return null;
        k = k.trim();
        try {
            String[] p = k.split(",");
            if (p.length == 3) {
                int x = Integer.parseInt(p[0].trim());
                int y = Integer.parseInt(p[1].trim());
                int z = Integer.parseInt(p[2].trim());
                return new BlockPos(x, y, z);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // -------------------- small helpers --------------------

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        return k.isEmpty() ? null : k;
    }

    /** int overflow-safe add */
    private static int safeAddInt(int a, int b) {
        long r = (long) a + (long) b;
        if (r > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (r < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) r;
    }

    public void addCommanderDamageAndAdjustLife(BlockPos source, int delta) {
        if (source == null || delta == 0) return;

        // commander damage update (same logic as addCommanderDamage but WITHOUT sync)
        long k = source.asLong();
        int cur = commanderDamage.get(k);
        int v = safeAddInt(cur, delta);
        if (v < 0) v = 0;

        if (v == 0) commanderDamage.remove(k);
        else commanderDamage.put(k, v);

        // life tracks commander damage
        life = safeAddInt(life, -delta);

        // single sync
        sync();
    }

    private boolean firstTick = true;

}
