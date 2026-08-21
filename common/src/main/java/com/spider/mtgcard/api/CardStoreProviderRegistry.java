package com.spider.mtgcard.api;

import com.spider.mtgcard.cardstore.CardStoreBlockEntity;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CardStoreProviderRegistry {
    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();

    static {
        if (MtgcardConfig.mtgGameEnabled()) register(new Provider() {
            @Override
            public String game() {
                return TcgGameRegistry.MTG;
            }

            @Override
            public Component label() {
                return TcgGameRegistry.labelForGame(TcgGameRegistry.MTG);
            }
        });
    }

    public static synchronized Provider register(Provider provider) {
        Provider safe = Objects.requireNonNull(provider, "provider");
        String game = TcgGameRegistry.normalizeGameId(safe.game());
        if (game.isBlank()) {
            throw new IllegalArgumentException("Card Store provider game must not be blank");
        }
        if (!TcgGameRegistry.containsGame(game)) {
            TcgGameRegistry.register(game, safe.label());
        }
        PROVIDERS.put(game, safe);
        return safe;
    }

    public static synchronized Optional<Provider> get(String game) {
        return Optional.ofNullable(PROVIDERS.get(sanitizeGameId(game)));
    }

    public static synchronized boolean containsProvider(String game) {
        return PROVIDERS.containsKey(sanitizeGameId(game));
    }

    public static synchronized List<TcgGameRegistry.Entry> gameEntriesWithProviders() {
        ArrayList<TcgGameRegistry.Entry> entries = new ArrayList<>();
        for (TcgGameRegistry.Entry game : TcgGameRegistry.gameEntries()) {
            if (PROVIDERS.containsKey(game.id())) entries.add(game);
        }
        entries.sort(Comparator.comparing(TcgGameRegistry.Entry::id));
        return List.copyOf(entries);
    }

    public static synchronized List<TcgGameRegistry.Entry> filterOptionsWithProviders() {
        ArrayList<TcgGameRegistry.Entry> entries = new ArrayList<>();
        entries.add(TcgGameRegistry.allEntry());
        entries.addAll(gameEntriesWithProviders());
        return List.copyOf(entries);
    }

    public static boolean hasMultipleProviders() {
        return gameEntriesWithProviders().size() > 1;
    }

    public static String sanitizeGameId(String game) {
        try {
            String normalized = TcgGameRegistry.normalizeGameId(game);
            return normalized.isBlank() ? TcgGameRegistry.MTG : normalized;
        } catch (IllegalArgumentException ignored) {
            return TcgGameRegistry.MTG;
        }
    }

    public static String sanitizeFilterId(String game) {
        try {
            String normalized = TcgGameRegistry.normalizeGameId(game);
            return normalized.isBlank() ? TcgGameRegistry.ALL_GAMES : normalized;
        } catch (IllegalArgumentException ignored) {
            return TcgGameRegistry.ALL_GAMES;
        }
    }

    public interface Provider {
        String game();

        default Component label() {
            return TcgGameRegistry.labelForGame(game());
        }

        default CompletableFuture<SearchResult> search(SearchContext ctx, String query) {
            return CompletableFuture.completedFuture(SearchResult.noMatch("No Card Store search provider for this game."));
        }

        default CompletableFuture<SearchPrintsResult> searchPrints(SearchContext ctx, String query, int page, int pageSize) {
            return CompletableFuture.completedFuture(SearchPrintsResult.empty("No Card Store search provider for this game.", page));
        }

        default CompletableFuture<ImportDeckResult> importDeck(SearchContext ctx, List<ImportLine> lines) {
            return CompletableFuture.completedFuture(ImportDeckResult.empty("No Card Store deck import provider for this game."));
        }

        default CompletableFuture<PurchaseResult> preparePurchase(SearchContext ctx, List<PurchaseLine> lines) {
            return CompletableFuture.completedFuture(PurchaseResult.empty("No Card Store purchase provider for this game."));
        }
    }

    public record SearchContext(
            MinecraftServer server,
            ServerLevel level,
            ServerPlayer player,
            CardStoreBlockEntity store,
            BlockPos storePos
    ) {
    }

    public record SearchResult(
            boolean ok,
            String name,
            String setCode,
            String collectorNumber,
            String message,
            ItemStack preview
    ) {
        public SearchResult {
            name = name == null ? "" : name;
            setCode = setCode == null ? "" : setCode.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            message = message == null ? "" : message;
            preview = sanitizeStack(preview);
            ok = ok && !setCode.isBlank() && !collectorNumber.isBlank();
        }

        public static SearchResult noMatch(String message) {
            return new SearchResult(false, "", "", "", message, ItemStack.EMPTY);
        }
    }

    public record PrintEntry(String setCode, String collectorNumber, ItemStack stack, long priceItems) {
        public PrintEntry {
            setCode = setCode == null ? "" : setCode.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            stack = sanitizeStack(stack);
            priceItems = Math.max(0L, priceItems);
        }

        public boolean isValid() {
            return !setCode.isBlank() && !collectorNumber.isBlank() && !stack.isEmpty();
        }
    }

    public record SearchPrintsResult(
            boolean ok,
            String message,
            String canonicalName,
            int page,
            int total,
            boolean hasMore,
            List<PrintEntry> entries
    ) {
        public SearchPrintsResult {
            message = message == null ? "" : message;
            canonicalName = canonicalName == null ? "" : canonicalName;
            page = Math.max(1, page);
            total = Math.max(0, total);
            entries = sanitizePrintEntries(entries);
            ok = ok && !entries.isEmpty();
        }

        public static SearchPrintsResult empty(String message, int page) {
            return new SearchPrintsResult(false, message, "", page, 0, false, List.of());
        }
    }

    public record ImportLine(String set, String collectorNumber, int qty) {
        public ImportLine {
            set = set == null ? "" : set.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            qty = Math.max(1, qty);
        }
    }

    public record ImportEntry(String set, String collectorNumber, int qty, ItemStack stack, long priceItems) {
        public ImportEntry {
            set = set == null ? "" : set.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            qty = Math.max(1, qty);
            stack = sanitizeStack(stack);
            priceItems = Math.max(0L, priceItems);
        }

        public boolean isValid() {
            return !set.isBlank() && !collectorNumber.isBlank() && !stack.isEmpty();
        }
    }

    public record ImportDeckResult(boolean ok, String message, List<ImportEntry> entries) {
        public ImportDeckResult {
            message = message == null ? "" : message;
            entries = sanitizeImportEntries(entries);
            ok = ok && !entries.isEmpty();
        }

        public static ImportDeckResult empty(String message) {
            return new ImportDeckResult(false, message, List.of());
        }
    }

    public record PurchaseLine(String set, String collectorNumber, int qty) {
        public PurchaseLine {
            set = set == null ? "" : set.trim();
            collectorNumber = collectorNumber == null ? "" : collectorNumber.trim();
            qty = Math.max(1, qty);
        }
    }

    public record PurchaseEntry(ItemStack stack, int qty, long priceItems) {
        public PurchaseEntry {
            stack = sanitizeStack(stack);
            qty = Math.max(1, qty);
            priceItems = Math.max(0L, priceItems);
        }

        public boolean isValid() {
            return !stack.isEmpty();
        }
    }

    public record PurchaseResult(boolean ok, String message, List<PurchaseEntry> entries) {
        public PurchaseResult {
            message = message == null ? "" : message;
            entries = sanitizePurchaseEntries(entries);
            ok = ok && !entries.isEmpty();
        }

        public static PurchaseResult empty(String message) {
            return new PurchaseResult(false, message, List.of());
        }
    }

    private static List<PrintEntry> sanitizePrintEntries(List<PrintEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        ArrayList<PrintEntry> out = new ArrayList<>();
        for (PrintEntry entry : entries) {
            if (entry == null || !entry.isValid()) continue;
            out.add(entry);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static List<ImportEntry> sanitizeImportEntries(List<ImportEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        ArrayList<ImportEntry> out = new ArrayList<>();
        for (ImportEntry entry : entries) {
            if (entry == null || !entry.isValid()) continue;
            out.add(entry);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static List<PurchaseEntry> sanitizePurchaseEntries(List<PurchaseEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        ArrayList<PurchaseEntry> out = new ArrayList<>();
        for (PurchaseEntry entry : entries) {
            if (entry == null || !entry.isValid()) continue;
            out.add(entry);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static ItemStack sanitizeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.copyWithCount(1);
    }

    private CardStoreProviderRegistry() {
    }
}
