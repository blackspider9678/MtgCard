package com.spider.mtgcard.deckcontrol;

import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class DeckControlPackets {

    // -------------------- C2S: Simple actions --------------------
    public enum Action {
        DRAW,
        SHUFFLE,
        TOGGLE_PEEK,
        REVEAL_N,
        MILL_N,
        START_SCRY,
        RESOLVE_SCRY,
        START_SURVEIL,
        RESOLVE_SURVEIL,
        PLACE_N_FROM_TOP,
        PLACE_BOTTOM,
        CASCADE_START,      // optional if you want cascade via Action packet
        CASCADE_RESOLVE,     // optional if you want cascade via Action packet
        SHUFFLE_GRAVEYARD_TO_LIBRARY,
        RESET_DECK
    }

    public enum OrderedKind {
        SCRY,
        SURVEIL
    }

    public enum OverlayKind {
        REVEAL,
        SCRY,
        SURVEIL,
        SHUFFLE_GRAVEYARD_TO_LIBRARY,
        RESET_DECK
    }

    public record ActionC2S(BlockPos pos, int actionOrdinal, int a, int b) implements CustomPacketPayload {
        public static final Type<ActionC2S> ID = new Type<>(ModRegistry.id("deck_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.actionOrdinal());
                            buf.writeVarInt(p.a());
                            buf.writeVarInt(p.b());
                        },
                        (buf) -> new ActionC2S(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // -------------------- S2C: Show cards (for overlays) --------------------


    public record OverlayS2C(BlockPos pos, int kindOrdinal, int n, List<ItemStack> cards) implements CustomPacketPayload {
        public static final Type<OverlayS2C> ID = new Type<>(ModRegistry.id("deck_overlay"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OverlayS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.kindOrdinal());
                            buf.writeVarInt(p.n());
                            buf.writeVarInt(p.cards().size());
                            for (var st : p.cards()) {
                                boolean present = (st != null && !st.isEmpty());
                                buf.writeBoolean(present);
                                if (present) ItemStack.STREAM_CODEC.encode(buf, st);
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int k = buf.readVarInt();
                            int n = buf.readVarInt();
                            int size = buf.readVarInt();
                            var list = new ArrayList<ItemStack>(size);
                            for (int i = 0; i < size; i++) {
                                boolean present = buf.readBoolean();
                                list.add(present ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY);
                            }
                            return new OverlayS2C(pos, k, n, list);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // -------------------- C2S: Cascade start/resolve --------------------
    public record CascadeStartC2S(BlockPos pos, int sourceMv) implements CustomPacketPayload {
        public static final Type<CascadeStartC2S> ID = new Type<>(ModRegistry.id("deck_cascade_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeStartC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.sourceMv()); },
                        (buf) -> new CascadeStartC2S(buf.readBlockPos(), buf.readVarInt())
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record CascadeResolveC2S(BlockPos pos, boolean cast) implements CustomPacketPayload {
        public static final Type<CascadeResolveC2S> ID = new Type<>(ModRegistry.id("deck_cascade_resolve"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeResolveC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeBoolean(p.cast()); },
                        (buf) -> new CascadeResolveC2S(buf.readBlockPos(), buf.readBoolean())
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // -------------------- S2C: Cascade overlay --------------------
    public record CascadeS2C(BlockPos pos, int sourceMv, int hitIndex, List<ItemStack> revealed) implements CustomPacketPayload {
        public static final Type<CascadeS2C> ID = new Type<>(ModRegistry.id("deck_cascade_overlay"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.sourceMv());
                            buf.writeVarInt(p.hitIndex());
                            buf.writeVarInt(p.revealed().size());
                            for (ItemStack st : p.revealed()) {
                                // IMPORTANT: do not send empties here
                                ItemStack.STREAM_CODEC.encode(buf, st);
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int mv = buf.readVarInt();
                            int hitIndex = buf.readVarInt();
                            int size = buf.readVarInt();
                            var list = new ArrayList<ItemStack>(size);
                            for (int i = 0; i < size; i++) list.add(ItemStack.STREAM_CODEC.decode(buf));
                            return new CascadeS2C(pos, mv, hitIndex, list);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // -------------------- registration --------------------
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(ActionC2S.ID, ActionC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OverlayS2C.ID, OverlayS2C.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(CascadeStartC2S.ID, CascadeStartC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CascadeResolveC2S.ID, CascadeResolveC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CascadeS2C.ID, CascadeS2C.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ResolveOrderedC2S.ID, ResolveOrderedC2S.CODEC);
    }

    public static void registerReceivers() {

        // --- base action receiver ---
        ServerPlayNetworking.registerGlobalReceiver(ActionC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    ServerLevel world = (ServerLevel) sp.level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof DeckControlBlockEntity dc)) return;

                    Action action;
                    try {
                        action = Action.values()[payload.actionOrdinal()];
                    } catch (Throwable t) {
                        return;
                    }

                    switch (action) {
                        case DRAW -> dc.drawTopAndEject();
                        case SHUFFLE -> dc.shuffle();
                        case TOGGLE_PEEK -> dc.setPeekActive(payload.a() != 0);
                        case MILL_N -> dc.millTop(Math.max(1, payload.a()));

                        case SHUFFLE_GRAVEYARD_TO_LIBRARY -> dc.shuffleGraveyardIntoLibrary(sp);
                        case RESET_DECK -> dc.resetDeck(sp);

                        case REVEAL_N -> {
                            int n = Math.max(1, payload.a());
                            var cards = noEmptyCopies(dc.peekTopCopies(n), n);
                            int actual = cards.size();
                            ServerPlayNetworking.send(sp,
                                    new OverlayS2C(payload.pos(), OverlayKind.REVEAL.ordinal(), actual, cards));
                        }

                        case START_SCRY -> {
                            int n = Math.max(1, payload.a());
                            var cards = noEmptyCopies(dc.peekTopCopies(n), n);
                            int actual = cards.size();
                            ServerPlayNetworking.send(sp,
                                    new OverlayS2C(payload.pos(), OverlayKind.SCRY.ordinal(), actual, cards));
                        }

                        case RESOLVE_SCRY -> {
                            int n = Math.max(1, payload.a());
                            int keepMask = payload.b();
                            var keepOrder = new ArrayList<Integer>();
                            for (int i = 0; i < n; i++) if (((keepMask >> i) & 1) != 0) keepOrder.add(i);
                            dc.resolveScry(keepOrder, false, n);
                        }

                        case START_SURVEIL -> {
                            int n = Math.max(1, payload.a());
                            var cards = noEmptyCopies(dc.peekTopCopies(n), n);
                            int actual = cards.size();
                            ServerPlayNetworking.send(sp,
                                    new OverlayS2C(payload.pos(), OverlayKind.SURVEIL.ordinal(), actual, cards));
                        }

                        case RESOLVE_SURVEIL -> {
                            int n = Math.max(1, payload.a());
                            int millMask = payload.b();
                            var toMill = new ArrayList<Integer>();
                            for (int i = 0; i < n; i++) if (((millMask >> i) & 1) != 0) toMill.add(i);
                            dc.resolveSurveil(sp, toMill, false, n);
                        }

                        case PLACE_N_FROM_TOP -> {
                            int playerSlot = payload.a();
                            int nFromTop = Math.max(1, payload.b());
                            dc.placeFromPlayer(sp, playerSlot, nFromTop - 1);
                        }

                        case PLACE_BOTTOM -> {
                            int playerSlot = payload.a();
                            dc.placeFromPlayerBottom(sp, playerSlot);
                        }

                        // optional if you route cascade through ActionC2S:
                        case CASCADE_START -> dc.startCascade(sp, payload.a());
                        case CASCADE_RESOLVE -> dc.resolveCascade(sp, payload.a() != 0);
                    }
                })
        );

        // --- cascade dedicated packets (recommended) ---
        ServerPlayNetworking.registerGlobalReceiver(CascadeStartC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    var world = (ServerLevel) sp.level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof DeckControlBlockEntity dc)) return;
                    dc.startCascade(sp, payload.sourceMv());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(CascadeResolveC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    var world = (ServerLevel) sp.level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof DeckControlBlockEntity dc)) return;
                    dc.resolveCascade(sp, payload.cast());
                })
        );

        // --- ordered resolve receiver (drag/drop) ---
        ServerPlayNetworking.registerGlobalReceiver(ResolveOrderedC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    ServerLevel world = (ServerLevel) sp.level();
                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof DeckControlBlockEntity dc)) return;

                    OrderedKind kind;
                    try {
                        kind = OrderedKind.values()[payload.kindOrdinal()];
                    } catch (Throwable t) {
                        return;
                    }

                    int n = Math.max(1, payload.n());
                    int topCount = Math.max(0, Math.min(payload.topCount(), n));

                    // Basic validation: must be length n, contain 0..n-1 exactly once
                    if (payload.size() != n) return;

                    boolean[] seen = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        int idx = payload.order()[i];
                        if (idx < 0 || idx >= n) return;
                        if (seen[idx]) return;
                        seen[idx] = true;
                    }

                    // Split into top/bottom in the exact order the client chose
                    var topOrder = new ArrayList<Integer>(topCount);
                    var bottomOrder = new ArrayList<Integer>(n - topCount);

                    for (int i = 0; i < n; i++) {
                        int idx = payload.order()[i];
                        if (i < topCount) topOrder.add(idx);
                        else bottomOrder.add(idx);
                    }

                    // Apply
                    switch (kind) {
                        case SCRY -> dc.resolveOrderedScry(n, topCount, payload.order(), false);
                        case SURVEIL -> {
                            var keepTopOrder = new ArrayList<Integer>(topCount);
                            var millOrder = new ArrayList<Integer>(n - topCount);

                            for (int i = 0; i < n; i++) {
                                int idx = payload.order()[i];
                                if (i < topCount) keepTopOrder.add(idx);
                                else millOrder.add(idx);
                            }

                            dc.resolveSurveilOrdered(sp, keepTopOrder, millOrder, false, n);
                        }
                    }
                })
        );
    }

    // -------------------- C2S: Ordered resolve (drag/drop) --------------------
    public record ResolveOrderedC2S(BlockPos pos, int kindOrdinal, int n, int topCount, int size, int[] order)
            implements CustomPacketPayload {

        public static final Type<ResolveOrderedC2S> ID = new Type<>(ModRegistry.id("deck_resolve_ordered"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResolveOrderedC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.kindOrdinal());
                            buf.writeVarInt(p.n());
                            buf.writeVarInt(p.topCount());
                            buf.writeVarInt(p.size());
                            for (int i = 0; i < p.size(); i++) buf.writeVarInt(p.order()[i]);
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int k = buf.readVarInt();
                            int n = buf.readVarInt();
                            int topCount = buf.readVarInt();
                            int size = buf.readVarInt();
                            int[] order = new int[size];
                            for (int i = 0; i < size; i++) order[i] = buf.readVarInt();
                            return new ResolveOrderedC2S(pos, k, n, topCount, size, order);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static List<ItemStack> noEmptyCopies(List<ItemStack> in, int max) {
        var out = new ArrayList<ItemStack>(Math.min(max, in.size()));
        for (int i = 0; i < in.size() && out.size() < max; i++) {
            ItemStack st = in.get(i);
            if (st == null || st.isEmpty()) continue;
            ItemStack c = st.copy();
            c.setCount(1);
            out.add(c);
        }
        return out;
    }

    private DeckControlPackets() {}
}
