package com.spider.mtgcard.life;

import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LifePointPackets {

    // ---------------- Presets (server-memory) ----------------
    private static final ConcurrentHashMap<UUID, CompoundTag> PLAYER_PRESET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> PLAYER_PRESET_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompoundTag> PRESET_BY_ID = new ConcurrentHashMap<>();

    private static String genId() {
        // short-ish readable id; collisions extremely unlikely, but handled
        for (int tries = 0; tries < 20; tries++) {
            String s = Long.toString(System.nanoTime(), 36).toUpperCase();
            if (s.length() > 8) s = s.substring(s.length() - 8);
            if (!PRESET_BY_ID.containsKey(s)) return s;
        }
        // fallback
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    // In LifePointPackets.java
    public static final Identifier SET_ICON_SWAP_COLOR_ID =
            Identifier.fromNamespaceAndPath("mtgcard", "life_set_icon_swap_color");

    public record SetIconSwapColorC2S(BlockPos pos, int rgb) implements CustomPacketPayload {
        public static final Type<SetIconSwapColorC2S> ID = new Type<>(SET_ICON_SWAP_COLOR_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, SetIconSwapColorC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetIconSwapColorC2S(buf.readBlockPos(), buf.readInt())
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    private static CompoundTag presetFrom(LifePointBlockEntity lp) {
        var n = new CompoundTag();
        n.putInt("PlayerColor", lp.getPlayerColor());
        n.putInt("LifeColor", lp.getLifeColor());
        n.putString("IconKey", lp.getIconKey());
        n.putString("FormatKey", lp.getFormatKey());
        n.putInt("CmdLethal", lp.getCommanderLethal());
        n.putInt("IconSwapColor", lp.getIconSwapColor());
        return n;
    }

    private static void applyPreset(LifePointBlockEntity lp, CompoundTag n) {
        if (n == null) return;

        int pc = n.getInt("PlayerColor").orElse(lp.getPlayerColor());
        int lc = n.getInt("LifeColor").orElse(lp.getLifeColor());
        String icon = n.getString("IconKey").orElse(lp.getIconKey());
        String fmt  = n.getString("FormatKey").orElse(lp.getFormatKey());
        int lethal  = n.getInt("CmdLethal").orElse(lp.getCommanderLethal());
        int swap = n.getInt("IconSwapColor").orElse(lp.getIconSwapColor());

        lp.setPlayerColor(pc);
        lp.setLifeColor(lc);
        lp.setIconKey(icon);
        if (lp.getLevel() instanceof ServerLevel world) {
            if (LifePlayGroups.canChangeFormat(world, lp.getBlockPos())) {
                LifePlayGroups.applyFormatSelection(world, lp.getBlockPos(), fmt);
            }
        } else {
            lp.setFormatKey(fmt);
        }
        lp.setCommanderLethal(lethal);
        lp.setIconSwapColor(swap);
    }

    // ---------- S2C ----------
    public record OpenLifeScreenPayload(BlockPos pos, CompoundTag state) implements CustomPacketPayload {
        public static final Type<OpenLifeScreenPayload> ID = new Type<>(ModRegistry.id("open_life"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenLifeScreenPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeNbt(p.state()); },
                        (buf) -> new OpenLifeScreenPayload(buf.readBlockPos(), buf.readNbt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SyncLifePayload(BlockPos pos, CompoundTag state) implements CustomPacketPayload {
        public static final Type<SyncLifePayload> ID = new Type<>(ModRegistry.id("sync_life"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncLifePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeNbt(p.state()); },
                        (buf) -> new SyncLifePayload(buf.readBlockPos(), buf.readNbt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Sent to the player after saving a preset, so the GUI can show "Your ID". */
    public record YourPresetIdS2C(String id) implements CustomPacketPayload {
        public static final Type<YourPresetIdS2C> ID = new Type<>(ModRegistry.id("life_your_preset_id"));
        public static final StreamCodec<RegistryFriendlyByteBuf, YourPresetIdS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.id(), 16),
                        (buf) -> new YourPresetIdS2C(buf.readUtf(16))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Scan results including each member's display name. */
    public record NearbyResultS2C(BlockPos origin, List<Entry> found) implements CustomPacketPayload {
        public record Entry(BlockPos pos, String name) {}
        public static final Type<NearbyResultS2C> ID = new Type<>(ModRegistry.id("life_nearby_result"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NearbyResultS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.origin());
                            buf.writeVarInt(p.found().size());
                            for (var e : p.found()) {
                                buf.writeBlockPos(e.pos());
                                buf.writeUtf(e.name(), 64);
                            }
                        },
                        (buf) -> {
                            BlockPos o = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var list = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                BlockPos bp = buf.readBlockPos();
                                String name = buf.readUtf(64);
                                list.add(new Entry(bp, name));
                            }
                            return new NearbyResultS2C(o, list);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** All groups (id + name) for the left list. */
    public record GroupsListS2C(List<Entry> groups) implements CustomPacketPayload {
        public record Entry(UUID id, String name) {}

        public static final Type<GroupsListS2C> ID = new Type<>(ModRegistry.id("life_groups_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GroupsListS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeVarInt(p.groups().size());
                            for (var e : p.groups()) {
                                buf.writeUUID(e.id());
                                buf.writeUtf(e.name(), 64);
                            }
                        },
                        (buf) -> {
                            int n = buf.readVarInt();
                            var out = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                out.add(new Entry(buf.readUUID(), buf.readUtf(64)));
                            }
                            return new GroupsListS2C(out);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Group snapshot broadcast to clients: name, started, active index, member order, dead list. */
    public record GroupSnapshotS2C(
            UUID groupId,
            String name,
            boolean started,
            int activeIndex,
            List<BlockPos> members,
            List<String> memberNames,
            List<BlockPos> dead
    ) implements CustomPacketPayload {
        public static final Type<GroupSnapshotS2C> ID = new Type<>(ModRegistry.id("life_group_snapshot"));

        public static final StreamCodec<RegistryFriendlyByteBuf, GroupSnapshotS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUUID(p.groupId());
                            buf.writeUtf(p.name(), 64);
                            buf.writeBoolean(p.started());
                            buf.writeVarInt(p.activeIndex());

                            buf.writeVarInt(p.members().size());
                            for (var bp : p.members()) buf.writeBlockPos(bp);

                            buf.writeVarInt(p.memberNames().size());
                            for (var s : p.memberNames()) buf.writeUtf(s, 64);

                            buf.writeVarInt(p.dead().size());
                            for (var bp : p.dead()) buf.writeBlockPos(bp);
                        },
                        (buf) -> {
                            UUID id = buf.readUUID();
                            String name = buf.readUtf(64);
                            boolean started = buf.readBoolean();
                            int ai = buf.readVarInt();

                            int n = buf.readVarInt();
                            var members = new ArrayList<BlockPos>(n);
                            for (int i = 0; i < n; i++) members.add(buf.readBlockPos());

                            int nn = buf.readVarInt();
                            var memberNames = new ArrayList<String>(nn);
                            for (int i = 0; i < nn; i++) memberNames.add(buf.readUtf(64));

                            int d = buf.readVarInt();
                            var dead = new ArrayList<BlockPos>(d);
                            for (int i = 0; i < d; i++) dead.add(buf.readBlockPos());

                            return new GroupSnapshotS2C(id, name, started, ai, members, memberNames, dead);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record GroupRemovedS2C(UUID groupId) implements CustomPacketPayload {
        public static final Type<GroupRemovedS2C> ID = new Type<>(ModRegistry.id("life_group_removed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GroupRemovedS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUUID(p.groupId()),
                        (buf) -> new GroupRemovedS2C(buf.readUUID())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S ----------
    public record SetNameC2S(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SetNameC2S> ID = new Type<>(ModRegistry.id("life_set_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetNameC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.name(), 64); },
                        (buf) -> new SetNameC2S(buf.readBlockPos(), buf.readUtf(64))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetLifeC2S(BlockPos pos, int value) implements CustomPacketPayload {
        public static final Type<SetLifeC2S> ID = new Type<>(ModRegistry.id("life_set_value"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetLifeC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.value()); },
                        (buf) -> new SetLifeC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AddLifeC2S(BlockPos pos, int delta) implements CustomPacketPayload {
        public static final Type<AddLifeC2S> ID = new Type<>(ModRegistry.id("life_add_value"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddLifeC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddLifeC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetColorC2S(BlockPos pos, int rgb) implements CustomPacketPayload {
        public static final Type<SetColorC2S> ID = new Type<>(ModRegistry.id("life_set_color"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetColorC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetColorC2S(buf.readBlockPos(), buf.readInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // --- appearance packets ---
    public record SetPlayerColorC2S(BlockPos pos, int rgb) implements CustomPacketPayload {
        public static final Type<SetPlayerColorC2S> ID = new Type<>(ModRegistry.id("life_set_player_color"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetPlayerColorC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetPlayerColorC2S(buf.readBlockPos(), buf.readInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetIconKeyC2S(BlockPos pos, String key) implements CustomPacketPayload {
        public static final Type<SetIconKeyC2S> ID = new Type<>(ModRegistry.id("life_set_icon_key"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetIconKeyC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 32); },
                        (buf) -> new SetIconKeyC2S(buf.readBlockPos(), buf.readUtf(32))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetFormatKeyC2S(BlockPos pos, String key) implements CustomPacketPayload {
        public static final Type<SetFormatKeyC2S> ID = new Type<>(ModRegistry.id("life_set_format_key"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetFormatKeyC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 128); },
                        (buf) -> new SetFormatKeyC2S(buf.readBlockPos(), buf.readUtf(128))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetCommanderLethalC2S(BlockPos pos, int value) implements CustomPacketPayload {
        public static final Type<SetCommanderLethalC2S> ID = new Type<>(ModRegistry.id("life_set_cmd_lethal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCommanderLethalC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.value()); },
                        (buf) -> new SetCommanderLethalC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ResetAppearanceC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ResetAppearanceC2S> ID = new Type<>(ModRegistry.id("life_reset_appearance"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetAppearanceC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ResetAppearanceC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---- NEW: preset id workflow ----
    public record SavePresetToPlayerC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<SavePresetToPlayerC2S> ID = new Type<>(ModRegistry.id("life_preset_save_to_player"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SavePresetToPlayerC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new SavePresetToPlayerC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ApplyPlayerPresetToBlockC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ApplyPlayerPresetToBlockC2S> ID = new Type<>(ModRegistry.id("life_preset_apply_player"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPlayerPresetToBlockC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ApplyPlayerPresetToBlockC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ApplyPresetIdToBlockC2S(BlockPos pos, String id) implements CustomPacketPayload {
        public static final Type<ApplyPresetIdToBlockC2S> ID = new Type<>(ModRegistry.id("life_preset_apply_id"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApplyPresetIdToBlockC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.id(), 16); },
                        (buf) -> new ApplyPresetIdToBlockC2S(buf.readBlockPos(), buf.readUtf(16))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetCounterC2S(BlockPos pos, String key, int value) implements CustomPacketPayload {
        public static final Type<SetCounterC2S> ID = new Type<>(ModRegistry.id("life_set_counter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCounterC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 32); buf.writeVarInt(p.value()); },
                        (buf) -> new SetCounterC2S(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AddCounterC2S(BlockPos pos, String key, int delta) implements CustomPacketPayload {
        public static final Type<AddCounterC2S> ID = new Type<>(ModRegistry.id("life_add_counter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddCounterC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 32); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddCounterC2S(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ScanNearbyC2S(BlockPos origin, int radius) implements CustomPacketPayload {
        public static final Type<ScanNearbyC2S> ID = new Type<>(ModRegistry.id("life_scan"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScanNearbyC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.origin()); buf.writeVarInt(p.radius()); },
                        (buf) -> new ScanNearbyC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record RequestGroupsC2S() implements CustomPacketPayload {
        public static final Type<RequestGroupsC2S> ID = new Type<>(ModRegistry.id("life_groups_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestGroupsC2S> CODEC =
                StreamCodec.of((buf, p) -> {}, (buf) -> new RequestGroupsC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CreateEmptyGroupC2S() implements CustomPacketPayload {
        public static final Type<CreateEmptyGroupC2S> ID = new Type<>(ModRegistry.id("life_group_create_empty"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateEmptyGroupC2S> CODEC =
                StreamCodec.of((buf, p) -> {}, (buf) -> new CreateEmptyGroupC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SaveGroupC2S(UUID groupId, String name, List<BlockPos> ordered) implements CustomPacketPayload {
        public static final Type<SaveGroupC2S> ID = new Type<>(ModRegistry.id("life_group_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveGroupC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUUID(p.groupId());
                            buf.writeUtf(p.name(), 64);
                            buf.writeVarInt(p.ordered().size());
                            for (var bp : p.ordered()) buf.writeBlockPos(bp);
                        },
                        (buf) -> {
                            UUID gid = buf.readUUID();
                            String name = buf.readUtf(64);
                            int n = buf.readVarInt();
                            var list = new ArrayList<BlockPos>(n);
                            for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
                            return new SaveGroupC2S(gid, name, list);
                        }
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record DeleteGroupC2S(UUID groupId) implements CustomPacketPayload {
        public static final Type<DeleteGroupC2S> ID = new Type<>(ModRegistry.id("life_group_delete"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteGroupC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUUID(p.groupId()),
                        (buf) -> new DeleteGroupC2S(buf.readUUID())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record StartGameC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<StartGameC2S> ID = new Type<>(ModRegistry.id("life_start_game"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartGameC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new StartGameC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record PassTurnC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<PassTurnC2S> ID = new Type<>(ModRegistry.id("life_pass_turn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassTurnC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new PassTurnC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record SetDeadC2S(BlockPos pos, boolean dead) implements CustomPacketPayload {
        public static final Type<SetDeadC2S> ID = new Type<>(ModRegistry.id("life_set_dead"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetDeadC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeBoolean(p.dead()); },
                        (buf) -> new SetDeadC2S(buf.readBlockPos(), buf.readBoolean())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Sets the icon key for a counter (stored in NBT as CounterIcons.<counterKey> = <iconKey>). */
    public record SetCounterIconC2S(BlockPos pos, String counterKey, String iconKey) implements CustomPacketPayload {
        public static final Type<SetCounterIconC2S> ID = new Type<>(ModRegistry.id("life_set_counter_icon"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetCounterIconC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeUtf(p.counterKey(), 32);
                            buf.writeUtf(p.iconKey(), 32);
                        },
                        (buf) -> new SetCounterIconC2S(
                                buf.readBlockPos(),
                                buf.readUtf(32),
                                buf.readUtf(32)
                        )
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }



    // ---------- registration ----------
    public static void registerCommon() {
        // S2C
        PayloadTypeRegistry.playS2C().register(OpenLifeScreenPayload.ID, OpenLifeScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncLifePayload.ID, SyncLifePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(YourPresetIdS2C.ID, YourPresetIdS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NearbyResultS2C.ID, NearbyResultS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupsListS2C.ID, GroupsListS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupSnapshotS2C.ID, GroupSnapshotS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(GroupRemovedS2C.ID, GroupRemovedS2C.CODEC);

        // C2S
        PayloadTypeRegistry.playC2S().register(SetNameC2S.ID, SetNameC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetLifeC2S.ID, SetLifeC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(AddLifeC2S.ID, AddLifeC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetColorC2S.ID, SetColorC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCounterC2S.ID, SetCounterC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(AddCounterC2S.ID, AddCounterC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ScanNearbyC2S.ID, ScanNearbyC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(RequestGroupsC2S.ID, RequestGroupsC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CreateEmptyGroupC2S.ID, CreateEmptyGroupC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveGroupC2S.ID, SaveGroupC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteGroupC2S.ID, DeleteGroupC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(StartGameC2S.ID, StartGameC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(PassTurnC2S.ID, PassTurnC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetDeadC2S.ID, SetDeadC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(ResetGameC2S.ID, ResetGameC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveCounterKeyC2S.ID, RemoveCounterKeyC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(AddCommanderDamageC2S.ID, AddCommanderDamageC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(LifePointPackets.SetIconSwapColorC2S.ID, LifePointPackets.SetIconSwapColorC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(SetPlayerColorC2S.ID, SetPlayerColorC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetIconKeyC2S.ID, SetIconKeyC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetFormatKeyC2S.ID, SetFormatKeyC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCommanderLethalC2S.ID, SetCommanderLethalC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ResetAppearanceC2S.ID, ResetAppearanceC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCounterIconC2S.ID, SetCounterIconC2S.CODEC);

        PayloadTypeRegistry.playC2S().register(SavePresetToPlayerC2S.ID, SavePresetToPlayerC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ApplyPlayerPresetToBlockC2S.ID, ApplyPlayerPresetToBlockC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ApplyPresetIdToBlockC2S.ID, ApplyPresetIdToBlockC2S.CODEC);

        // ---- receivers ----
        ServerPlayNetworking.registerGlobalReceiver(SetNameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setDisplayName(payload.name());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetLifeC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.setLife(world, payload.pos(), payload.value());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddLifeC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.addLife(world, payload.pos(), payload.delta());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetColorC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setLifeColor(payload.rgb());
                })
        );

        // appearance receivers
        ServerPlayNetworking.registerGlobalReceiver(SetPlayerColorC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setPlayerColor(payload.rgb());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetIconKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setIconKey(payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetFormatKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.applyFormatSelection(world, payload.pos(), payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCommanderLethalC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setCommanderLethal(payload.value());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ResetAppearanceC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.resetAppearance();
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(LifePointPackets.SetIconSwapColorC2S.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            int rgb = payload.rgb() & 0xFFFFFF;

            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.level();
                if (!world.isLoaded(pos)) return;

                var be = world.getBlockEntity(pos);
                if (!(be instanceof LifePointBlockEntity lifeBe)) return;

                lifeBe.setIconSwapColor(rgb);     // you add this setter (next section)
                lifeBe.setChanged();

                // whichever sync approach you already use:
                // - world.updateListeners(...)
                // - be.markDirty + markForUpdate
                // - or send your existing LifePoint sync packet to tracking players
                lifeBe.sync(); // if you already have a helper like this
            });
        });


        // ---- preset workflow receivers ----
        ServerPlayNetworking.registerGlobalReceiver(SavePresetToPlayerC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    UUID pid = ctx.player().getUUID();
                    CompoundTag preset = presetFrom(lp);

                    PLAYER_PRESET.put(pid, preset);

                    String id = PLAYER_PRESET_ID.get(pid);
                    if (id == null || id.isBlank()) {
                        id = genId();
                        PLAYER_PRESET_ID.put(pid, id);
                    }
                    PRESET_BY_ID.put(id, preset);

                    ServerPlayNetworking.send((ServerPlayer) ctx.player(), new YourPresetIdS2C(id));

                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ApplyPlayerPresetToBlockC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    CompoundTag preset = PLAYER_PRESET.get(ctx.player().getUUID());
                    if (preset == null) return;

                    applyPreset(lp, preset);
                    syncToTracking(world, payload.pos(), lp);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ApplyPresetIdToBlockC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    String id = payload.id();
                    if (id == null) return;
                    id = id.trim().toUpperCase();
                    if (id.isEmpty()) return;

                    CompoundTag preset = PRESET_BY_ID.get(id);
                    if (preset == null) return;

                    applyPreset(lp, preset);
                    syncToTracking(world, payload.pos(), lp);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCounterC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    int v = Math.max(0, payload.value()); // ✅ only min clamp, no max cap
                    lp.setCounter(payload.key(), v);

                    // Optional but recommended: push corrected value immediately
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddCounterC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    String key = payload.key();
                    int oldV = lp.getCounters().getOrDefault(key, 0);
                    int newV = Math.max(0, oldV + payload.delta()); // ✅ no max cap

                    lp.setCounter(key, newV);

                    // Optional but recommended: update clients immediately
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ScanNearbyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var found = LifePlayGroups.scanNamed(world, payload.origin(), payload.radius());
                    var s2c = new NearbyResultS2C(payload.origin(), found);
                    ServerPlayNetworking.send((ServerPlayer) ctx.player(), s2c);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(RequestGroupsC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    var list = LifePlayGroups.getGroupsList(world);
                    ServerPlayNetworking.send((ServerPlayer) ctx.player(), new GroupsListS2C(list));

                    for (var e : list) {
                        var g = LifePlayGroups.getGroup(world, e.id());
                        if (g != null) {
                            var names = new ArrayList<String>(g.order.size());
                            for (var bp : g.order) {
                                String nm = "";
                                var be = world.getBlockEntity(bp);
                                if (be instanceof LifePointBlockEntity lp) nm = lp.getDisplayName();
                                if (nm == null || nm.isBlank()) nm = bp.getX() + "," + bp.getY() + "," + bp.getZ();
                                names.add(nm);
                            }

                            ServerPlayNetworking.send(
                                    (ServerPlayer) ctx.player(),
                                    new GroupSnapshotS2C(
                                            g.id, g.name, g.started, g.activeIndex,
                                            List.copyOf(g.order),
                                            names,
                                            new ArrayList<>(g.dead)
                                    )
                            );
                        }
                    }
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CreateEmptyGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.createEmptyGroup(world);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SaveGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.saveGroup(world, payload.groupId(), payload.name(), payload.ordered());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(DeleteGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.deleteGroup(world, payload.groupId());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(StartGameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    UUID gid = LifePlayGroups.findGroupId(world, payload.pos());
                    if (gid != null) LifePlayGroups.startGame(world, gid);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(PassTurnC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.passTurn(world, payload.pos());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetDeadC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    LifePlayGroups.setDead(world, payload.pos(), payload.dead());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ResetGameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerLevel world = (ServerLevel) ctx.player().level();
                    UUID gid = LifePlayGroups.findGroupId(world, payload.pos());
                    if (gid != null) LifePlayGroups.resetGame(world, gid);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(RemoveCounterKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.removeCounter(payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddCommanderDamageC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var world = (ServerLevel) ctx.player().level();
                    var be = world.getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) {
                        if (!lp.getLifeFormat().hasCommanderDamage()) return;
                        lp.addCommanderDamageAndAdjustLife(payload.source(), payload.delta());
                        return;
                    }
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCounterIconC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerLevel) ctx.player().level()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) {
                        lp.setCounterIcon(payload.counterKey(), payload.iconKey());
                        // Make sure clients update immediately
                        syncToTracking((ServerLevel) ctx.player().level(), payload.pos(), lp);
                    }
                })
        );

    }

    public record ResetGameC2S(BlockPos pos) implements CustomPacketPayload {
        public static final Type<ResetGameC2S> ID = new Type<>(ModRegistry.id("life_reset_game"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetGameC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ResetGameC2S(buf.readBlockPos())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record RemoveCounterKeyC2S(BlockPos pos, String key) implements CustomPacketPayload {
        public static final Type<RemoveCounterKeyC2S> ID = new Type<>(ModRegistry.id("life_remove_counter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveCounterKeyC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeUtf(p.key(), 32); },
                        (buf) -> new RemoveCounterKeyC2S(buf.readBlockPos(), buf.readUtf(32))
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AddCommanderDamageC2S(BlockPos pos, BlockPos source, int delta) implements CustomPacketPayload {
        public static final Type<AddCommanderDamageC2S> ID = new Type<>(ModRegistry.id("life_add_cmd_dmg"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddCommanderDamageC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeBlockPos(p.source()); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddCommanderDamageC2S(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- helpers ----------
    public static void openScreen(ServerLevel world, Player player, BlockPos pos, LifePointBlockEntity lp) {
        if (!(player instanceof ServerPlayer sp)) return;
        ServerPlayNetworking.send(sp, new OpenLifeScreenPayload(pos, toNbt(lp)));
    }

    public static void syncToTracking(Level world, BlockPos pos, LifePointBlockEntity lp) {
        if (!(world instanceof ServerLevel sw)) return;
        var payload = new SyncLifePayload(pos, toNbt(lp));
        var watchers = new java.util.LinkedHashSet<ServerPlayer>();
        for (var sp : PlayerLookup.tracking(sw, pos)) {
            watchers.add(sp);
        }
        for (BlockPos displayPos : lp.getLinkedDisplaysCopy()) {
            if (displayPos == null) continue;
            for (var sp : PlayerLookup.tracking(sw, displayPos)) {
                watchers.add(sp);
            }
        }
        for (var sp : watchers) {
            ServerPlayNetworking.send(sp, payload);
        }
    }

    public static void groupSnapshot(ServerLevel world, UUID id, String name, boolean started,
                                     int activeIndex, List<BlockPos> members, List<String> memberNames, List<BlockPos> dead) {
        var p = new GroupSnapshotS2C(id, name, started, activeIndex, members, memberNames, dead);
        for (var sp : world.players()) ServerPlayNetworking.send(sp, p);
    }

    public static void groupRemoved(ServerLevel world, UUID groupId) {
        var p = new GroupRemovedS2C(groupId);
        for (var sp : world.players()) ServerPlayNetworking.send(sp, p);
    }

    public static void groupsList(ServerLevel world, List<GroupsListS2C.Entry> list) {
        var p = new GroupsListS2C(list);
        for (var sp : world.players()) ServerPlayNetworking.send(sp, p);
    }

    private static CompoundTag toNbt(LifePointBlockEntity lp) {
        var n = new CompoundTag();
        n.putString("DisplayName", lp.getDisplayName());
        n.putInt("Life", lp.getLife());
        n.putInt("LifeColor", lp.getLifeColor());
        n.putBoolean("TurnActive", lp.isTurnActive());
        n.putBoolean("GameStarted", lp.isGameStarted());

        // ✅ appearance
        n.putInt("PlayerColor", lp.getPlayerColor());
        n.putString("IconKey", lp.getIconKey());
        n.putString("FormatKey", lp.getFormatKey());
        n.putInt("CmdLethal", lp.getCommanderLethal());
        n.putInt("IconSwapColor", lp.getIconSwapColor());


        var c = new CompoundTag();
        for (var e : lp.getCounters().entrySet()) c.putInt(e.getKey(), e.getValue());
        n.put("Counters", c);

        var ci = new CompoundTag();
        for (var e : lp.getCounterIcons().entrySet()) ci.putString(e.getKey(), e.getValue());
        n.put("CounterIcons", ci);

        // Commander damage: legacy compound (optional) + new list for display
        var cdList = new net.minecraft.nbt.ListTag();

        if (lp.getLifeFormat().hasCommanderDamage()) {
            for (var e : lp.getCommanderDamage().long2IntEntrySet()) {
                int dmg = e.getIntValue();
                if (dmg <= 0) continue;

                var row = new CompoundTag();
                row.putLong("AttackerPos", e.getLongKey());
                row.putInt("Damage", dmg);
                cdList.add(row);
            }
        }

        n.put("CommanderDamageList", cdList);
        // ✅ also send new long-key format (matches BE persistence)
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (lp.getLifeFormat().hasCommanderDamage()) {
            for (var e : lp.getCommanderDamage().long2IntEntrySet()) {
                int dmg = e.getIntValue();
                if (dmg <= 0) continue;

                long k = e.getLongKey();

                if (!first) sb.append(',');
                first = false;
                sb.append(k);

                n.putInt("CmdL_" + k, dmg);
            }
        }

        n.putString("CmdLKeys", sb.toString());




        if (lp.getGroupId() != null) n.putString("GroupId", lp.getGroupId().toString());
        n.putInt("GroupOrderIndex", lp.getGroupOrderIndex());
        return n;
    }

    public static void syncToPlayers(Iterable<ServerPlayer> players, BlockPos lifePos, LifePointBlockEntity lp) {
        if (players == null || lp == null) return;

        var payload = new SyncLifePayload(lifePos, toNbt(lp));

        for (ServerPlayer sp : players) {
            if (sp == null) continue;
            ServerPlayNetworking.send(sp, payload);
        }
    }

    private LifePointPackets() {}
}
