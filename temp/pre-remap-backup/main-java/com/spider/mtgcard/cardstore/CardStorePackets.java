package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.content.pack.cache.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CardStorePackets {
    private static final int COLLECTOR_NUMBER_MAX_LENGTH = 64;

    // ---------- C2S: Confirm purchase ----------
    public record ConfirmPurchaseC2S(BlockPos pos, int lineCount, List<Line> lines) implements CustomPacketPayload {
        public static final Type<ConfirmPurchaseC2S> ID = new Type<>(ModRegistry.id("cardstore_confirm"));
        public record Line(String setCode, String collectorNumber, int qty) {}

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmPurchaseC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.lines().size());
                            for (Line l : p.lines()) {
                                buf.writeUtf(l.setCode());
                                buf.writeUtf(l.collectorNumber(), COLLECTOR_NUMBER_MAX_LENGTH);
                                buf.writeVarInt(l.qty());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var lines = new ArrayList<Line>(n);
                            for (int i = 0; i < n; i++) {
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(COLLECTOR_NUMBER_MAX_LENGTH);
                                int qty    = buf.readVarInt();
                                lines.add(new Line(set, cn, qty));
                            }
                            return new ConfirmPurchaseC2S(pos, n, lines);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Search (single resolve) ----------
    public record SearchC2S(BlockPos storePos, String query) implements CustomPacketPayload {
        public static final Type<SearchC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchC2S> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SearchC2S::storePos,
                ByteBufCodecs.STRING_UTF8, SearchC2S::query,
                SearchC2S::new
        );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Search result (single) ----------
    public record SearchS2C(
            BlockPos storePos,
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
                            boolean ok = buf.readBoolean();
                            String name = buf.readUtf();
                            String set = buf.readUtf();
                            String cn = buf.readUtf();
                            String msg = buf.readUtf();

                            boolean has = buf.readBoolean();
                            ItemStack st = has ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY;

                            return new SearchS2C(pos, ok, name, set, cn, msg, has, st);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Search prints (paged + streamed) ----------
    public record SearchPrintsC2S(BlockPos storePos, String query, int page, int pageSize, UUID requestId) implements CustomPacketPayload {
        public static final Type<SearchPrintsC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_store_search_prints"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUtf(p.query());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.pageSize());
                            buf.writeUUID(p.requestId());
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String q = buf.readUtf();
                            int page = buf.readVarInt();
                            int pageSize = buf.readVarInt();
                            UUID req = buf.readUUID();
                            return new SearchPrintsC2S(pos, q, page, pageSize, req);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: prints start ----------
    public record SearchPrintsStartS2C(
            BlockPos storePos,
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
                            buf.writeUUID(p.requestId());
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message() == null ? "" : p.message());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());
                        },
                        (buf) -> new SearchPrintsDoneS2C(
                                buf.readBlockPos(),
                                buf.readUUID(),
                                buf.readBoolean(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean()
                        )
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Search prints results (GRID) ----------
    public record SearchPrintsS2C(
            BlockPos storePos,
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

        public record Entry(String setCode, String collectorNumber, ItemStack stack, long priceItems) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC =
                    StreamCodec.of(
                            (buf, e) -> {
                                buf.writeUtf(e.setCode());
                                buf.writeUtf(e.collectorNumber(), COLLECTOR_NUMBER_MAX_LENGTH);
                                ItemStack.STREAM_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            },
                            (buf) -> {
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(COLLECTOR_NUMBER_MAX_LENGTH);
                                ItemStack st = ItemStack.STREAM_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                return new Entry(set, cn, st, price);
                            }
                    );
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, SearchPrintsS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
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

                            return new SearchPrintsS2C(pos, ok, msg, name, page, total, hasMore, priceItemId, priceBasis, list);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: Import Deck ----------
    public record ImportDeckC2S(BlockPos pos, int lineCount, List<Line> lines) implements CustomPacketPayload {
        public static final Type<ImportDeckC2S> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "cardstore_import_deck"));

        public record Line(String set, String cn, int qty) {}

        public static final StreamCodec<RegistryFriendlyByteBuf, ImportDeckC2S> CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ImportDeckC2S::pos,
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

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: Import Deck result ----------
    public record ImportDeckS2C(BlockPos pos, boolean ok, String message, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<ImportDeckS2C> ID = new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "cardstore_import_deck_result"));

        public record Entry(String set, String cn, int qty, ItemStack stack, long priceItems) {}

        public static final StreamCodec<RegistryFriendlyByteBuf, ImportDeckS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeBoolean(p.ok());
                            buf.writeUtf(p.message() == null ? "" : p.message());

                            buf.writeVarInt(p.entries().size());
                            for (Entry e : p.entries()) {
                                buf.writeUtf(e.set());
                                buf.writeUtf(e.cn(), COLLECTOR_NUMBER_MAX_LENGTH);
                                buf.writeVarInt(e.qty());
                                ItemStack.STREAM_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            boolean ok = buf.readBoolean();
                            String msg = buf.readUtf();

                            int n = buf.readVarInt();
                            var entries = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                String set = buf.readUtf();
                                String cn  = buf.readUtf(COLLECTOR_NUMBER_MAX_LENGTH);
                                int qty    = buf.readVarInt();
                                ItemStack st = ItemStack.STREAM_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                entries.add(new Entry(set, cn, qty, st, price));
                            }

                            return new ImportDeckS2C(pos, ok, msg, entries);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- CODEC registration ----------
    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(ConfirmPurchaseC2S.ID, ConfirmPurchaseC2S.CODEC);
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

        var cfg = com.spider.mtgcard.config.MtgcardConfig.get();
        String priceItemId = cfg.Price_Item;
        String priceBasis  = cfg.Price_Basis;

        ServerPlayNetworking.registerGlobalReceiver(ConfirmPurchaseC2S.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!(ctx.player() instanceof ServerPlayer sp)) return;
                    ServerLevel world = (ServerLevel) sp.level();

                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof CardStoreBlockEntity store)) return;

                    CardStorePurchaseService.handleConfirm(ctx.server(), sp, store, payload.lines());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ImportDeckC2S.ID, (payload, ctx) -> {
            var server = ctx.server();
            server.execute(() -> {
                ServerPlayer player = ctx.player();
                ServerLevel world = player.level();

                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof CardStoreBlockEntity store)) {
                    ServerPlayNetworking.send(player,
                            new ImportDeckS2C(payload.pos(), false, "Card Store not found.", List.of()));
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
                                                model.set, model.collectorNumber, qty,
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
                                    new ImportDeckS2C(payload.pos(), ok, msg, out));
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

            String q = payload.query();
            if (q == null || q.trim().isEmpty()) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchS2C(payload.storePos(), false, "", "", "", "Type a card name.", false, ItemStack.EMPTY)));
                return;
            }

            String trimmed = q.trim();
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
                            new SearchS2C(payload.storePos(), true,
                                    finalHit.name, setCode, finalHit.id,
                                    "Custom ✓", true, st)));
                    return;
                }
            } catch (Throwable ignored) {}

            // ---- FALL BACK TO SCRYFALL ----
            ScryfallNamedFetch.fetchNamedFuzzyAsync(trimmed).whenComplete((hit, ex) -> {
                if (ex != null || hit == null) {
                    server.execute(() -> ServerPlayNetworking.send(player,
                            new SearchS2C(payload.storePos(), false, "", "", "", "No match found.", false, ItemStack.EMPTY)));
                    return;
                }

                ServerLevel world = (ServerLevel) player.level();

                ScryfallExactFetch.fetchBySetCollectorAsync(world, hit.set(), hit.collectorNumber())
                        .whenComplete((card, ex2) -> server.execute(() -> {
                            if (ex2 != null || card == null) {
                                ServerPlayNetworking.send(player,
                                        new SearchS2C(payload.storePos(), false, "", "", "",
                                                "Resolved name, but failed exact printing.", false, ItemStack.EMPTY));
                                return;
                            }

                            ItemStack template = CardStackBuilders.buildScryfallStackFromModel(card, false);
                            boolean has = template != null && !template.isEmpty();

                            ServerPlayNetworking.send(player,
                                    new SearchS2C(
                                            payload.storePos(),
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

            String q = payload.query();
            int page = Math.max(1, payload.page());
            int pageSize = Math.max(1, Math.min(payload.pageSize(), 175));
            UUID reqId = payload.requestId();

            if (q == null || q.trim().isEmpty()) {
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsStartS2C(payload.storePos(), reqId, false, "Type a card name.", page, 0, false, priceItemId, priceBasis)));
                server.execute(() -> ServerPlayNetworking.send(player,
                        new SearchPrintsDoneS2C(payload.storePos(), reqId, false, "No query.", page, 0, false)));
                return;
            }

            String qTrim = q.trim();
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
                                new SearchPrintsStartS2C(payload.storePos(), reqId, true, "Searching…", page, total, hasMore, priceItemId, priceBasis)));

                        ArrayList<SearchPrintsS2C.Entry> customEntries = buildCustomEntries(customPage);
                        server.execute(() -> {
                            for (var entry : customEntries) {
                                ServerPlayNetworking.send(player, new SearchPrintsAddS2C(payload.storePos(), reqId, entry));
                            }
                        });

                        int sent = customEntries.size();

                        if (!scryOk || pg.hits().isEmpty()) {
                            String doneMsg = (sent > 0) ? ("Loaded " + sent + " results.") : "No results.";
                            server.execute(() -> ServerPlayNetworking.send(player,
                                    new SearchPrintsDoneS2C(payload.storePos(), reqId, sent > 0, doneMsg, page, total, hasMore)));
                            return;
                        }

                        ArrayList<ScryfallPrintSearchFetch.PrintHit> pageHits = new ArrayList<>(pg.hits());
                        ScryfallExactFetch.fetchCollectionByPrintHitsAsync(pageHits)
                                .exceptionally(err -> List.of())
                                .whenComplete((models, ex3) -> {
                                    ArrayList<SearchPrintsS2C.Entry> entries = buildSearchEntries(pageHits, models);

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
                                                new SearchPrintsDoneS2C(payload.storePos(), reqId, totalSent > 0, doneMsg, page, total, hasMore));
                                    });
                                });
                    });
        });
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

    private static final Pattern CUSTOM_FIELD_QUERY =
            Pattern.compile("\\b([a-zA-Z_]+):(?:\"([^\"]+)\"|(\\S+))");

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

        Matcher matcher = CUSTOM_FIELD_QUERY.matcher(query);
        boolean sawFieldQuery = false;

        while (matcher.find()) {
            sawFieldQuery = true;

            String field = matcher.group(1);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (value == null || value.isBlank()) continue;

            if (!customFieldValue(meta, field).contains(norm(value))) {
                return false;
            }
        }

        if (sawFieldQuery) return true;

        String haystack = String.join(" ",
                norm(meta.name),
                norm(meta.typeLine),
                norm(meta.oracleText),
                norm(meta.set),
                norm(meta.id),
                norm(meta.backName),
                norm(meta.backTypeLine),
                norm(meta.backOracleText)
        ).trim();

        return !haystack.isEmpty() && haystack.contains(norm(query));
    }

    private static String customFieldValue(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta meta, String rawField) {
        String field = rawField == null ? "" : rawField.trim().toLowerCase(Locale.ROOT);
        return switch (field) {
            case "name", "n" -> norm(meta.name) + " " + norm(meta.backName);
            case "type", "type_line", "t" -> norm(meta.typeLine) + " " + norm(meta.backTypeLine);
            case "oracle", "oracle_text", "o" -> norm(meta.oracleText) + " " + norm(meta.backOracleText);
            case "set", "s", "e" -> norm(meta.set);
            case "id", "cn", "collector", "collector_number" -> norm(meta.id);
            default -> "";
        };
    }

    private static ArrayList<SearchPrintsS2C.Entry> buildSearchEntries(
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

            entries.add(new SearchPrintsS2C.Entry(model.set, model.collectorNumber, stack, priceItems));
        }

        return entries;
    }

    private static ArrayList<SearchPrintsS2C.Entry> buildCustomEntries(
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
            entries.add(new SearchPrintsS2C.Entry(setCode, meta.id, st, price));
        }

        return entries;
    }

    private static String printingKey(String set, String collectorNumber) {
        return norm(set) + "\u0000" + norm(collectorNumber);
    }

    private CardStorePackets() {}
}
