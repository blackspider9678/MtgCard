package com.spider.mtgcard.life;

import com.spider.mtgcard.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
    private String formatKey = LifeFormat.DEFAULT.key();
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

    public void unlinkAllDisplaysOnBreak(ServerLevel world) {
        if (world == null) return;

        // copy first so we don't mutate while iterating
        var displays = getLinkedDisplaysCopy();

        for (BlockPos dpos : displays) {
            if (dpos == null) continue;
            if (!world.isLoaded(dpos)) continue;

            var dbe = world.getBlockEntity(dpos);
            if (dbe instanceof com.spider.mtgcard.displayblock.DisplayBlockEntity display) {
                // only clear if it is actually pointing at THIS life block
                boolean matches =
                        display.getLinkedDimId().map(world.dimension().identifier()::equals).orElse(false) &&
                                display.getLinkedLifePos().map(this.worldPosition::equals).orElse(false);

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
            setChanged();
        }
    }

    public void removeLinkedDisplay(BlockPos displayPos) {
        if (displayPos == null) return;
        if (linkedDisplays.remove(displayPos.asLong())) {
            setChanged();
        }
    }

    public java.util.List<BlockPos> getLinkedDisplaysCopy() {
        var out = new java.util.ArrayList<BlockPos>(linkedDisplays.size());
        for (long l : linkedDisplays) out.add(BlockPos.of(l));
        return out;
    }

    public void clearLinkedDisplays() {
        if (!linkedDisplays.isEmpty()) {
            linkedDisplays.clear();
            setChanged();
        }
    }

    // -------------------- getters --------------------

    public String getDisplayName() { return displayName; }
    public int getLife() { return life; }
    public int getLifeColor() { return lifeColor; }

    public int getPlayerColor() { return playerColor; }
    public String getIconKey() { return iconKey; }
    public String getFormatKey() { return formatKey; }
    public LifeFormat getLifeFormat() { return LifeFormat.fromKey(formatKey); }
    public int getCommanderLethal() { return commanderLethal; }

    public Map<String, Integer> getCounters() { return counters; }
    public Map<String, String> getCounterIcons() { return counterIcons; }
    public Long2IntMap getCommanderDamage() { return commanderDamage; }


    public UUID getGroupId() { return groupId; }
    public int getGroupOrderIndex() { return groupOrderIndex; }
    public boolean isTurnActive() { return turnActive; }

    public boolean getLastPowered() { return lastPowered; }
    public void setLastPowered(boolean v) { lastPowered = v; setChanged(); }

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
        formatKey = LifeFormat.normalizeKey(k);
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
        formatKey = LifeFormat.DEFAULT.key();
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

        setChanged();
        if (level != null && !level.isClientSide()) {
            LifePointPackets.syncToTracking(level, worldPosition, this);
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

        if (level instanceof ServerLevel sw) {
            if (turnActive) ensureGlowEntity(sw);
            else removeGlowEntity(sw);
        }

        sync();
    }

    // -------------------- tick --------------------

    public static void tick(Level world, BlockPos pos, BlockState state, LifePointBlockEntity be) {
        if (world.isClientSide()) return;

        if (be.firstTick) {
            be.firstTick = false;
            be.sync();
        }

        // Name init (server-authoritative)
        if (!be.nameInitialized) {
            be.nameInitialized = true;

            if (be.displayName == null || be.displayName.isBlank()) {
                int n = world.getRandom().nextInt(10000);
                be.displayName = String.format("%04d", n);
                be.sync(); // push name to clients right away
            }
        }

        // Marker maintenance
        if (world instanceof ServerLevel sw) {
            if (be.turnActive) be.ensureGlowEntity(sw);
            else if (be.glowEntityId != null) be.removeGlowEntity(sw);
        }
    }

    // -------------------- sync --------------------

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            LifePointPackets.syncToTracking(level, worldPosition, this);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    // -------------------- default name helper --------------------

    public void ensureDefaultName(ServerLevel world) {
        String cur = getDisplayName();
        if (cur != null && !cur.isBlank()) return;

        int n = 1000 + world.getRandom().nextInt(9000);
        setDisplayName(Integer.toString(n));
        setChanged();

        LifePointPackets.syncToTracking(world, getBlockPos(), this);
    }

    // -------------------- marker entity (glow) --------------------

    /**
     * Spawn / maintain marker entity.
     * Uses an invisible glowing shulker so the outline looks blocky (not armor-stand shaped).
     */
    private void ensureGlowEntity(ServerLevel sw) {
        final double size = 0.99D;

        if (glowEntityId != null) {
            Entity e = sw.getEntity(glowEntityId);
            if (e instanceof Shulker sh) {
                configureGlowShulker(sh, size);
                return;
            }
            glowEntityId = null;
        }

        Shulker sh = new Shulker(EntityTypes.SHULKER, sw);

        sh.snapTo(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.05,
                worldPosition.getZ() + 0.5,
                0.0f,
                0.0f
        );

        configureGlowShulker(sh, size);

        sh.setPersistenceRequired();
        sw.addFreshEntity(sh);

        glowEntityId = sh.getUUID();
        setChanged();
    }

    private static void configureGlowShulker(Shulker sh, double size) {
        sh.setSilent(true);
        sh.setInvulnerable(true);
        sh.setNoGravity(true);
        sh.setNoAi(true);

        sh.setInvisible(true);
        sh.setGlowingTag(true);

        sh.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                true,
                false
        ));

        var attr = sh.getAttribute(Attributes.SCALE);
        if (attr != null) attr.setBaseValue(size);
    }

    private void removeGlowEntity(ServerLevel sw) {
        if (glowEntityId == null) return;
        Entity e = sw.getEntity(glowEntityId);
        if (e != null) e.discard();
        glowEntityId = null;
        setChanged();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sw) {
            removeGlowEntity(sw);
        }
    }

    // -------------------- NBT --------------------

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);

        // appearance
        view.putInt("PlayerColor", playerColor);
        view.putString("IconKey", iconKey == null ? "none" : iconKey);
        view.putString("FormatKey", LifeFormat.normalizeKey(formatKey));
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

        var ci = new CompoundTag();
        for (var e : counterIcons.entrySet()) ci.putString(e.getKey(), e.getValue());
        view.store("CounterIcons", CompoundTag.CODEC, ci);

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
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);

        // appearance
        playerColor = view.getIntOr("PlayerColor", 0xE8E8E8);
        iconKey = view.getStringOr("IconKey", "none");
        formatKey = LifeFormat.normalizeKey(view.getStringOr("FormatKey", LifeFormat.DEFAULT.key()));
        commanderLethal = view.getIntOr("CmdLethal", 21);

        if (iconKey == null || iconKey.isBlank()) iconKey = "none";
        if (formatKey == null || formatKey.isBlank()) formatKey = LifeFormat.DEFAULT.key();
        if (commanderLethal < 1) commanderLethal = 1;

        // core
        displayName = view.getStringOr("DisplayName", "");
        life = view.getIntOr("Life", 40);
        lifeColor = view.getIntOr("LifeColor", 0xFFFFFF);
        nameInitialized = view.getBooleanOr("NameInit", false);
        iconSwapColor = view.getIntOr("IconSwapColor", 0xFF0000) & 0xFFFFFF;

        // ensure deterministic default name if missing
        if (!nameInitialized) {
            nameInitialized = true;
            if (displayName == null || displayName.isBlank()) {
                int seed = (getBlockPos().getX() * 73428767) ^ (getBlockPos().getZ() * 912931) ^ getBlockPos().getY();
                int n = Math.floorMod(seed, 10000);
                displayName = String.format("%04d", n);
            }
        }

        // counters
        counters.clear();

        String keys = view.getStringOr("CounterKeys", "");
        if (!keys.isEmpty()) {
            var parts = Arrays.stream(keys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            var pref = List.of("poison", "experience", "energy");

            // keep pref order first if present
            for (String k : pref) {
                if (parts.contains(k)) counters.put(k, view.getIntOr("Counter_" + k, 0));
            }
            // then remaining in saved order
            for (String k : parts) {
                if (!counters.containsKey(k)) counters.put(k, view.getIntOr("Counter_" + k, 0));
            }
        } else {
            // legacy fallback
            counters.put("poison", view.getIntOr("Counter_poison", 0));
            counters.put("experience", view.getIntOr("Counter_experience", 0));
            counters.put("energy", view.getIntOr("Counter_energy", 0));
        }

        if (counters.isEmpty()) {
            counters.put("poison", 0);
            counters.put("experience", 0);
            counters.put("energy", 0);
        }

        // counter icons
        counterIcons.clear();
        var ci = view.read("CounterIcons", CompoundTag.CODEC).orElse(null);
        if (ci != null) {
            for (String k : ci.keySet()) {
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
        String gid = view.getStringOr("GroupId", "");
        groupId = null;
        if (!gid.isEmpty()) {
            try { groupId = UUID.fromString(gid); } catch (Exception ignored) {}
        }
        groupOrderIndex = view.getIntOr("GroupOrderIndex", -1);

        // turn/power
        turnActive = view.getBooleanOr("TurnActive", false);
        lastPowered = view.getBooleanOr("LastPowered", false);

        // marker entity
        String glow = view.getStringOr("GlowEntityId", "");
        glowEntityId = null;
        if (!glow.isEmpty()) {
            try { glowEntityId = UUID.fromString(glow); } catch (Exception ignored) {}
        }

        // commander damage
        commanderDamage.clear();

// 1) NEW FORMAT: CmdLKeys = "long,long,long" and each CmdL_<long> = value
        String cmdLKeys = view.getStringOr("CmdLKeys", "");
        if (!cmdLKeys.isEmpty()) {
            var parts = Arrays.stream(cmdLKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            for (String s : parts) {
                try {
                    long k = Long.parseLong(s);
                    int v = view.getIntOr("CmdL_" + k, 0);
                    if (v < 0) v = 0;
                    if (v > 0) commanderDamage.put(k, v);
                } catch (Exception ignored) {}
            }
        } else {
            // 2) LEGACY FORMAT: CmdKeys = "x,y,z,x,y,z..." + Cmd_<x,y,z> = value
            String cmdKeys = view.getStringOr("CmdKeys", "");
            if (!cmdKeys.isEmpty()) {
                var parts = Arrays.stream(cmdKeys.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                for (String kStr : parts) {
                    int v = view.getIntOr("Cmd_" + kStr, 0);
                    if (v < 0) v = 0;
                    if (v <= 0) continue;

                    BlockPos bp = parseLegacyPosKey(kStr);
                    if (bp != null) commanderDamage.put(bp.asLong(), v);
                }
            }
        }

        linkedDisplays.clear();
        String ld = view.getStringOr("LinkedDisplayKeys", "");
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
        gameStarted = view.getBooleanOr("GameStarted", false);

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
