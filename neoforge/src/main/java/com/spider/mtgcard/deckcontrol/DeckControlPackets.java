package com.spider.mtgcard.deckcontrol;

import com.spider.mtgcard.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public final class DeckControlPackets {
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
        CASCADE_START,
        CASCADE_RESOLVE,
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
                        buf -> new ActionC2S(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record OverlayS2C(BlockPos pos, int kindOrdinal, int n, List<ItemStack> cards) implements CustomPacketPayload {
        public static final Type<OverlayS2C> ID = new Type<>(ModRegistry.id("deck_overlay"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OverlayS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.kindOrdinal());
                            buf.writeVarInt(p.n());
                            buf.writeVarInt(p.cards().size());
                            for (ItemStack stack : p.cards()) {
                                boolean present = stack != null && !stack.isEmpty();
                                buf.writeBoolean(present);
                                if (present) {
                                    ItemStack.STREAM_CODEC.encode(buf, stack);
                                }
                            }
                        },
                        buf -> {
                            BlockPos pos = buf.readBlockPos();
                            int kind = buf.readVarInt();
                            int n = buf.readVarInt();
                            int size = buf.readVarInt();
                            ArrayList<ItemStack> cards = new ArrayList<>(size);
                            for (int i = 0; i < size; i++) {
                                cards.add(buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY);
                            }
                            return new OverlayS2C(pos, kind, n, cards);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CascadeStartC2S(BlockPos pos, int sourceMv) implements CustomPacketPayload {
        public static final Type<CascadeStartC2S> ID = new Type<>(ModRegistry.id("deck_cascade_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeStartC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.sourceMv());
                        },
                        buf -> new CascadeStartC2S(buf.readBlockPos(), buf.readVarInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CascadeResolveC2S(BlockPos pos, boolean cast) implements CustomPacketPayload {
        public static final Type<CascadeResolveC2S> ID = new Type<>(ModRegistry.id("deck_cascade_resolve"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeResolveC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeBoolean(p.cast());
                        },
                        buf -> new CascadeResolveC2S(buf.readBlockPos(), buf.readBoolean())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CascadeS2C(BlockPos pos, int sourceMv, int hitIndex, List<ItemStack> revealed)
            implements CustomPacketPayload {
        public static final Type<CascadeS2C> ID = new Type<>(ModRegistry.id("deck_cascade_overlay"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CascadeS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.sourceMv());
                            buf.writeVarInt(p.hitIndex());
                            buf.writeVarInt(p.revealed().size());
                            for (ItemStack stack : p.revealed()) {
                                ItemStack.STREAM_CODEC.encode(buf, stack);
                            }
                        },
                        buf -> {
                            BlockPos pos = buf.readBlockPos();
                            int sourceMv = buf.readVarInt();
                            int hitIndex = buf.readVarInt();
                            int size = buf.readVarInt();
                            ArrayList<ItemStack> revealed = new ArrayList<>(size);
                            for (int i = 0; i < size; i++) {
                                revealed.add(ItemStack.STREAM_CODEC.decode(buf));
                            }
                            return new CascadeS2C(pos, sourceMv, hitIndex, revealed);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

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
                            for (int i = 0; i < p.size(); i++) {
                                buf.writeVarInt(p.order()[i]);
                            }
                        },
                        buf -> {
                            BlockPos pos = buf.readBlockPos();
                            int kind = buf.readVarInt();
                            int n = buf.readVarInt();
                            int topCount = buf.readVarInt();
                            int size = buf.readVarInt();
                            int[] order = new int[size];
                            for (int i = 0; i < size; i++) {
                                order[i] = buf.readVarInt();
                            }
                            return new ResolveOrderedC2S(pos, kind, n, topCount, size, order);
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void handleAction(ActionC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel world = player.level();
            if (!(world.getBlockEntity(payload.pos()) instanceof DeckControlBlockEntity dc)) {
                return;
            }

            Action action;
            try {
                action = Action.values()[payload.actionOrdinal()];
            } catch (Throwable ignored) {
                return;
            }

            switch (action) {
                case DRAW -> dc.drawTopAndEject();
                case SHUFFLE -> dc.shuffle();
                case TOGGLE_PEEK -> dc.setPeekActive(payload.a() != 0);
                case MILL_N -> dc.millTop(Math.max(1, payload.a()));
                case SHUFFLE_GRAVEYARD_TO_LIBRARY -> dc.shuffleGraveyardIntoLibrary(player);
                case RESET_DECK -> dc.resetDeck(player);
                case REVEAL_N -> {
                    int n = Math.max(1, payload.a());
                    List<ItemStack> cards = noEmptyCopies(dc.peekTopCopies(n), n);
                    PacketDistributor.sendToPlayer(
                            player,
                            new OverlayS2C(payload.pos(), OverlayKind.REVEAL.ordinal(), cards.size(), cards)
                    );
                }
                case START_SCRY -> {
                    int n = Math.max(1, payload.a());
                    List<ItemStack> cards = noEmptyCopies(dc.peekTopCopies(n), n);
                    PacketDistributor.sendToPlayer(
                            player,
                            new OverlayS2C(payload.pos(), OverlayKind.SCRY.ordinal(), cards.size(), cards)
                    );
                }
                case RESOLVE_SCRY -> {
                    int n = Math.max(1, payload.a());
                    int keepMask = payload.b();
                    ArrayList<Integer> keepOrder = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (((keepMask >> i) & 1) != 0) {
                            keepOrder.add(i);
                        }
                    }
                    dc.resolveScry(keepOrder, false, n);
                }
                case START_SURVEIL -> {
                    int n = Math.max(1, payload.a());
                    List<ItemStack> cards = noEmptyCopies(dc.peekTopCopies(n), n);
                    PacketDistributor.sendToPlayer(
                            player,
                            new OverlayS2C(payload.pos(), OverlayKind.SURVEIL.ordinal(), cards.size(), cards)
                    );
                }
                case RESOLVE_SURVEIL -> {
                    int n = Math.max(1, payload.a());
                    int millMask = payload.b();
                    ArrayList<Integer> toMill = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (((millMask >> i) & 1) != 0) {
                            toMill.add(i);
                        }
                    }
                    dc.resolveSurveil(player, toMill, false, n);
                }
                case PLACE_N_FROM_TOP -> dc.placeFromPlayer(player, payload.a(), Math.max(1, payload.b()) - 1);
                case PLACE_BOTTOM -> dc.placeFromPlayerBottom(player, payload.a());
                case CASCADE_START -> dc.startCascade(player, payload.a());
                case CASCADE_RESOLVE -> dc.resolveCascade(player, payload.a() != 0);
            }
        });
    }

    public static void handleCascadeStart(CascadeStartC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof DeckControlBlockEntity dc) {
                dc.startCascade(player, payload.sourceMv());
            }
        });
    }

    public static void handleCascadeResolve(CascadeResolveC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof DeckControlBlockEntity dc) {
                dc.resolveCascade(player, payload.cast());
            }
        });
    }

    public static void handleResolveOrdered(ResolveOrderedC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel world = player.level();
            if (!(world.getBlockEntity(payload.pos()) instanceof DeckControlBlockEntity dc)) {
                return;
            }

            OrderedKind kind;
            try {
                kind = OrderedKind.values()[payload.kindOrdinal()];
            } catch (Throwable ignored) {
                return;
            }

            int n = Math.max(1, payload.n());
            int topCount = Math.max(0, Math.min(payload.topCount(), n));
            if (payload.size() != n) {
                return;
            }

            boolean[] seen = new boolean[n];
            for (int i = 0; i < n; i++) {
                int idx = payload.order()[i];
                if (idx < 0 || idx >= n || seen[idx]) {
                    return;
                }
                seen[idx] = true;
            }

            switch (kind) {
                case SCRY -> dc.resolveOrderedScry(n, topCount, payload.order(), false);
                case SURVEIL -> {
                    ArrayList<Integer> keepTopOrder = new ArrayList<>(topCount);
                    ArrayList<Integer> millOrder = new ArrayList<>(n - topCount);
                    for (int i = 0; i < n; i++) {
                        int idx = payload.order()[i];
                        if (i < topCount) {
                            keepTopOrder.add(idx);
                        } else {
                            millOrder.add(idx);
                        }
                    }
                    dc.resolveSurveilOrdered(player, keepTopOrder, millOrder, false, n);
                }
            }
        });
    }

    private static List<ItemStack> noEmptyCopies(List<ItemStack> in, int max) {
        ArrayList<ItemStack> out = new ArrayList<>(Math.min(max, in.size()));
        for (int i = 0; i < in.size() && out.size() < max; i++) {
            ItemStack stack = in.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);
            out.add(copy);
        }
        return out;
    }

    private DeckControlPackets() {}
}
