package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.api.CardStoreProviderRegistry;
import com.spider.mtgcard.api.TcgGameRegistry;
import com.spider.mtgcard.content.pack.cache.*;
import com.spider.mtgcard.db.search.ScryfallSyntax;
import com.spider.mtgcard.registry.ModRegistry;
import com.spider.mtgcard.util.CardStackBuilders;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CardStorePackets {
    private static boolean serverRegistered = false;
    private static final int MAX_COLLECTOR_KEY_LENGTH = 64;

    // ---------- C2S: Confirm purchase ----------
    public record ConfirmPurchaseC2S(BlockPos pos, int lineCount, List<Line> lines) implements CustomPacketPayload {
        public static final Type<ConfirmPurchaseC2S> ID = new Type<>(ModRegistry.id("cardstore_confirm"));
        public record Line(String game, String setCode, String collectorNumber, int qty) {
            public Line(String setCode, String collectorNumber, int qty) {
                this(TcgGameRegistry.MTG, setCode, collectorNumber, qty);
            }

            public Line {
                game = sanitizeGame(game);
                setCode = setCode == null ? "" : setCode.trim();
                collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
                qty = Math.max(0, qty);
            }
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmPurchaseC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.lines().size());
                            for (Line l : p.lines()) {
                                buf.writeUtf(l.game(), 128);
                                buf.writeUtf(l.setCode());
                                buf.writeUtf(l.collectorNumber(), MAX_COLLECTOR_KEY_LENGTH);
                                buf.writeVarInt(l.qty());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var lines = new ArrayList<Line>(n);
                            for (int i = 0; i < n; i++) {
                                String game = buf.readUtf(128);
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(MAX_COLLECTOR_KEY_LENGTH);
                                int qty    = buf.readVarInt();
                                lines.add(new Line(game, set, cn, qty));
                            }
                            return new ConfirmPurchaseC2S(pos, n, lines);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Save cart snapshot ----------
    public record SetCartC2S(BlockPos pos, int lineCount, List<CardStoreScreenHandler.CartEntryData> lines) implements CustomPacketPayload {
        public static final Type<SetCartC2S> ID = new Type<>(ModRegistry.id("cardstore_set_cart"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetCartC2S> CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeBlockPos(payload.pos());
                            buf.writeVarInt(payload.lines().size());
                            for (var line : payload.lines()) {
                                CardStoreScreenHandler.CartEntryData.STREAM_CODEC.encode(buf, line);
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var lines = new ArrayList<CardStoreScreenHandler.CartEntryData>(n);
                            for (int i = 0; i < n; i++) {
                                lines.add(CardStoreScreenHandler.CartEntryData.STREAM_CODEC.decode(buf));
                            }
                            return new SetCartC2S(pos, n, lines);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Remember selected game ----------
    public record SetGameC2S(BlockPos pos, String game) implements CustomPacketPayload {
        public static final Type<SetGameC2S> ID = new Type<>(ModRegistry.id("cardstore_set_game"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetGameC2S> CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeBlockPos(payload.pos());
                            buf.writeUtf(payload.game(), 128);
                        },
                        (buf) -> new SetGameC2S(buf.readBlockPos(), buf.readUtf(128))
                );

        public SetGameC2S {
            game = sanitizeGame(game);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Search (single resolve) ----------
    public record SearchC2S(BlockPos storePos, String game, String query) implements CustomPacketPayload {
        public static final Type<SearchC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchC2S> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SearchC2S::storePos,
                ByteBufCodecs.STRING_UTF8, SearchC2S::game,
                ByteBufCodecs.STRING_UTF8, SearchC2S::query,
                SearchC2S::new
        );

        public SearchC2S {
            game = sanitizeGame(game);
            query = query == null ? "" : query;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Search result (single) ----------
    public record SearchS2C(
            BlockPos storePos,
            String game,
            boolean ok,
            String name,
            String setCode,
            String collectorNumber,
            String message,
            boolean hasPreview,
            ItemStack preview
    ) implements CustomPacketPayload {

        public static final Type<SearchS2C> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_result"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.name());
                            buf.writeUtf(p.setCode());
                            buf.writeUtf(p.collectorNumber());
                            buf.writeUtf(p.message());

                            buf.writeBoolean(p.hasPreview());
                            if (p.hasPreview()) {
                                ItemStack.STREAM_CODEC.encode(buf, p.preview());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String game = buf.readUtf(128);
                            boolean ok = buf.readBoolean();
                            String name = buf.readUtf();
                            String set = buf.readUtf();
                            String cn = buf.readUtf();
                            String msg = buf.readUtf();

                            boolean has = buf.readBoolean();
                            ItemStack st = has ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;

                            return new SearchS2C(pos, game, ok, name, set, cn, msg, has, st);
                        }
                );

        public SearchS2C {
            game = sanitizeGame(game);
            name = name == null ? "" : name;
            setCode = setCode == null ? "" : setCode.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            message = message == null ? "" : message;
            preview = preview == null ? ItemStack.EMPTY : preview.copyWithCount(1);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Search prints (paged + streamed) ----------
    public record SearchPrintsC2S(BlockPos storePos, String game, String query, int page, int pageSize, UUID requestId) implements CustomPacketPayload {
        public static final Type<SearchPrintsC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeUtf(p.query());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.pageSize());
                            buf.writeUUID(p.requestId());
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String game = buf.readUtf(128);
                            String q = buf.readUtf();
                            int page = buf.readVarInt();
                            int pageSize = buf.readVarInt();
                            UUID req = buf.readUUID();
                            return new SearchPrintsC2S(pos, game, q, page, pageSize, req);
                        }
                );

        public SearchPrintsC2S {
            game = sanitizeGame(game);
            query = query == null ? "" : query;
            page = Math.max(1, page);
            pageSize = Math.max(1, pageSize);
            requestId = requestId == null ? new UUID(0L, 0L) : requestId;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: prints start ----------
    public record SearchPrintsStartS2C(
            BlockPos storePos,
            String game,
            UUID requestId,
            boolean ok,
            String message,
            int page,
            int total,
            boolean hasMore,
            String priceItemId,
            String priceBasis
    ) implements CustomPacketPayload {
        public static final Type<SearchPrintsStartS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints_start"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsStartS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeUUID(p.requestId());
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message() == null ? "" : p.message());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());
                            buf.writeUtf(p.priceItemId() == null ? "" : p.priceItemId());
                            buf.writeUtf(p.priceBasis() == null ? "" : p.priceBasis());
                        },
                        (buf) -> new SearchPrintsStartS2C(
                                buf.readBlockPos(),
                                buf.readUtf(128),
                                buf.readUUID(),
                                buf.readBoolean(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean(),
                                buf.readUtf(),
                                buf.readUtf()
                        )
                );

        public SearchPrintsStartS2C {
            game = sanitizeGame(game);
            requestId = requestId == null ? new UUID(0L, 0L) : requestId;
            message = message == null ? "" : message;
            priceItemId = priceItemId == null ? "" : priceItemId;
            priceBasis = priceBasis == null ? "" : priceBasis;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: prints add entry ----------
    public record SearchPrintsAddS2C(BlockPos storePos, UUID requestId, SearchPrintsS2C.Entry entry) implements CustomPacketPayload {
        public static final Type<SearchPrintsAddS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints_add"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsAddS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUUID(p.requestId());
                            SearchPrintsS2C.Entry.CODEC.encode(buf, p.entry());
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            UUID req = buf.readUUID();
                            var entry = SearchPrintsS2C.Entry.CODEC.decode(buf);
                            return new SearchPrintsAddS2C(pos, req, entry);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: prints done ----------
    public record SearchPrintsDoneS2C(
            BlockPos storePos,
            String game,
            UUID requestId,
            boolean ok,
            String message,
            int page,
            int total,
            boolean hasMore
    ) implements CustomPacketPayload {
        public static final Type<SearchPrintsDoneS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints_done"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsDoneS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeUUID(p.requestId());
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message() == null ? "" : p.message());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());
                        },
                        (buf) -> new SearchPrintsDoneS2C(
                                buf.readBlockPos(),
                                buf.readUtf(128),
                                buf.readUUID(),
                                buf.readBoolean(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean()
                        )
                );

        public SearchPrintsDoneS2C {
            game = sanitizeGame(game);
            requestId = requestId == null ? new UUID(0L, 0L) : requestId;
            message = message == null ? "" : message;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Search prints results (GRID) ----------
    public record SearchPrintsS2C(
            BlockPos storePos,
            String game,
            boolean ok,
            String message,
            String canonicalName,
            int page,
            int total,
            boolean hasMore,
            String priceItemId,
            String priceBasis,
            List<Entry> entries
    ) implements CustomPacketPayload {

        public static final Type<SearchPrintsS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints_result"));

        public record Entry(String game, String setCode, String collectorNumber, ItemStack stack, long priceItems) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC =
                    StreamCodec.of(
                            (buf, e) -> {
                                buf.writeUtf(e.game(), 128);
                                buf.writeUtf(e.setCode());
                                buf.writeUtf(e.collectorNumber(), MAX_COLLECTOR_KEY_LENGTH);
                                ItemStack.STREAM_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            },
                            (buf) -> {
                                String game = buf.readUtf(128);
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(MAX_COLLECTOR_KEY_LENGTH);
                                ItemStack st = ItemStack.STREAM_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                return new Entry(game, set, cn, st, price);
                            }
                    );

            public Entry(String setCode, String collectorNumber, ItemStack stack, long priceItems) {
                this(TcgGameRegistry.MTG, setCode, collectorNumber, stack, priceItems);
            }

            public Entry {
                game = sanitizeGame(game);
                setCode = setCode == null ? "" : setCode.trim();
                collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
                stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
                priceItems = Math.max(0L, priceItems);
            }
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message());
                            buf.writeUtf(p.canonicalName());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());

                            buf.writeUtf(p.priceItemId() == null ? "" : p.priceItemId());
                            buf.writeUtf(p.priceBasis() == null ? "" : p.priceBasis());

                            buf.writeVarInt(p.entries().size());
                            for (Entry e : p.entries()) Entry.CODEC.encode(buf, e);
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String game = buf.readUtf(128);
                            boolean ok = buf.readBoolean();
                            String msg = buf.readUtf();
                            String name = buf.readUtf();
                            int page = buf.readVarInt();
                            int total = buf.readVarInt();
                            boolean hasMore = buf.readBoolean();

                            String priceItemId = buf.readUtf();
                            String priceBasis  = buf.readUtf();

                            int n = buf.readVarInt();
                            var list = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) list.add(Entry.CODEC.decode(buf));

                            return new SearchPrintsS2C(pos, game, ok, msg, name, page, total, hasMore, priceItemId, priceBasis, list);
                        }
                );

        public SearchPrintsS2C {
            game = sanitizeGame(game);
            message = message == null ? "" : message;
            canonicalName = canonicalName == null ? "" : canonicalName;
            priceItemId = priceItemId == null ? "" : priceItemId;
            priceBasis = priceBasis == null ? "" : priceBasis;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Import Deck ----------
    public record ImportDeckC2S(BlockPos pos, String game, int lineCount, List<Line> lines) implements CustomPacketPayload {
        public static final Type<ImportDeckC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "cardstore_import_deck"));

        public record Line(String set, String cn, int qty) {}

        public static final StreamCodec<RegistryFriendlyByteBuf, ImportDeckC2S> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ImportDeckC2S::pos,
                        ByteBufCodecs.STRING_UTF8, ImportDeckC2S::game,
                        ByteBufCodecs.VAR_INT, ImportDeckC2S::lineCount,
                        StreamCodec.composite(
                                ByteBufCodecs.STRING_UTF8, Line::set,
                                ByteBufCodecs.STRING_UTF8, Line::cn,
                                ByteBufCodecs.VAR_INT, Line::qty,
                                Line::new
                        ).apply(ByteBufCodecs.list()),
                        ImportDeckC2S::lines,
                        ImportDeckC2S::new
                );

        public ImportDeckC2S(BlockPos pos, int lineCount, List<Line> lines) {
            this(pos, TcgGameRegistry.MTG, lineCount, lines);
        }

        public ImportDeckC2S {
            game = sanitizeGame(game);
            lines = lines == null ? List.of() : List.copyOf(lines);
            lineCount = lines.size();
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Import Deck result ----------
    public record ImportDeckS2C(BlockPos pos, String game, boolean ok, String message, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<ImportDeckS2C> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "cardstore_import_deck_result"));

        public record Entry(String game, String set, String cn, int qty, ItemStack stack, long priceItems) {
            public Entry(String set, String cn, int qty, ItemStack stack, long priceItems) {
                this(TcgGameRegistry.MTG, set, cn, qty, stack, priceItems);
            }

            public Entry {
                game = sanitizeGame(game);
                set = set == null ? "" : set.trim();
                cn = cn == null ? "" : cn.trim();
                qty = Math.max(1, qty);
                stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
                priceItems = Math.max(0L, priceItems);
            }
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ImportDeckS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeUtf(p.game(), 128);
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message() == null ? "" : p.message());

                            buf.writeVarInt(p.entries().size());
                            for (Entry e : p.entries()) {
                                buf.writeUtf(e.game(), 128);
                                buf.writeUtf(e.set());
                                buf.writeUtf(e.cn(), MAX_COLLECTOR_KEY_LENGTH);
                                buf.writeVarInt(e.qty());
                                ItemStack.STREAM_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String game = buf.readUtf(128);
                            boolean ok = buf.readBoolean();
                            String msg = buf.readUtf();

                            int n = buf.readVarInt();
                            var entries = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                String entryGame = buf.readUtf(128);
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(MAX_COLLECTOR_KEY_LENGTH);
                                int qty    = buf.readVarInt();
                                ItemStack st = ItemStack.STREAM_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                entries.add(new Entry(entryGame, set, cn, qty, st, price));
                            }

                            return new ImportDeckS2C(pos, game, ok, msg, entries);
                        }
                );

        public ImportDeckS2C(BlockPos pos, boolean ok, String message, List<Entry> entries) {
            this(pos, TcgGameRegistry.MTG, ok, message, entries);
        }

        public ImportDeckS2C {
            game = sanitizeGame(game);
            message = message == null ? "" : message;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- CODEC registration ----------
    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(ConfirmPurchaseC2S.ID, ConfirmPurchaseC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCartC2S.ID, SetCartC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SetGameC2S.ID, SetGameC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(SearchC2S.ID, SearchC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchS2C.ID, SearchS2C.CODEC);

        PayloadTypeRegistry.playC2S().register(SearchPrintsC2S.ID, SearchPrintsC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchPrintsS2C.ID, SearchPrintsS2C.CODEC);

        PayloadTypeRegistry.playS2C().register(SearchPrintsStartS2C.ID, SearchPrintsStartS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchPrintsAddS2C.ID,   SearchPrintsAddS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(SearchPrintsDoneS2C.ID,  SearchPrintsDoneS2C.CODEC);

        PayloadTypeRegistry.playC2S().register(ImportDeckC2S.ID, ImportDeckC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(ImportDeckS2C.ID, ImportDeckS2C.CODEC);
    }

    // ---------- Server receivers ----------
    public static void registerServer() {
        if (serverRegistered) return;
        serverRegistered = true;

        ServerPlayNetworking.registerGlobalReceiver(ConfirmPurchaseC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    ServerLevel world = (ServerLevel) sp.level();

                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof CardStoreBlockEntity store)) return;

                    store.clearSavedCart(sp.getUUID());
                    CardStorePurchaseService.handleConfirm(ctx.server(), sp, store, payload.lines());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetCartC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerPlayer player = ctx.player();
                    ServerLevel world = player.level();

                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof CardStoreBlockEntity store)) return;

                    store.setSavedCart(player.getUUID(), payload.lines());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(SetGameC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    ServerPlayer player = ctx.player();
                    ServerLevel world = player.level();

                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof CardStoreBlockEntity store)) return;

                    String game = sanitizeGame(payload.game());
                    if (!CardStoreProviderRegistry.containsProvider(game)) return;
                    store.setSelectedGame(player.getUUID(), game);
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ImportDeckC2S.ID, (payload, ctx) -> {
            var server = ctx.server();
            server.execute(() -> {
                ServerPlayer player = ctx.player();
                ServerLevel world = player.level();
                String game = sanitizeGame(payload.game());

                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof CardStoreBlockEntity store)) {
                    ServerPlayNetworking.send(player,
                            new ImportDeckS2C(payload.pos(), game, false, "Card Store not found.", List.of()));
                    return;
                }

                if (!CardStoreProviderRegistry.containsProvider(game)) {
                    ServerPlayNetworking.send(player,
                            new ImportDeckS2C(payload.pos(), game, false, "No Card Store provider for this game.", List.of()));
                    return;
                }

                store.setSelectedGame(player.getUUID(), game);

                if (!TcgGameRegistry.MTG.equals(game)) {
                    CardStoreProviderRegistry.Provider provider = CardStoreProviderRegistry.get(game).orElse(null);
                    if (provider == null) {
                        ServerPlayNetworking.send(player,
                                new ImportDeckS2C(payload.pos(), game, false, "No Card Store provider for this game.", List.of()));
                        return;
                    }

                    provider.importDeck(
                                    new CardStoreProviderRegistry.SearchContext(server, world, player, store, payload.pos()),
                                    toProviderImportLines(payload.lines())
                            )
                            .exceptionally(err -> CardStoreProviderRegistry.ImportDeckResult.empty("Deck import failed."))
                            .whenComplete((result, ex) -> server.execute(() -> {
                                CardStoreProviderRegistry.ImportDeckResult safe = result == null
                                        ? CardStoreProviderRegistry.ImportDeckResult.empty("Deck import failed.")
                                        : result;
                                ArrayList<ImportDeckS2C.Entry> entries = new ArrayList<>();
                                for (var entry : safe.entries()) {
                                    entries.add(new ImportDeckS2C.Entry(
                                            game,
                                            entry.set(),
                                            entry.collectorNumber(),
                                            entry.qty(),
                                            entry.stack(),
                                            entry.priceItems()
                                    ));
                                }
                                ServerPlayNetworking.send(player,
                                        new ImportDeckS2C(payload.pos(), game, safe.ok(), safe.message(), entries));
                            }));
                    return;
                }

                ArrayList<CompletableFuture<ImportDeckS2C.Entry>> futures = new ArrayList<>();

                for (var line : payload.lines()) {
                    final String set = (line.set() == null ? "" : line.set().trim());
                    final String cn  = (line.cn() == null  ? "" : line.cn().trim());
                    final int qty    = Math.max(1, line.qty());

                    if (set.isBlank() || cn.isBlank()) continue;

                    CompletableFuture<ImportDeckS2C.Entry> fCustom =
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    var customStore = com.spider.mtgcard.content.pack.custom.CustomCardStores.get(server);

                                    com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta meta = null;
                                    for (var m : customStore.all()) {
                                        if (m == null) continue;
                                        if (m.id != null && m.id.equalsIgnoreCase(cn)) {
                                            meta = m;
                                            break;
                                        }
                                    }
                                    if (meta == null) return null;

                                    ItemStack st = CardStackBuilders.buildCustomStackFromMeta(meta, false);
                                    if (st == null || st.isEmpty()) return null;

                                    // (optional) ensure getName() is good everywhere
                                    applyItemNameIfPresent(st, meta.name);

                                    long priceItems = com.spider.mtgcard.util.CustomCardPricing.priceItemsForRarity(meta.rarity);
                                    String setCode = (meta.set == null || meta.set.isBlank()) ? "CSTM" : meta.set;

                                    return new ImportDeckS2C.Entry(
                                            game,
                                            setCode,
                                            meta.id,
                                            qty,
                                            st.copyWithCount(1),
                                            priceItems
                                    );
                                } catch (Throwable t) {
                                    return null;
                                }
                            });

                    CompletableFuture<ImportDeckS2C.Entry> fScry =
                            ScryfallExactFetch.fetchBySetCollectorAsync(world, set.toLowerCase(Locale.ROOT), cn)
                                    .thenApply(model -> {
                                        if (model == null) return null;

                                        ItemStack st = CardStackBuilders.buildScryfallStackFromModel(model, false);
                                        if (st == null || st.isEmpty()) return null;

                                        long priceItems = CardStorePrice.toCurrencyItemsFromStrings(
                                                false,
                                                model.price.usd, model.price.usdFoil, model.price.usdEtched,
                                                model.price.eur, model.price.eurFoil,
                                                model.price.tix
                                        );

                                        return new ImportDeckS2C.Entry(
                                                game, model.set, model.collectorNumber, qty,
                                                st.copyWithCount(1),
                                                priceItems
                                        );
                                    })
                                    .exceptionally(err -> null);

                    futures.add(fCustom.thenCompose(customHit ->
                            customHit != null ? CompletableFuture.completedFuture(customHit) : fScry
                    ));
                }

                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .whenComplete((v, ex) -> server.execute(() -> {
                            ArrayList<ImportDeckS2C.Entry> out = new ArrayList<>();
                            for (var f : futures) {
                                var e = f.getNow(null);
                                if (e != null) out.add(e);
                            }

                            boolean ok = !out.isEmpty();
                            String msg = ok
                                    ? ("Imported " + out.size() + " resolved lines.")
                                    : "Imported 0 lines (no matches). Check set/cn values.";

                            ServerPlayNetworking.send(player,
                                    new ImportDeckS2C(payload.pos(), game, ok, msg, out));
                        }));
            });
        });

        // ---------------------------------------------------------------------
        // SearchC2S = single resolve ONLY (fills resolveField + preview card)
        // FIX: custom now builds from META so mtg_meta.name is present (no more ---)
        // ---------------------------------------------------------------------
        ServerPlayNetworking.registerGlobalReceiver(SearchC2S.ID, (payload, ctx) -> {
            var server = ctx.server();
            ServerPlayer player = ctx.player();
            ServerLevel serverWorld = player.level();
            String game = sanitizeGame(payload.game());

            BlockEntity be = serverWorld.getBlockEntity(payload.storePos());
            if (!(be instanceof CardStoreBlockEntity store)) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchS2C(payload.storePos(), game, false, "", "", "", "Card Store not found.", false, ItemStack.EMPTY)));
                return;
            }

            if (!CardStoreProviderRegistry.containsProvider(game)) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchS2C(payload.storePos(), game, false, "", "", "", "No Card Store provider for this game.", false, ItemStack.EMPTY)));
                return;
            }

            String q = payload.query();
            if (q == null || q.trim().isEmpty()) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchS2C(payload.storePos(), game, false, "", "", "", "Type a card name.", false, ItemStack.EMPTY)));
                return;
            }

            String trimmed = q.trim();
            if (!TcgGameRegistry.MTG.equals(game)) {
                CardStoreProviderRegistry.Provider provider = CardStoreProviderRegistry.get(game).orElse(null);
                if (provider == null) {
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchS2C(payload.storePos(), game, false, "", "", "", "No Card Store provider for this game.", false, ItemStack.EMPTY)));
                    return;
                }

                provider.search(new CardStoreProviderRegistry.SearchContext(server, serverWorld, player, store, payload.storePos()), trimmed)
                        .exceptionally(err -> CardStoreProviderRegistry.SearchResult.noMatch("Search failed."))
                        .whenComplete((result, ex) -> server.execute(() -> {
                            CardStoreProviderRegistry.SearchResult safe = result == null
                                    ? CardStoreProviderRegistry.SearchResult.noMatch("Search failed.")
                                    : result;
                            ItemStack preview = safe.preview();
                            boolean hasPreview = preview != null && !preview.isEmpty();
                            ServerPlayNetworking.send(player, new SearchS2C(
                                    payload.storePos(),
                                    game,
                                    safe.ok(),
                                    safe.name(),
                                    safe.setCode(),
                                    safe.collectorNumber(),
                                    safe.message(),
                                    hasPreview,
                                    hasPreview ? preview : ItemStack.EMPTY
                            ));
                        }));
                return;
            }

            String qLower = trimmed.toLowerCase(Locale.ROOT);

            // ---- CUSTOM FIRST ----
            try {
                var custom = com.spider.mtgcard.content.pack.custom.CustomCardStores.get(server);
                com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta hit = null;

                for (var m : custom.all()) {
                    if (m != null && m.name != null && m.name.toLowerCase(Locale.ROOT).contains(qLower)) {
                        hit = m;
                        break;
                    }
                }

                if (hit != null) {
                    // ✅ IMPORTANT: build from meta so name/type/rarity/etc are written into mtg_meta
                    ItemStack st = CardStackBuilders.buildCustomStackFromMeta(hit, false);
                    if (st != null && !st.isEmpty()) {
                        applyItemNameIfPresent(st, hit.name);
                    }

                    String setCode = (hit.set == null || hit.set.isBlank()) ? "CSTM" : hit.set;

                    var finalHit = hit;
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchS2C(payload.storePos(), game, true,
                                    finalHit.name, setCode, finalHit.id,
                                    "Custom ✓", true, st)));
                    return;
                }
            } catch (Throwable ignored) {}

            // ---- FALL BACK TO SCRYFALL ----
            ScryfallNamedFetch.fetchNamedFuzzyAsync(trimmed).whenComplete((hit, ex) -> {
                if (ex != null || hit == null) {
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchS2C(payload.storePos(), game, false, "", "", "", "No match found.", false, ItemStack.EMPTY)));
                    return;
                }

                ServerLevel world = (ServerLevel) player.level();

                ScryfallExactFetch.fetchBySetCollectorAsync(world, hit.set(), hit.collectorNumber())
                        .whenComplete((card, ex2) -> server.execute(() -> {
                            if (ex2 != null || card == null) {
                                ServerPlayNetworking.send(player,
                                        new SearchS2C(payload.storePos(), game, false, "", "", "",
                                                "Resolved name, but failed exact printing.", false, ItemStack.EMPTY));
                                return;
                            }

                            ItemStack template = CardStackBuilders.buildScryfallStackFromModel(card, false);
                            boolean has = template != null && !template.isEmpty();

                            ServerPlayNetworking.send(player,
                                    new SearchS2C(
                                            payload.storePos(),
                                            game,
                                            true,
                                            card.name,
                                            hit.set(),
                                            hit.collectorNumber(),
                                            "Resolved ✓",
                                            has,
                                            has ? template : ItemStack.EMPTY
                                    ));
                        }));
            });
        });

        // ---------------------------------------------------------------------
        // SearchPrintsC2S = GRID search (always /cards/search unique=prints)
        // Customs streamed first so they always show.
        // ---------------------------------------------------------------------
        ServerPlayNetworking.registerGlobalReceiver(SearchPrintsC2S.ID, (payload, ctx) -> {
            var server = ctx.server();
            ServerPlayer player = ctx.player();
            ServerLevel serverWorld = player.level();
            String game = sanitizeGame(payload.game());

            String q = payload.query();
            int page = Math.max(1, payload.page());
            int pageSize = Math.max(1, Math.min(payload.pageSize(), 175));
            UUID reqId = payload.requestId();

            BlockEntity be = serverWorld.getBlockEntity(payload.storePos());
            if (!(be instanceof CardStoreBlockEntity store)) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsStartS2C(payload.storePos(), game, reqId, false, "Card Store not found.", page, 0, false, currentPriceItemId(), currentPriceBasis())));
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsDoneS2C(payload.storePos(), game, reqId, false, "Card Store not found.", page, 0, false)));
                return;
            }

            if (!CardStoreProviderRegistry.containsProvider(game)) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsStartS2C(payload.storePos(), game, reqId, false, "No Card Store provider for this game.", page, 0, false, currentPriceItemId(), currentPriceBasis())));
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsDoneS2C(payload.storePos(), game, reqId, false, "No provider.", page, 0, false)));
                return;
            }

            if (q == null || q.trim().isEmpty()) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsStartS2C(payload.storePos(), game, reqId, false, "Type a card name.", page, 0, false, currentPriceItemId(), currentPriceBasis())));
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsDoneS2C(payload.storePos(), game, reqId, false, "No query.", page, 0, false)));
                return;
            }

            String qTrim = q.trim();
            if (!TcgGameRegistry.MTG.equals(game)) {
                CardStoreProviderRegistry.Provider provider = CardStoreProviderRegistry.get(game).orElse(null);
                if (provider == null) {
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchPrintsStartS2C(payload.storePos(), game, reqId, false, "No Card Store provider for this game.", page, 0, false, currentPriceItemId(), currentPriceBasis())));
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchPrintsDoneS2C(payload.storePos(), game, reqId, false, "No provider.", page, 0, false)));
                    return;
                }

                provider.searchPrints(new CardStoreProviderRegistry.SearchContext(server, serverWorld, player, store, payload.storePos()), qTrim, page, pageSize)
                        .exceptionally(err -> CardStoreProviderRegistry.SearchPrintsResult.empty("Search failed.", page))
                        .whenComplete((result, ex) -> server.execute(() -> {
                            CardStoreProviderRegistry.SearchPrintsResult safe = result == null
                                    ? CardStoreProviderRegistry.SearchPrintsResult.empty("Search failed.", page)
                                    : result;
                            ServerPlayNetworking.send(player,
                                    new SearchPrintsStartS2C(payload.storePos(), game, reqId, true, safe.message(), page,
                                            safe.total(), safe.hasMore(), currentPriceItemId(), currentPriceBasis()));
                            int sent = 0;
                            for (var entry : safe.entries()) {
                                ServerPlayNetworking.send(player, new SearchPrintsAddS2C(
                                        payload.storePos(),
                                        reqId,
                                        new SearchPrintsS2C.Entry(game, entry.setCode(), entry.collectorNumber(), entry.stack(), entry.priceItems())
                                ));
                                sent++;
                            }
                            String doneMsg = !safe.message().isBlank()
                                    ? safe.message()
                                    : (sent > 0 ? ("Loaded " + sent + " results.") : "No results.");
                            ServerPlayNetworking.send(player,
                                    new SearchPrintsDoneS2C(payload.storePos(), game, reqId, sent > 0, doneMsg, page,
                                            safe.total(), safe.hasMore()));
                        }));
                return;
            }

            String qLower = qTrim.toLowerCase(Locale.ROOT);
            String scryQ = qLower.contains("include:") ? qTrim : (qTrim + " include:extras");
            int offset = (page - 1) * pageSize;

            List<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> customMatches =
                    findCustomMatches(server, qTrim);
            int customTotal = customMatches.size();
            int customStart = Math.min(offset, customTotal);
            int customSendCount = Math.min(pageSize, Math.max(0, customTotal - offset));
            List<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> customPage =
                    customSendCount <= 0
                            ? List.of()
                            : customMatches.subList(customStart, customStart + customSendCount);

            int officialOffset = Math.max(0, offset - customTotal);
            int officialLimit = Math.max(0, pageSize - customPage.size());

            CompletableFuture<ScryfallPrintSearchFetch.Page> officialFuture =
                    (officialLimit <= 0)
                            ? CompletableFuture.completedFuture(new ScryfallPrintSearchFetch.Page(List.of(), 0, false))
                            : ScryfallPrintSearchFetch.fetchSearchOffsetSliceAsync(scryQ, officialOffset, officialLimit);

            officialFuture
                    .whenComplete((pg, ex2) -> {
                        boolean scryOk = (ex2 == null && pg != null && pg.hits() != null);

                        int officialTotal = (scryOk ? pg.totalCards() : 0);
                        int total = customTotal + officialTotal;
                        boolean hasMore = total > (offset + pageSize);

                        server.execute(() -> ServerPlayNetworking.send(player,
                                new SearchPrintsStartS2C(payload.storePos(), game, reqId, true, "Searching…", page, total, hasMore, currentPriceItemId(), currentPriceBasis())));

                        ArrayList<SearchPrintsS2C.Entry> customEntries = buildCustomEntries(game, customPage);
                        server.execute(() -> {
                            for (var entry : customEntries) {
                                ServerPlayNetworking.send(player, new SearchPrintsAddS2C(payload.storePos(), reqId, entry));
                            }
                        });

                        int sent = customEntries.size();

                        if (!scryOk || pg.hits().isEmpty()) {
                            String doneMsg = (sent > 0) ? ("Loaded " + sent + " results.") : "No results.";
                            server.execute(() -> ServerPlayNetworking.send(player,
                                    new SearchPrintsDoneS2C(payload.storePos(), game, reqId, sent > 0, doneMsg, page, total, hasMore)));
                            return;
                        }

                        ArrayList<ScryfallPrintSearchFetch.PrintHit> pageHits = new ArrayList<>(pg.hits());
                        ScryfallExactFetch.fetchCollectionByPrintHitsAsync(pageHits)
                                .exceptionally(err -> List.of())
                                .whenComplete((models, ex3) -> {
                                    ArrayList<SearchPrintsS2C.Entry> entries = buildSearchEntries(game, pageHits, models);

                                    server.execute(() -> {
                                        for (var entry : entries) {
                                            ServerPlayNetworking.send(player,
                                                    new SearchPrintsAddS2C(payload.storePos(), reqId, entry));
                                        }

                                        int totalSent = sent + entries.size();
                                        String doneMsg = (totalSent > 0)
                                                ? ("Loaded " + totalSent + " results.")
                                                : "No results.";
                                        ServerPlayNetworking.send(player,
                                                new SearchPrintsDoneS2C(payload.storePos(), game, reqId, totalSent > 0, doneMsg, page, total, hasMore));
                                    });
                                });
                    });
        });
    }

    private static String currentPriceItemId() {
        return CardStoreScreenHandler.defaultPriceItemId();
    }

    private static String currentPriceBasis() {
        return CardStoreScreenHandler.defaultPriceBasis();
    }

    // -------------------------
    // Small helper: make getName() reliable without stomping your rarity-colored CUSTOM_NAME
    // -------------------------
    private static void applyItemNameIfPresent(ItemStack st, String name) {
        if (st == null || st.isEmpty()) return;
        if (name == null || name.isBlank()) return;

        // Don’t overwrite if something already set it
        if (!st.has(DataComponents.ITEM_NAME)) {
            st.set(DataComponents.ITEM_NAME, Component.literal(name));
        }
        // Keep your CardStackBuilders rarity-colored custom name; only set if absent.
        if (!st.has(DataComponents.CUSTOM_NAME)) {
            st.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    // -------------------------
    // Custom streaming helpers
    // -------------------------

    private static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).trim();
        t = t.replace('’', '\'');
        t = t.replaceAll("[^a-z0-9' ]+", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private static List<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> findCustomMatches(
            MinecraftServer server,
            String queryRaw
    ) {
        try {
            var store = com.spider.mtgcard.content.pack.custom.CustomCardStores.get(server);
            ArrayList<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> matches = new ArrayList<>();
            for (var meta : store.all()) {
                if (!matchesCustomSearch(meta, queryRaw)) continue;
                matches.add(meta);
            }
            matches.sort((a, b) -> {
                String an = a != null && a.name != null ? a.name : "";
                String bn = b != null && b.name != null ? b.name : "";
                int byName = String.CASE_INSENSITIVE_ORDER.compare(an, bn);
                if (byName != 0) return byName;
                String aid = a != null && a.id != null ? a.id : "";
                String bid = b != null && b.id != null ? b.id : "";
                return String.CASE_INSENSITIVE_ORDER.compare(aid, bid);
            });
            return matches;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static boolean matchesCustomSearch(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta meta, String queryRaw) {
        if (meta == null) return false;

        String query = (queryRaw == null) ? "" : queryRaw.trim();
        if (query.isBlank()) return false;

        return ScryfallSyntax.parse(query).matches(new CustomCardView(meta));
    }

    private static final class CustomCardView implements ScryfallSyntax.CardView {
        private final com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta meta;

        CustomCardView(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta meta) {
            this.meta = meta;
        }

        @Override public String name() { return nz(meta == null ? null : meta.name); }
        @Override public String otherNames() { return nz(meta == null ? null : meta.backName); }
        @Override public String set() { return nz(meta == null ? null : meta.set); }
        @Override public String collectorNumber() { return nz(meta == null ? null : meta.id); }
        @Override public String rarity() { return nz(meta == null ? null : meta.rarity); }
        @Override public String manaCost() { return nz(meta == null ? null : meta.manaCost); }
        @Override public String typeLine() {
            return joinSearchText(meta == null ? null : meta.typeLine, meta == null ? null : meta.backTypeLine);
        }
        @Override public String oracleText() {
            return joinSearchText(meta == null ? null : meta.oracleText, meta == null ? null : meta.backOracleText);
        }
        @Override public String power() { return nz(meta == null ? null : meta.power); }
        @Override public String toughness() { return nz(meta == null ? null : meta.toughness); }
        @Override public String loyalty() { return nz(meta == null ? null : meta.loyalty); }
        @Override public String layout() { return meta != null && meta.doubleFaced ? "custom_dfc" : "custom"; }
        @Override public int manaValue() { return estimateManaValue(meta == null ? null : meta.manaCost); }
        @Override public java.util.Set<String> colors() {
            return ScryfallSyntax.colorsFromManaCost(meta == null ? null : meta.manaCost);
        }
        @Override public java.util.Set<String> colorIdentity() {
            return ScryfallSyntax.colorsFromManaCost(meta == null ? null : meta.manaCost);
        }
        @Override public boolean tokenLike() {
            return typeHasAny(typeLine(), "token", "emblem", "dungeon", "attraction", "sticker",
                    "contraption", "scheme", "plane", "phenomenon", "vanguard");
        }
        @Override public boolean legendary() { return typeHasAny(typeLine(), "legendary"); }
        @Override public boolean doubleFaced() { return meta != null && meta.doubleFaced; }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String joinSearchText(String... values) {
        StringBuilder out = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(value);
            }
        }
        return out.toString();
    }

    private static boolean typeHasAny(String typeLine, String... words) {
        if (typeLine == null || typeLine.isBlank() || words == null) return false;
        String lower = typeLine.toLowerCase(Locale.ROOT);
        for (String part : lower.split("[^a-z0-9]+")) {
            for (String word : words) {
                if (word != null && part.equals(word.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private static int estimateManaValue(String manaCost) {
        if (manaCost == null || manaCost.isBlank()) return 0;
        int total = 0;
        String s = manaCost.trim();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '{') {
                int end = s.indexOf('}', i + 1);
                if (end < 0) break;
                total += manaSymbolValue(s.substring(i + 1, end));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(ch)) {
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                total += parseManaNumber(s.substring(start, i));
                continue;
            }
            if ("WUBRGC".indexOf(Character.toUpperCase(ch)) >= 0) total++;
            i++;
        }
        return Math.max(0, total);
    }

    private static int manaSymbolValue(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (s.isBlank() || s.equals("X") || s.equals("Y") || s.equals("Z")) return 0;
        if (s.contains("/")) return 1;
        if ("WUBRGC".contains(s)) return 1;
        return parseManaNumber(s);
    }

    private static int parseManaNumber(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static ArrayList<SearchPrintsS2C.Entry> buildSearchEntries(
            String game,
            List<ScryfallPrintSearchFetch.PrintHit> requestedHits,
            List<ScryfallModels.Card> models
    ) {
        ArrayList<SearchPrintsS2C.Entry> entries = new ArrayList<>();
        if (requestedHits == null || requestedHits.isEmpty() || models == null || models.isEmpty()) return entries;

        Map<String, ScryfallModels.Card> byPrinting = new HashMap<>();
        for (var model : models) {
            if (model == null || model.set == null || model.collectorNumber == null) continue;
            byPrinting.put(printingKey(model.set, model.collectorNumber), model);
        }

        for (var hit : requestedHits) {
            if (hit == null) continue;

            ScryfallModels.Card model = byPrinting.get(printingKey(hit.set(), hit.collectorNumber()));
            if (model == null) continue;

            ItemStack stack = CardStackBuilders.buildScryfallStackFromModel(model, false);
            if (stack == null || stack.isEmpty()) continue;

            long priceItems = CardStorePrice.toCurrencyItemsFromStrings(
                    false,
                    model.price.usd, model.price.usdFoil, model.price.usdEtched,
                    model.price.eur, model.price.eurFoil,
                    model.price.tix
            );

            entries.add(new SearchPrintsS2C.Entry(game, model.set, model.collectorNumber, stack, priceItems));
        }

        return entries;
    }

    private static ArrayList<SearchPrintsS2C.Entry> buildCustomEntries(
            String game,
            List<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> metas
    ) {
        ArrayList<SearchPrintsS2C.Entry> entries = new ArrayList<>();
        if (metas == null || metas.isEmpty()) return entries;

        for (var meta : metas) {
            if (meta == null) continue;

            ItemStack st = CardStackBuilders.buildCustomStackFromMeta(meta, false);
            if (st == null || st.isEmpty()) continue;

            applyItemNameIfPresent(st, meta.name);

            long price = com.spider.mtgcard.util.CustomCardPricing.priceItemsForRarity(meta.rarity);
            String setCode = (meta.set == null || meta.set.isBlank()) ? "CSTM" : meta.set;
            entries.add(new SearchPrintsS2C.Entry(game, setCode, meta.id, st, price));
        }

        return entries;
    }

    private static String printingKey(String set, String collectorNumber) {
        return norm(set) + "\u0000" + norm(collectorNumber);
    }

    private static List<CardStoreProviderRegistry.ImportLine> toProviderImportLines(List<ImportDeckC2S.Line> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        ArrayList<CardStoreProviderRegistry.ImportLine> out = new ArrayList<>();
        for (var line : lines) {
            if (line == null) continue;
            String set = line.set() == null ? "" : line.set().trim();
            String cn = line.cn() == null ? "" : line.cn().trim();
            int qty = Math.max(1, line.qty());
            if (set.isBlank() || cn.isBlank()) continue;
            out.add(new CardStoreProviderRegistry.ImportLine(set, cn, qty));
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static String sanitizeGame(String game) {
        return CardStoreProviderRegistry.sanitizeGameId(game);
    }

    private CardStorePackets() {}
}
