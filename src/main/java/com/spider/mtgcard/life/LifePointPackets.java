package com.spider.mtgcard.life;

import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LifePointPackets {

    // ---------------- Presets (server-memory) ----------------
    private static final ConcurrentHashMap<UUID, NbtCompound> PLAYER_PRESET = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> PLAYER_PRESET_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, NbtCompound> PRESET_BY_ID = new ConcurrentHashMap<>();

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
            Identifier.of("mtgcard", "life_set_icon_swap_color");

    public record SetIconSwapColorC2S(BlockPos pos, int rgb) implements CustomPayload {
        public static final Id<SetIconSwapColorC2S> ID = new Id<>(SET_ICON_SWAP_COLOR_ID);

        public static final PacketCodec<RegistryByteBuf, SetIconSwapColorC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetIconSwapColorC2S(buf.readBlockPos(), buf.readInt())
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }


    private static NbtCompound presetFrom(LifePointBlockEntity lp) {
        var n = new NbtCompound();
        n.putInt("PlayerColor", lp.getPlayerColor());
        n.putInt("LifeColor", lp.getLifeColor());
        n.putString("IconKey", lp.getIconKey());
        n.putString("FormatKey", lp.getFormatKey());
        n.putInt("CmdLethal", lp.getCommanderLethal());
        n.putInt("IconSwapColor", lp.getIconSwapColor());
        return n;
    }

    private static void applyPreset(LifePointBlockEntity lp, NbtCompound n) {
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
        lp.setFormatKey(fmt);
        lp.setCommanderLethal(lethal);
        lp.setIconSwapColor(swap);
    }

    // ---------- S2C ----------
    public record OpenLifeScreenPayload(BlockPos pos, NbtCompound state) implements CustomPayload {
        public static final Id<OpenLifeScreenPayload> ID = new Id<>(ModRegistry.id("open_life"));
        public static final PacketCodec<RegistryByteBuf, OpenLifeScreenPayload> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeNbt(p.state()); },
                        (buf) -> new OpenLifeScreenPayload(buf.readBlockPos(), buf.readNbt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncLifePayload(BlockPos pos, NbtCompound state) implements CustomPayload {
        public static final Id<SyncLifePayload> ID = new Id<>(ModRegistry.id("sync_life"));
        public static final PacketCodec<RegistryByteBuf, SyncLifePayload> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeNbt(p.state()); },
                        (buf) -> new SyncLifePayload(buf.readBlockPos(), buf.readNbt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Sent to the player after saving a preset, so the GUI can show "Your ID". */
    public record YourPresetIdS2C(String id) implements CustomPayload {
        public static final Id<YourPresetIdS2C> ID = new Id<>(ModRegistry.id("life_your_preset_id"));
        public static final PacketCodec<RegistryByteBuf, YourPresetIdS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeString(p.id(), 16),
                        (buf) -> new YourPresetIdS2C(buf.readString(16))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Scan results including each member's display name. */
    public record NearbyResultS2C(BlockPos origin, List<Entry> found) implements CustomPayload {
        public record Entry(BlockPos pos, String name) {}
        public static final Id<NearbyResultS2C> ID = new Id<>(ModRegistry.id("life_nearby_result"));

        public static final PacketCodec<RegistryByteBuf, NearbyResultS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.origin());
                            buf.writeVarInt(p.found().size());
                            for (var e : p.found()) {
                                buf.writeBlockPos(e.pos());
                                buf.writeString(e.name(), 64);
                            }
                        },
                        (buf) -> {
                            BlockPos o = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var list = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                BlockPos bp = buf.readBlockPos();
                                String name = buf.readString(64);
                                list.add(new Entry(bp, name));
                            }
                            return new NearbyResultS2C(o, list);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** All groups (id + name) for the left list. */
    public record GroupsListS2C(List<Entry> groups) implements CustomPayload {
        public record Entry(UUID id, String name) {}

        public static final Id<GroupsListS2C> ID = new Id<>(ModRegistry.id("life_groups_list"));
        public static final PacketCodec<RegistryByteBuf, GroupsListS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeVarInt(p.groups().size());
                            for (var e : p.groups()) {
                                buf.writeUuid(e.id());
                                buf.writeString(e.name(), 64);
                            }
                        },
                        (buf) -> {
                            int n = buf.readVarInt();
                            var out = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                out.add(new Entry(buf.readUuid(), buf.readString(64)));
                            }
                            return new GroupsListS2C(out);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    ) implements CustomPayload {
        public static final Id<GroupSnapshotS2C> ID = new Id<>(ModRegistry.id("life_group_snapshot"));

        public static final PacketCodec<RegistryByteBuf, GroupSnapshotS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeUuid(p.groupId());
                            buf.writeString(p.name(), 64);
                            buf.writeBoolean(p.started());
                            buf.writeVarInt(p.activeIndex());

                            buf.writeVarInt(p.members().size());
                            for (var bp : p.members()) buf.writeBlockPos(bp);

                            buf.writeVarInt(p.memberNames().size());
                            for (var s : p.memberNames()) buf.writeString(s, 64);

                            buf.writeVarInt(p.dead().size());
                            for (var bp : p.dead()) buf.writeBlockPos(bp);
                        },
                        (buf) -> {
                            UUID id = buf.readUuid();
                            String name = buf.readString(64);
                            boolean started = buf.readBoolean();
                            int ai = buf.readVarInt();

                            int n = buf.readVarInt();
                            var members = new ArrayList<BlockPos>(n);
                            for (int i = 0; i < n; i++) members.add(buf.readBlockPos());

                            int nn = buf.readVarInt();
                            var memberNames = new ArrayList<String>(nn);
                            for (int i = 0; i < nn; i++) memberNames.add(buf.readString(64));

                            int d = buf.readVarInt();
                            var dead = new ArrayList<BlockPos>(d);
                            for (int i = 0; i < d; i++) dead.add(buf.readBlockPos());

                            return new GroupSnapshotS2C(id, name, started, ai, members, memberNames, dead);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GroupRemovedS2C(UUID groupId) implements CustomPayload {
        public static final Id<GroupRemovedS2C> ID = new Id<>(ModRegistry.id("life_group_removed"));
        public static final PacketCodec<RegistryByteBuf, GroupRemovedS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeUuid(p.groupId()),
                        (buf) -> new GroupRemovedS2C(buf.readUuid())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S ----------
    public record SetNameC2S(BlockPos pos, String name) implements CustomPayload {
        public static final Id<SetNameC2S> ID = new Id<>(ModRegistry.id("life_set_name"));
        public static final PacketCodec<RegistryByteBuf, SetNameC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.name(), 64); },
                        (buf) -> new SetNameC2S(buf.readBlockPos(), buf.readString(64))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetLifeC2S(BlockPos pos, int value) implements CustomPayload {
        public static final Id<SetLifeC2S> ID = new Id<>(ModRegistry.id("life_set_value"));
        public static final PacketCodec<RegistryByteBuf, SetLifeC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.value()); },
                        (buf) -> new SetLifeC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AddLifeC2S(BlockPos pos, int delta) implements CustomPayload {
        public static final Id<AddLifeC2S> ID = new Id<>(ModRegistry.id("life_add_value"));
        public static final PacketCodec<RegistryByteBuf, AddLifeC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddLifeC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetColorC2S(BlockPos pos, int rgb) implements CustomPayload {
        public static final Id<SetColorC2S> ID = new Id<>(ModRegistry.id("life_set_color"));
        public static final PacketCodec<RegistryByteBuf, SetColorC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetColorC2S(buf.readBlockPos(), buf.readInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // --- appearance packets ---
    public record SetPlayerColorC2S(BlockPos pos, int rgb) implements CustomPayload {
        public static final Id<SetPlayerColorC2S> ID = new Id<>(ModRegistry.id("life_set_player_color"));
        public static final PacketCodec<RegistryByteBuf, SetPlayerColorC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeInt(p.rgb()); },
                        (buf) -> new SetPlayerColorC2S(buf.readBlockPos(), buf.readInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetIconKeyC2S(BlockPos pos, String key) implements CustomPayload {
        public static final Id<SetIconKeyC2S> ID = new Id<>(ModRegistry.id("life_set_icon_key"));
        public static final PacketCodec<RegistryByteBuf, SetIconKeyC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.key(), 32); },
                        (buf) -> new SetIconKeyC2S(buf.readBlockPos(), buf.readString(32))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetFormatKeyC2S(BlockPos pos, String key) implements CustomPayload {
        public static final Id<SetFormatKeyC2S> ID = new Id<>(ModRegistry.id("life_set_format_key"));
        public static final PacketCodec<RegistryByteBuf, SetFormatKeyC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.key(), 32); },
                        (buf) -> new SetFormatKeyC2S(buf.readBlockPos(), buf.readString(32))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetCommanderLethalC2S(BlockPos pos, int value) implements CustomPayload {
        public static final Id<SetCommanderLethalC2S> ID = new Id<>(ModRegistry.id("life_set_cmd_lethal"));
        public static final PacketCodec<RegistryByteBuf, SetCommanderLethalC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.value()); },
                        (buf) -> new SetCommanderLethalC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ResetAppearanceC2S(BlockPos pos) implements CustomPayload {
        public static final Id<ResetAppearanceC2S> ID = new Id<>(ModRegistry.id("life_reset_appearance"));
        public static final PacketCodec<RegistryByteBuf, ResetAppearanceC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ResetAppearanceC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---- NEW: preset id workflow ----
    public record SavePresetToPlayerC2S(BlockPos pos) implements CustomPayload {
        public static final Id<SavePresetToPlayerC2S> ID = new Id<>(ModRegistry.id("life_preset_save_to_player"));
        public static final PacketCodec<RegistryByteBuf, SavePresetToPlayerC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new SavePresetToPlayerC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ApplyPlayerPresetToBlockC2S(BlockPos pos) implements CustomPayload {
        public static final Id<ApplyPlayerPresetToBlockC2S> ID = new Id<>(ModRegistry.id("life_preset_apply_player"));
        public static final PacketCodec<RegistryByteBuf, ApplyPlayerPresetToBlockC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ApplyPlayerPresetToBlockC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ApplyPresetIdToBlockC2S(BlockPos pos, String id) implements CustomPayload {
        public static final Id<ApplyPresetIdToBlockC2S> ID = new Id<>(ModRegistry.id("life_preset_apply_id"));
        public static final PacketCodec<RegistryByteBuf, ApplyPresetIdToBlockC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.id(), 16); },
                        (buf) -> new ApplyPresetIdToBlockC2S(buf.readBlockPos(), buf.readString(16))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetCounterC2S(BlockPos pos, String key, int value) implements CustomPayload {
        public static final Id<SetCounterC2S> ID = new Id<>(ModRegistry.id("life_set_counter"));
        public static final PacketCodec<RegistryByteBuf, SetCounterC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.key(), 32); buf.writeVarInt(p.value()); },
                        (buf) -> new SetCounterC2S(buf.readBlockPos(), buf.readString(32), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AddCounterC2S(BlockPos pos, String key, int delta) implements CustomPayload {
        public static final Id<AddCounterC2S> ID = new Id<>(ModRegistry.id("life_add_counter"));
        public static final PacketCodec<RegistryByteBuf, AddCounterC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.key(), 32); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddCounterC2S(buf.readBlockPos(), buf.readString(32), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ScanNearbyC2S(BlockPos origin, int radius) implements CustomPayload {
        public static final Id<ScanNearbyC2S> ID = new Id<>(ModRegistry.id("life_scan"));
        public static final PacketCodec<RegistryByteBuf, ScanNearbyC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.origin()); buf.writeVarInt(p.radius()); },
                        (buf) -> new ScanNearbyC2S(buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RequestGroupsC2S() implements CustomPayload {
        public static final Id<RequestGroupsC2S> ID = new Id<>(ModRegistry.id("life_groups_request"));
        public static final PacketCodec<RegistryByteBuf, RequestGroupsC2S> CODEC =
                PacketCodec.ofStatic((buf, p) -> {}, (buf) -> new RequestGroupsC2S());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CreateEmptyGroupC2S() implements CustomPayload {
        public static final Id<CreateEmptyGroupC2S> ID = new Id<>(ModRegistry.id("life_group_create_empty"));
        public static final PacketCodec<RegistryByteBuf, CreateEmptyGroupC2S> CODEC =
                PacketCodec.ofStatic((buf, p) -> {}, (buf) -> new CreateEmptyGroupC2S());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SaveGroupC2S(UUID groupId, String name, List<BlockPos> ordered) implements CustomPayload {
        public static final Id<SaveGroupC2S> ID = new Id<>(ModRegistry.id("life_group_save"));
        public static final PacketCodec<RegistryByteBuf, SaveGroupC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeUuid(p.groupId());
                            buf.writeString(p.name(), 64);
                            buf.writeVarInt(p.ordered().size());
                            for (var bp : p.ordered()) buf.writeBlockPos(bp);
                        },
                        (buf) -> {
                            UUID gid = buf.readUuid();
                            String name = buf.readString(64);
                            int n = buf.readVarInt();
                            var list = new ArrayList<BlockPos>(n);
                            for (int i = 0; i < n; i++) list.add(buf.readBlockPos());
                            return new SaveGroupC2S(gid, name, list);
                        }
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record DeleteGroupC2S(UUID groupId) implements CustomPayload {
        public static final Id<DeleteGroupC2S> ID = new Id<>(ModRegistry.id("life_group_delete"));
        public static final PacketCodec<RegistryByteBuf, DeleteGroupC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeUuid(p.groupId()),
                        (buf) -> new DeleteGroupC2S(buf.readUuid())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record StartGameC2S(BlockPos pos) implements CustomPayload {
        public static final Id<StartGameC2S> ID = new Id<>(ModRegistry.id("life_start_game"));
        public static final PacketCodec<RegistryByteBuf, StartGameC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new StartGameC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record PassTurnC2S(BlockPos pos) implements CustomPayload {
        public static final Id<PassTurnC2S> ID = new Id<>(ModRegistry.id("life_pass_turn"));
        public static final PacketCodec<RegistryByteBuf, PassTurnC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new PassTurnC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetDeadC2S(BlockPos pos, boolean dead) implements CustomPayload {
        public static final Id<SetDeadC2S> ID = new Id<>(ModRegistry.id("life_set_dead"));
        public static final PacketCodec<RegistryByteBuf, SetDeadC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeBoolean(p.dead()); },
                        (buf) -> new SetDeadC2S(buf.readBlockPos(), buf.readBoolean())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Sets the icon key for a counter (stored in NBT as CounterIcons.<counterKey> = <iconKey>). */
    public record SetCounterIconC2S(BlockPos pos, String counterKey, String iconKey) implements CustomPayload {
        public static final Id<SetCounterIconC2S> ID = new Id<>(ModRegistry.id("life_set_counter_icon"));
        public static final PacketCodec<RegistryByteBuf, SetCounterIconC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeString(p.counterKey(), 32);
                            buf.writeString(p.iconKey(), 32);
                        },
                        (buf) -> new SetCounterIconC2S(
                                buf.readBlockPos(),
                                buf.readString(32),
                                buf.readString(32)
                        )
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
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

        // appearance packets
        PayloadTypeRegistry.playC2S().register(SetPlayerColorC2S.ID, SetPlayerColorC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetIconKeyC2S.ID, SetIconKeyC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetFormatKeyC2S.ID, SetFormatKeyC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCommanderLethalC2S.ID, SetCommanderLethalC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ResetAppearanceC2S.ID, ResetAppearanceC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCounterIconC2S.ID, SetCounterIconC2S.CODEC);


        // preset workflow packets
        PayloadTypeRegistry.playC2S().register(SavePresetToPlayerC2S.ID, SavePresetToPlayerC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ApplyPlayerPresetToBlockC2S.ID, ApplyPlayerPresetToBlockC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(ApplyPresetIdToBlockC2S.ID, ApplyPresetIdToBlockC2S.CODEC);

        // ---- receivers ----
        ServerPlayNetworking.registerGlobalReceiver(SetNameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setDisplayName(payload.name());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetLifeC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setLife(payload.value());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddLifeC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.addLife(payload.delta());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetColorC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setLifeColor(payload.rgb());
                })
        );

        // appearance receivers
        ServerPlayNetworking.registerGlobalReceiver(SetPlayerColorC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setPlayerColor(payload.rgb());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetIconKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setIconKey(payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetFormatKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setFormatKey(payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCommanderLethalC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.setCommanderLethal(payload.value());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ResetAppearanceC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.resetAppearance();
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(LifePointPackets.SetIconSwapColorC2S.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            int rgb = payload.rgb() & 0xFFFFFF;

            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.getEntityWorld();
                if (!world.isChunkLoaded(pos)) return;

                var be = world.getBlockEntity(pos);
                if (!(be instanceof LifePointBlockEntity lifeBe)) return;

                lifeBe.setIconSwapColor(rgb);     // you add this setter (next section)
                lifeBe.markDirty();

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
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    UUID pid = ctx.player().getUuid();
                    NbtCompound preset = presetFrom(lp);

                    PLAYER_PRESET.put(pid, preset);

                    String id = PLAYER_PRESET_ID.get(pid);
                    if (id == null || id.isBlank()) {
                        id = genId();
                        PLAYER_PRESET_ID.put(pid, id);
                    }
                    PRESET_BY_ID.put(id, preset);

                    ((ServerPlayerEntity) ctx.player()).networkHandler.sendPacket(
                            ServerPlayNetworking.createS2CPacket(new YourPresetIdS2C(id))
                    );
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ApplyPlayerPresetToBlockC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    NbtCompound preset = PLAYER_PRESET.get(ctx.player().getUuid());
                    if (preset == null) return;

                    applyPreset(lp, preset);
                    syncToTracking(world, payload.pos(), lp);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ApplyPresetIdToBlockC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    String id = payload.id();
                    if (id == null) return;
                    id = id.trim().toUpperCase();
                    if (id.isEmpty()) return;

                    NbtCompound preset = PRESET_BY_ID.get(id);
                    if (preset == null) return;

                    applyPreset(lp, preset);
                    syncToTracking(world, payload.pos(), lp);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCounterC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof LifePointBlockEntity lp)) return;

                    int v = Math.max(0, payload.value()); // ✅ only min clamp, no max cap
                    lp.setCounter(payload.key(), v);

                    // Optional but recommended: push corrected value immediately
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddCounterC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
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
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var found = LifePlayGroups.scanNamed(world, payload.origin(), payload.radius());
                    var s2c = new NearbyResultS2C(payload.origin(), found);
                    ((ServerPlayerEntity) ctx.player()).networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(s2c));
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(RequestGroupsC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    var list = LifePlayGroups.getGroupsList(world);
                    ((ServerPlayerEntity) ctx.player()).networkHandler.sendPacket(
                            ServerPlayNetworking.createS2CPacket(new GroupsListS2C(list))
                    );

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

                            ((ServerPlayerEntity) ctx.player()).networkHandler.sendPacket(
                                    ServerPlayNetworking.createS2CPacket(
                                            new GroupSnapshotS2C(
                                                    g.id, g.name, g.started, g.activeIndex,
                                                    List.copyOf(g.order),
                                                    names,
                                                    new ArrayList<>(g.dead)
                                            )
                                    )
                            );
                        }
                    }
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CreateEmptyGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    LifePlayGroups.createEmptyGroup(world);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SaveGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    LifePlayGroups.saveGroup(world, payload.groupId(), payload.name(), payload.ordered());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(DeleteGroupC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    LifePlayGroups.deleteGroup(world, payload.groupId());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(StartGameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    UUID gid = LifePlayGroups.findGroupId(world, payload.pos());
                    if (gid != null) LifePlayGroups.startGame(world, gid);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(PassTurnC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    LifePlayGroups.passTurn(world, payload.pos());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetDeadC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    LifePlayGroups.setDead(world, payload.pos(), payload.dead());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ResetGameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerWorld world = (ServerWorld) ctx.player().getEntityWorld();
                    UUID gid = LifePlayGroups.findGroupId(world, payload.pos());
                    if (gid != null) LifePlayGroups.resetGame(world, gid);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(RemoveCounterKeyC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) lp.removeCounter(payload.key());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(AddCommanderDamageC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var world = (ServerWorld) ctx.player().getEntityWorld();
                    var be = world.getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) {
                        lp.addCommanderDamage(payload.source(), payload.delta()); // ✅ ONLY cmd damage
                        syncToTracking(world, payload.pos(), lp);                // ✅ push update
                    }
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCounterIconC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    var be = ((ServerWorld) ctx.player().getEntityWorld()).getBlockEntity(payload.pos());
                    if (be instanceof LifePointBlockEntity lp) {
                        lp.setCounterIcon(payload.counterKey(), payload.iconKey());
                        // Make sure clients update immediately
                        syncToTracking((ServerWorld) ctx.player().getEntityWorld(), payload.pos(), lp);
                    }
                })
        );

    }

    public record ResetGameC2S(BlockPos pos) implements CustomPayload {
        public static final Id<ResetGameC2S> ID = new Id<>(ModRegistry.id("life_reset_game"));
        public static final PacketCodec<RegistryByteBuf, ResetGameC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeBlockPos(p.pos()),
                        (buf) -> new ResetGameC2S(buf.readBlockPos())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RemoveCounterKeyC2S(BlockPos pos, String key) implements CustomPayload {
        public static final Id<RemoveCounterKeyC2S> ID = new Id<>(ModRegistry.id("life_remove_counter"));
        public static final PacketCodec<RegistryByteBuf, RemoveCounterKeyC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeString(p.key(), 32); },
                        (buf) -> new RemoveCounterKeyC2S(buf.readBlockPos(), buf.readString(32))
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AddCommanderDamageC2S(BlockPos pos, BlockPos source, int delta) implements CustomPayload {
        public static final Id<AddCommanderDamageC2S> ID = new Id<>(ModRegistry.id("life_add_cmd_dmg"));
        public static final PacketCodec<RegistryByteBuf, AddCommanderDamageC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeBlockPos(p.source()); buf.writeVarInt(p.delta()); },
                        (buf) -> new AddCommanderDamageC2S(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt())
                );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- helpers ----------
    public static void openScreen(ServerWorld world, PlayerEntity player, BlockPos pos, LifePointBlockEntity lp) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(
                new OpenLifeScreenPayload(pos, toNbt(lp))
        ));
    }

    public static void syncToTracking(World world, BlockPos pos, LifePointBlockEntity lp) {
        if (!(world instanceof ServerWorld sw)) return;
        var payload = new SyncLifePayload(pos, toNbt(lp));
        for (var sp : PlayerLookup.tracking(sw, pos)) {
            sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(payload));
        }
    }

    public static void groupSnapshot(ServerWorld world, UUID id, String name, boolean started,
                                     int activeIndex, List<BlockPos> members, List<String> memberNames, List<BlockPos> dead) {
        var p = new GroupSnapshotS2C(id, name, started, activeIndex, members, memberNames, dead);
        for (var sp : world.getPlayers()) sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(p));
    }

    public static void groupRemoved(ServerWorld world, UUID groupId) {
        var p = new GroupRemovedS2C(groupId);
        for (var sp : world.getPlayers()) sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(p));
    }

    public static void groupsList(ServerWorld world, List<GroupsListS2C.Entry> list) {
        var p = new GroupsListS2C(list);
        for (var sp : world.getPlayers()) sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(p));
    }

    private static NbtCompound toNbt(LifePointBlockEntity lp) {
        var n = new NbtCompound();
        n.putString("DisplayName", lp.getDisplayName());
        n.putInt("Life", lp.getLife());
        n.putInt("LifeColor", lp.getLifeColor());
        n.putBoolean("TurnActive", lp.isTurnActive());

        // ✅ appearance
        n.putInt("PlayerColor", lp.getPlayerColor());
        n.putString("IconKey", lp.getIconKey());
        n.putString("FormatKey", lp.getFormatKey());
        n.putInt("CmdLethal", lp.getCommanderLethal());
        n.putInt("IconSwapColor", lp.getIconSwapColor());


        var c = new NbtCompound();
        for (var e : lp.getCounters().entrySet()) c.putInt(e.getKey(), e.getValue());
        n.put("Counters", c);

        var ci = new NbtCompound();
        for (var e : lp.getCounterIcons().entrySet()) ci.putString(e.getKey(), e.getValue());
        n.put("CounterIcons", ci);

        // Commander damage: legacy compound (optional) + new list for display
        var cdList = new net.minecraft.nbt.NbtList();

        for (var e : lp.getCommanderDamage().long2IntEntrySet()) {
            int dmg = e.getIntValue();
            if (dmg <= 0) continue;

            var row = new NbtCompound();
            row.putLong("AttackerPos", e.getLongKey());
            row.putInt("Damage", dmg);
            cdList.add(row);
        }

        n.put("CommanderDamageList", cdList);
        // ✅ also send new long-key format (matches BE persistence)
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (var e : lp.getCommanderDamage().long2IntEntrySet()) {
            int dmg = e.getIntValue();
            if (dmg <= 0) continue;

            long k = e.getLongKey();

            if (!first) sb.append(',');
            first = false;
            sb.append(k);

            n.putInt("CmdL_" + k, dmg);
        }

        n.putString("CmdLKeys", sb.toString());




        if (lp.getGroupId() != null) n.putString("GroupId", lp.getGroupId().toString());
        n.putInt("GroupOrderIndex", lp.getGroupOrderIndex());
        return n;
    }

    public static void syncToPlayers(Iterable<ServerPlayerEntity> players, BlockPos lifePos, LifePointBlockEntity lp) {
        if (players == null || lp == null) return;

        var payload = new SyncLifePayload(lifePos, toNbt(lp));

        for (ServerPlayerEntity sp : players) {
            if (sp == null) continue;
            sp.networkHandler.sendPacket(ServerPlayNetworking.createS2CPacket(payload));
        }
    }

    private LifePointPackets() {}
}
