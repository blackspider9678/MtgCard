package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.content.pack.cache.*;
import com.spider.mtgcard.registry.ModRegistry;
import com.spider.mtgcard.util.CardStackBuilders;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CardStorePackets {

    // ---------- C2S: Confirm purchase ----------
    public record ConfirmPurchaseC2S(BlockPos pos, int lineCount, List<Line> lines) implements CustomPayload {
        public static final Id<ConfirmPurchaseC2S> ID = new Id<>(ModRegistry.id("cardstore_confirm"));
        public record Line(String setCode, String collectorNumber, int qty) {}

        public static final PacketCodec<RegistryByteBuf, ConfirmPurchaseC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeVarInt(p.lines().size());
                            for (Line l : p.lines()) {
                                buf.writeString(l.setCode());
                                buf.writeString(l.collectorNumber(), 32);
                                buf.writeVarInt(l.qty());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            int n = buf.readVarInt();
                            var lines = new ArrayList<Line>(n);
                            for (int i = 0; i < n; i++) {
                                String set = buf.readString();
                                String cn  = buf.readString(32);
                                int qty    = buf.readVarInt();
                                lines.add(new Line(set, cn, qty));
                            }
                            return new ConfirmPurchaseC2S(pos, n, lines);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: Search (single resolve) ----------
    public record SearchC2S(BlockPos storePos, String query) implements CustomPayload {
        public static final Id<SearchC2S> ID = new Id<>(Identifier.of("mtgcard", "card_store_search"));

        public static final PacketCodec<RegistryByteBuf, SearchC2S> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, SearchC2S::storePos,
                PacketCodecs.STRING, SearchC2S::query,
                SearchC2S::new
        );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    ) implements CustomPayload {

        public static final Id<SearchS2C> ID = new Id<>(Identifier.of("mtgcard", "card_store_search_result"));

        public static final PacketCodec<RegistryByteBuf, SearchS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeBoolean(p.ok());
                            buf.writeString(p.name());
                            buf.writeString(p.setCode());
                            buf.writeString(p.collectorNumber());
                            buf.writeString(p.message());

                            buf.writeBoolean(p.hasPreview());
                            if (p.hasPreview()) {
                                ItemStack.PACKET_CODEC.encode(buf, p.preview());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            boolean ok = buf.readBoolean();
                            String name = buf.readString();
                            String set = buf.readString();
                            String cn = buf.readString();
                            String msg = buf.readString();

                            boolean has = buf.readBoolean();
                            ItemStack st = has ? ItemStack.PACKET_CODEC.decode(buf) : ItemStack.EMPTY;

                            return new SearchS2C(pos, ok, name, set, cn, msg, has, st);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: Search prints (paged + streamed) ----------
    public record SearchPrintsC2S(BlockPos storePos, String query, int page, int pageSize, UUID requestId) implements CustomPayload {
        public static final Id<SearchPrintsC2S> ID = new Id<>(Identifier.of("mtgcard", "card_store_search_prints"));

        public static final PacketCodec<RegistryByteBuf, SearchPrintsC2S> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeString(p.query());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.pageSize());
                            buf.writeUuid(p.requestId());
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            String q = buf.readString();
                            int page = buf.readVarInt();
                            int pageSize = buf.readVarInt();
                            UUID req = buf.readUuid();
                            return new SearchPrintsC2S(pos, q, page, pageSize, req);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    ) implements CustomPayload {
        public static final Id<SearchPrintsStartS2C> ID =
                new Id<>(Identifier.of("mtgcard", "card_store_search_prints_start"));

        public static final PacketCodec<RegistryByteBuf, SearchPrintsStartS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUuid(p.requestId());
                            buf.writeBoolean(p.ok());
                            buf.writeString(p.message() == null ? "" : p.message());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());
                            buf.writeString(p.priceItemId() == null ? "" : p.priceItemId());
                            buf.writeString(p.priceBasis() == null ? "" : p.priceBasis());
                        },
                        (buf) -> new SearchPrintsStartS2C(
                                buf.readBlockPos(),
                                buf.readUuid(),
                                buf.readBoolean(),
                                buf.readString(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean(),
                                buf.readString(),
                                buf.readString()
                        )
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- S2C: prints add entry ----------
    public record SearchPrintsAddS2C(BlockPos storePos, UUID requestId, SearchPrintsS2C.Entry entry) implements CustomPayload {
        public static final Id<SearchPrintsAddS2C> ID =
                new Id<>(Identifier.of("mtgcard", "card_store_search_prints_add"));

        public static final PacketCodec<RegistryByteBuf, SearchPrintsAddS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUuid(p.requestId());
                            SearchPrintsS2C.Entry.CODEC.encode(buf, p.entry());
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            UUID req = buf.readUuid();
                            var entry = SearchPrintsS2C.Entry.CODEC.decode(buf);
                            return new SearchPrintsAddS2C(pos, req, entry);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    ) implements CustomPayload {
        public static final Id<SearchPrintsDoneS2C> ID =
                new Id<>(Identifier.of("mtgcard", "card_store_search_prints_done"));

        public static final PacketCodec<RegistryByteBuf, SearchPrintsDoneS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeUuid(p.requestId());
                            buf.writeBoolean(p.ok());
                            buf.writeString(p.message() == null ? "" : p.message());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());
                        },
                        (buf) -> new SearchPrintsDoneS2C(
                                buf.readBlockPos(),
                                buf.readUuid(),
                                buf.readBoolean(),
                                buf.readString(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean()
                        )
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    ) implements CustomPayload {

        public static final Id<SearchPrintsS2C> ID =
                new Id<>(Identifier.of("mtgcard", "card_store_search_prints_result"));

        public record Entry(String setCode, String collectorNumber, ItemStack stack, long priceItems) {
            public static final PacketCodec<RegistryByteBuf, Entry> CODEC =
                    PacketCodec.ofStatic(
                            (buf, e) -> {
                                buf.writeString(e.setCode());
                                buf.writeString(e.collectorNumber(), 32);
                                ItemStack.PACKET_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            },
                            (buf) -> {
                                String set = buf.readString();
                                String cn  = buf.readString(32);
                                ItemStack st = ItemStack.PACKET_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                return new Entry(set, cn, st, price);
                            }
                    );
        }

        public static final PacketCodec<RegistryByteBuf, SearchPrintsS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.storePos());
                            buf.writeBoolean(p.ok());
                            buf.writeString(p.message());
                            buf.writeString(p.canonicalName());
                            buf.writeVarInt(p.page());
                            buf.writeVarInt(p.total());
                            buf.writeBoolean(p.hasMore());

                            buf.writeString(p.priceItemId() == null ? "" : p.priceItemId());
                            buf.writeString(p.priceBasis() == null ? "" : p.priceBasis());

                            buf.writeVarInt(p.entries().size());
                            for (Entry e : p.entries()) Entry.CODEC.encode(buf, e);
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            boolean ok = buf.readBoolean();
                            String msg = buf.readString();
                            String name = buf.readString();
                            int page = buf.readVarInt();
                            int total = buf.readVarInt();
                            boolean hasMore = buf.readBoolean();

                            String priceItemId = buf.readString();
                            String priceBasis  = buf.readString();

                            int n = buf.readVarInt();
                            var list = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) list.add(Entry.CODEC.decode(buf));

                            return new SearchPrintsS2C(pos, ok, msg, name, page, total, hasMore, priceItemId, priceBasis, list);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: Import Deck ----------
    public record ImportDeckC2S(BlockPos pos, int lineCount, List<Line> lines) implements CustomPayload {
        public static final Id<ImportDeckC2S> ID = new Id<>(Identifier.of("mtgcard", "cardstore_import_deck"));

        public record Line(String set, String cn, int qty) {}

        public static final PacketCodec<RegistryByteBuf, ImportDeckC2S> CODEC =
                PacketCodec.tuple(
                        BlockPos.PACKET_CODEC, ImportDeckC2S::pos,
                        PacketCodecs.VAR_INT, ImportDeckC2S::lineCount,
                        PacketCodec.tuple(
                                PacketCodecs.STRING, Line::set,
                                PacketCodecs.STRING, Line::cn,
                                PacketCodecs.VAR_INT, Line::qty,
                                Line::new
                        ).collect(PacketCodecs.toList()),
                        ImportDeckC2S::lines,
                        ImportDeckC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private static CompletableFuture<ScryfallModels.Card> fetchExactWithTokenFallback(ServerWorld world, String set, String cn) {
        String set0 = (set == null) ? "" : set.trim().toLowerCase(Locale.ROOT);
        String cn0  = (cn == null) ? "" : cn.trim();

        CompletableFuture<ScryfallModels.Card> primary =
                ScryfallExactFetch.fetchBySetCollectorAsync(world, set0, cn0)
                        .exceptionally(err -> null);

        if (set0.isBlank()) return primary;

        return primary.thenCompose(card -> {
            if (card != null) return CompletableFuture.completedFuture(card);

            String alt = set0.startsWith("t") ? set0.substring(1) : ("t" + set0);
            if (alt.isBlank() || alt.equals(set0)) return CompletableFuture.completedFuture(null);

            return ScryfallExactFetch.fetchBySetCollectorAsync(world, alt, cn0)
                    .exceptionally(err -> null);
        });
    }

    // ---------- S2C: Import Deck result ----------
    public record ImportDeckS2C(BlockPos pos, boolean ok, String message, List<Entry> entries) implements CustomPayload {
        public static final Id<ImportDeckS2C> ID = new Id<>(Identifier.of("mtgcard", "cardstore_import_deck_result"));

        public record Entry(String set, String cn, int qty, ItemStack stack, long priceItems) {}

        public static final PacketCodec<RegistryByteBuf, ImportDeckS2C> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeBoolean(p.ok());
                            buf.writeString(p.message() == null ? "" : p.message());

                            buf.writeVarInt(p.entries().size());
                            for (Entry e : p.entries()) {
                                buf.writeString(e.set());
                                buf.writeString(e.cn(), 32);
                                buf.writeVarInt(e.qty());
                                ItemStack.PACKET_CODEC.encode(buf, e.stack());
                                buf.writeVarLong(e.priceItems());
                            }
                        },
                        (buf) -> {
                            BlockPos pos = buf.readBlockPos();
                            boolean ok = buf.readBoolean();
                            String msg = buf.readString();

                            int n = buf.readVarInt();
                            var entries = new ArrayList<Entry>(n);
                            for (int i = 0; i < n; i++) {
                                String set = buf.readString();
                                String cn  = buf.readString(32);
                                int qty    = buf.readVarInt();
                                ItemStack st = ItemStack.PACKET_CODEC.decode(buf);
                                long price = buf.readVarLong();
                                entries.add(new Entry(set, cn, qty, st, price));
                            }

                            return new ImportDeckS2C(pos, ok, msg, entries);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
                    if (!(ctx.player() instanceof ServerPlayerEntity sp)) return;
                    ServerWorld world = (ServerWorld) sp.getEntityWorld();

                    var be = world.getBlockEntity(payload.pos());
                    if (!(be instanceof CardStoreBlockEntity store)) return;

                    CardStorePurchaseService.handleConfirm(ctx.server(), sp, store, payload.lines());
                })
        );

        ServerPlayNetworking.registerGlobalReceiver(ImportDeckC2S.ID, (payload, ctx) -> {
            var server = ctx.server();
            server.execute(() -> {
                ServerPlayerEntity player = ctx.player();
                ServerWorld world = player.getEntityWorld();

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
            ServerPlayerEntity player = ctx.player();

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

                ServerWorld world = (ServerWorld) player.getEntityWorld();

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
            ServerPlayerEntity player = ctx.player();

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

            ServerWorld world = (ServerWorld) player.getEntityWorld();
            String qTrim = q.trim();
            String qLower = qTrim.toLowerCase(Locale.ROOT);
            String scryQ = qLower.contains("include:") ? qTrim : (qTrim + " include:extras");

            ScryfallPrintSearchFetch.fetchSearchSliceAsync(scryQ, page, pageSize)
                    .whenComplete((pg, ex2) -> {
                        boolean scryOk = (ex2 == null && pg != null && pg.hits() != null);

                        int total = (scryOk ? pg.totalCards() : 0);
                        boolean hasMore = (scryOk && pg.hasMore());

                        server.execute(() -> ServerPlayNetworking.send(player,
                                new SearchPrintsStartS2C(payload.storePos(), reqId, true, "Searching…", page, total, hasMore, priceItemId, priceBasis)));

                        final int[] sent = {0};
                        sent[0] += streamCustomMatchesCounted(server, player, payload.storePos(), reqId, qTrim, pageSize);

                        if (!scryOk || pg.hits().isEmpty()) {
                            String doneMsg = (sent[0] > 0) ? ("Loaded " + sent[0] + " results.") : "No results.";
                            server.execute(() -> ServerPlayNetworking.send(player,
                                    new SearchPrintsDoneS2C(payload.storePos(), reqId, sent[0] > 0, doneMsg, page, total, false)));
                            return;
                        }

                        var hits = pg.hits();
                        final int remaining = Math.max(0, pageSize - sent[0]);
                        final int limit = Math.min(remaining, hits.size());

                        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

                        for (int i = 0; i < limit; i++) {
                            var h = hits.get(i);

                            chain = chain.thenCompose(ignored ->
                                    fetchExactWithTokenFallback(world, h.set(), h.collectorNumber())
                                            .thenApply(model -> {
                                                if (model == null) return null;

                                                ItemStack stack = CardStackBuilders.buildScryfallStackFromModel(model, false);
                                                if (stack == null || stack.isEmpty()) return null;

                                                long priceItems = CardStorePrice.toCurrencyItemsFromStrings(
                                                        false,
                                                        model.price.usd, model.price.usdFoil, model.price.usdEtched,
                                                        model.price.eur, model.price.eurFoil,
                                                        model.price.tix
                                                );

                                                return new SearchPrintsS2C.Entry(model.set, model.collectorNumber, stack, priceItems);
                                            })
                                            .exceptionally(err -> null)
                                            .thenAccept(entry -> {
                                                if (entry == null) return;
                                                if (sent[0] >= pageSize) return;

                                                sent[0]++;
                                                server.execute(() -> ServerPlayNetworking.send(player,
                                                        new SearchPrintsAddS2C(payload.storePos(), reqId, entry)));
                                            })
                            );
                        }

                        chain.whenComplete((v, ex) -> {
                            String doneMsg = "Loaded " + sent[0] + " results.";
                            server.execute(() -> ServerPlayNetworking.send(player,
                                    new SearchPrintsDoneS2C(payload.storePos(), reqId, sent[0] > 0, doneMsg, page, total, hasMore)));
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
        if (!st.contains(DataComponentTypes.ITEM_NAME)) {
            st.set(DataComponentTypes.ITEM_NAME, Text.literal(name));
        }
        // Keep your CardStackBuilders rarity-colored custom name; only set if absent.
        if (!st.contains(DataComponentTypes.CUSTOM_NAME)) {
            st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
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

    private static int streamCustomMatchesCounted(MinecraftServer server, ServerPlayerEntity player, BlockPos pos, UUID reqId, String queryRaw, int maxToSend) {
        if (maxToSend <= 0) return 0;
        try {
            var store = com.spider.mtgcard.content.pack.custom.CustomCardStores.get(server);
            int sent = 0;

            String q = norm(queryRaw);

            for (var m : store.all()) {
                if (sent >= maxToSend) break;
                if (m == null || m.name == null) continue;
                if (!norm(m.name).contains(q)) continue;

                ItemStack st = CardStackBuilders.buildCustomStackFromMeta(m, false);
                if (st == null || st.isEmpty()) continue;

                applyItemNameIfPresent(st, m.name);

                long price = com.spider.mtgcard.util.CustomCardPricing.priceItemsForRarity(m.rarity);
                String setCode = (m.set == null || m.set.isBlank()) ? "CSTM" : m.set;

                var entry = new SearchPrintsS2C.Entry(setCode, m.id, st, price);
                sent++;

                server.execute(() -> ServerPlayNetworking.send(player, new SearchPrintsAddS2C(pos, reqId, entry)));
            }
            return sent;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void applyDisplayName(ItemStack st, String name) {
        if (st == null || st.isEmpty()) return;
        if (name == null || name.isBlank()) return;

        // Only set the real display name
        st.set(DataComponentTypes.ITEM_NAME, Text.literal(name));
    }

    private CardStorePackets() {}
}
