package com.spider.mtgcard.cardstore;

import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.cache.ScryfallExactFetch;
import com.spider.mtgcard.content.pack.cache.ScryfallModels;
import com.spider.mtgcard.util.CardStackBuilders;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CardStorePurchaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger("MtgCard/CardStore");

    private static int priceItemsForCustomRarity(String rarity) {
        if (rarity == null) return 1;
        String r = rarity.trim().toLowerCase(Locale.ROOT);
        return switch (r) {
            case "uncommon" -> 2;
            case "rare" -> 3;
            case "mythic", "mythic rare", "mythic_rare" -> 4;
            default -> 1; // common / unknown
        };
    }

    private static boolean isCustomLine(String setCode, String collector) {
        if (collector != null && collector.startsWith("custom_")) return true;
        return setCode != null && setCode.trim().equalsIgnoreCase("CSTM");
    }

    // ---------------------------------------------------------------------
    // ✅ Non-stacking support:
    // Give each printed card a unique mtg_uid so it can never stack.
    // We do this WITHOUT changing CardStoreBlockEntity by enqueueing qty times.
    // ---------------------------------------------------------------------
    private static void ensureUniqueCardUid(ItemStack st) {
        if (st == null || st.isEmpty()) return;

        CustomData comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();

        // Your schema: root["mtg_meta"] holds everything
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        // Overwrite each time so copies can never share the same uid
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void queueNoStack(CardStoreBlockEntity store, UUID buyer, ItemStack template, int qty) {
        if (store == null || template == null || template.isEmpty()) return;
        int n = Math.max(0, qty);
        if (n <= 0) return;

        // Enqueue n print jobs of count=1, each with a unique uid.
        for (int i = 0; i < n; i++) {
            ItemStack one = template.copyWithCount(1);
            ensureUniqueCardUid(one);
            store.beginPrinting(buyer, one, 1);
        }
    }

    public static void handleConfirm(
            MinecraftServer server,
            ServerPlayer player,
            CardStoreBlockEntity store,
            List<CardStorePackets.ConfirmPurchaseC2S.Line> lines
    ) {
        if (lines == null || lines.isEmpty()) {
            player.sendSystemMessage(Component.literal("Cart is empty."), true);
            return;
        }

        if (store.isDelivering()) {
            player.sendSystemMessage(Component.literal("This store is already printing."), true);
            return;
        }

        boolean creative = player.isCreative();
        ServerLevel world = (ServerLevel) player.level();

        // ---- custom meta lookup (for routing + pricing) ----
        var customStore = com.spider.mtgcard.content.pack.custom.CustomCardStores.get(server);
        java.util.Map<String, com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> customById = new java.util.HashMap<>();
        for (var m : customStore.all()) {
            if (m != null && m.id != null && !m.id.isBlank()) customById.put(m.id, m);
        }

        // Normalize & filter invalid lines, then split into custom vs scryfall
        List<CardStorePackets.ConfirmPurchaseC2S.Line> customLines = new ArrayList<>();
        List<CardStorePackets.ConfirmPurchaseC2S.Line> scryLines   = new ArrayList<>();

        for (var l : lines) {
            if (l == null) continue;

            String set = (l.setCode() == null) ? "" : l.setCode().trim();
            String cn  = (l.collectorNumber() == null) ? "" : l.collectorNumber().trim();
            int qty = Math.max(0, l.qty());

            if (qty <= 0 || set.isBlank() || cn.isBlank()) continue;

            // ✅ NEW: treat as custom if the ID exists in custom store
            boolean isCustom = customById.containsKey(cn) || isCustomLine(set, cn);

            if (isCustom) {
                customLines.add(new CardStorePackets.ConfirmPurchaseC2S.Line(set, cn, qty));
            } else {
                scryLines.add(new CardStorePackets.ConfirmPurchaseC2S.Line(
                        set.toLowerCase(Locale.ROOT),
                        cn.toLowerCase(Locale.ROOT),
                        qty
                ));
            }
        }

        if (customLines.isEmpty() && scryLines.isEmpty()) {
            player.sendSystemMessage(Component.literal("Cart is empty."), true);
            return;
        }

        // If there are NO scryfall lines, we can complete synchronously
        if (scryLines.isEmpty()) {
            server.execute(() -> {
                int totalItems = 0;

                // Price custom lines
                for (var l : customLines) {
                    var meta = customById.get(l.collectorNumber());
                    int unit = priceItemsForCustomRarity(meta == null ? "common" : meta.rarity);
                    long add = (long) unit * (long) l.qty();
                    if (add > Integer.MAX_VALUE) add = Integer.MAX_VALUE;
                    totalItems = (int) Math.min(Integer.MAX_VALUE, (long) totalItems + add);
                }

                MtgcardConfig cfg = MtgcardConfig.get();
                Item currency = resolveCurrencyItem(cfg);
                int costItems = totalItems;

                if (!creative) {
                    int have = countInInv(player, currency);
                    if (have < costItems) {
                        player.sendSystemMessage(Component.literal("Not enough currency. Need " + costItems + " " + currency.getName(new ItemStack(currency)).getString()
                                + " (have " + have + ")."), true);
                        return;
                    }
                    removeFromInv(player, currency, costItems);
                }

                // ✅ Print custom NON-STACKING
                for (var l : customLines) {
                    var meta = customById.get(l.collectorNumber());

                    ItemStack template = (meta != null)
                            ? CardStackBuilders.buildCustomStackFromMeta(meta, false)
                            : CardStackBuilders.buildCustomStackFromId(l.collectorNumber(), false);

                    if (template == null || template.isEmpty()) continue;
                    queueNoStack(store, player.getUUID(), template, l.qty());
                }

                player.sendSystemMessage(Component.literal(creative
                        ? "Printing cards (creative)."
                        : "Purchase confirmed. Printing cards..."), true);
            });
            return;
        }

        // ---- Scryfall async fetch for the scry lines ----
        List<CompletableFuture<ScryfallModels.Card>> futures = new ArrayList<>(scryLines.size());
        for (var l : scryLines) {
            String setCode = l.setCode();
            String collectorNumber = l.collectorNumber();
            futures.add(ScryfallExactFetch.fetchBySetCollectorAsync(world, setCode, collectorNumber)
                    .exceptionally(ex -> {
                        LOGGER.warn("Card store fetch failed for {}/{}", setCode, collectorNumber, ex);
                        return null;
                    }));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((v, ex) -> {
            server.execute(() -> {
                List<ScryfallModels.Card> cards = new ArrayList<>(futures.size());
                for (var f : futures) {
                    cards.add(f.getNow(null));
                }

                int totalItems = 0;
                int resolvedScryLines = 0;

                // Price scryfall lines
                for (int i = 0; i < cards.size(); i++) {
                    var c = cards.get(i);
                    var line = scryLines.get(i);
                    if (c == null || c.id == null || c.id.isBlank()) continue;

                    resolvedScryLines++;
                    boolean preferFoil = false;
                    int unitItems = priceItemsForCard(c, preferFoil);
                    long add = (long) unitItems * (long) line.qty();
                    if (add > Integer.MAX_VALUE) add = Integer.MAX_VALUE;
                    totalItems = (int) Math.min(Integer.MAX_VALUE, (long) totalItems + add);
                }

                // Price custom lines
                for (var l : customLines) {
                    var meta = customById.get(l.collectorNumber());
                    int unit = priceItemsForCustomRarity(meta == null ? "common" : meta.rarity);
                    long add = (long) unit * (long) l.qty();
                    if (add > Integer.MAX_VALUE) add = Integer.MAX_VALUE;
                    totalItems = (int) Math.min(Integer.MAX_VALUE, (long) totalItems + add);
                }

                MtgcardConfig cfg = MtgcardConfig.get();
                Item currency = resolveCurrencyItem(cfg);
                int costItems = totalItems;

                if (resolvedScryLines == 0 && customLines.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Failed fetching cards from Scryfall."), true);
                    return;
                }

                if (!creative) {
                    int have = countInInv(player, currency);
                    if (have < costItems) {
                        player.sendSystemMessage(Component.literal("Not enough currency. Need " + costItems + " " + currency.getName(new ItemStack(currency)).getString()
                                + " (have " + have + ")."), true);
                        return;
                    }
                    removeFromInv(player, currency, costItems);
                }

                // ✅ Print scryfall NON-STACKING
                for (int i = 0; i < cards.size(); i++) {
                    ScryfallModels.Card c = cards.get(i);
                    var line = scryLines.get(i);
                    if (c == null || c.id == null || c.id.isBlank()) continue;

                    ItemStack template = CardStackBuilders.buildScryfallStackFromModel(c, false);
                    if (template == null || template.isEmpty()) continue;

                    queueNoStack(store, player.getUUID(), template, line.qty());
                }

                // ✅ Print custom NON-STACKING
                for (var l : customLines) {
                    var meta = customById.get(l.collectorNumber());
                    ItemStack template = (meta != null)
                            ? CardStackBuilders.buildCustomStackFromMeta(meta, false)
                            : CardStackBuilders.buildCustomStackFromId(l.collectorNumber(), false);
                    if (template == null || template.isEmpty()) continue;
                    queueNoStack(store, player.getUUID(), template, l.qty());
                }

                if (!store.isDelivering()) {
                    player.sendSystemMessage(Component.literal("No cards could be fetched from Scryfall."), true);
                    return;
                }

                player.sendSystemMessage(Component.literal(creative
                        ? "Printing cards (creative)."
                        : "Purchase confirmed. Printing cards..."), true);
            });
        });
    }

    // -------- Pricing helpers --------

    private static double chooseUnitPrice(ScryfallModels.Card c) {
        if (c == null || c.price == null) return 0.0;
        return Math.max(0.0, parsePriceOrZero(c.price.usd));
    }

    private static double parsePriceOrZero(String s) {
        if (s == null) return 0.0;
        String t = s.trim();
        if (t.isEmpty() || t.equals("-") || t.equals("—")) return 0.0;
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
            return v;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static Item resolveCurrencyItem(MtgcardConfig cfg) {
        String id = (cfg == null || cfg.Price_Item == null || cfg.Price_Item.isBlank())
                ? "minecraft:diamond"
                : cfg.Price_Item.trim().toLowerCase(Locale.ROOT);

        Identifier ident;
        try {
            ident = Identifier.parse(id);
        } catch (Throwable t) {
            ident = Identifier.fromNamespaceAndPath("minecraft", "diamond");
        }

        Item item = BuiltInRegistries.ITEM.getValue(ident);
        if (item == null) item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "diamond"));
        return item;
    }

    private enum PriceTier { NORMAL, FOIL, ETCHED }
    private record PricePick(String value, PriceTier tier) {}

    private static int priceItemsForCard(ScryfallModels.Card c, boolean preferFoil) {
        if (c == null || c.price == null) return 0;

        String basis = MtgcardConfig.get().Price_Basis;
        String b = (basis == null ? "USD" : basis.trim().toUpperCase(Locale.ROOT));

        String normal;
        String foil;
        String etched;

        switch (b) {
            case "EUR" -> {
                normal = c.price.eur;
                foil   = c.price.eurFoil;
                etched = null;
            }
            case "TIX" -> {
                normal = c.price.tix;
                foil   = null;
                etched = null;
            }
            case "USD" -> {
                normal = c.price.usd;
                foil   = c.price.usdFoil;
                etched = c.price.usdEtched;
            }
            default -> {
                normal = c.price.usd;
                foil   = c.price.usdFoil;
                etched = c.price.usdEtched;
            }
        }

        PricePick pick = pickBestPrice(preferFoil, normal, foil, etched);
        return roundPriceToWhole(pick.value());
    }

    private static boolean isValidPrice(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !(t.isEmpty() || t.equals("-") || t.equals("—"));
    }

    private static PricePick pickBestPrice(boolean preferFoil, String normal, String foil, String etched) {
        if (preferFoil) {
            if (isValidPrice(foil))   return new PricePick(foil, PriceTier.FOIL);
            if (isValidPrice(etched)) return new PricePick(etched, PriceTier.ETCHED);
            if (isValidPrice(normal)) return new PricePick(normal, PriceTier.NORMAL);
        }
        if (isValidPrice(normal)) return new PricePick(normal, PriceTier.NORMAL);
        if (isValidPrice(foil))   return new PricePick(foil, PriceTier.FOIL);
        if (isValidPrice(etched)) return new PricePick(etched, PriceTier.ETCHED);
        return new PricePick("0", PriceTier.NORMAL);
    }

    private static int roundPriceToWhole(String price) {
        if (price == null) return 0;
        String s = price.trim();
        if (s.isEmpty() || s.equals("-") || s.equals("—")) return 0;

        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) return 0;

            long r = Math.round(v);
            if (r < 0) r = 0;
            if (r > Integer.MAX_VALUE) r = Integer.MAX_VALUE;
            return (int) r;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // -------- Inventory helpers --------

    private static int countInInv(ServerPlayer player, Item item) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(item)) total += st.getCount();
        }
        return total;
    }

    private static void removeFromInv(ServerPlayer player, Item item, int count) {
        int remaining = count;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (remaining <= 0) break;
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !st.is(item)) continue;

            int take = Math.min(remaining, st.getCount());
            st.shrink(take);
            remaining -= take;
        }
    }

    private CardStorePurchaseService() {}
}
