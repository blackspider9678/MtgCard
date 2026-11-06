package com.spider.mtgcard.content.pack.cache;

import com.spider.mtgcard.cardstore.DeckImportQuery;
import net.minecraft.server.level.ServerLevel;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Resolves incomplete deck entries to the latest released printing matching their supplied fields. */
public final class ScryfallDeckImportFetch {
    private static final RecentPrintingCache<String, ScryfallModels.Card> RECENT =
            new RecentPrintingCache<>(4096, TimeUnit.MINUTES.toNanos(10), System::nanoTime,
                    card -> card != null && card.id != null && !card.id.isBlank());

    private ScryfallDeckImportFetch() {}

    public static CompletableFuture<ScryfallModels.Card> fetchAsync(
            ServerLevel world, String name, String set, String collector) {
        if (!set.isBlank() && !collector.isBlank() && set.matches("[A-Za-z0-9]{2,12}")) {
            return ScryfallExactFetch.fetchBySetCollectorAsync(world, set, collector)
                    .exceptionally(error -> null)
                    .thenCompose(card -> card != null ? CompletableFuture.completedFuture(card)
                            : fetchNewestAsync(world, name, set, collector));
        }
        // CSV exports can supply a full edition name instead of a set code.
        return fetchNewestAsync(world, name, set, collector);
    }

    public static CompletableFuture<ScryfallModels.Card> fetchNewestAsync(
            ServerLevel world, String name, String set, String collector) {
        if ((name == null || name.isBlank()) && (set.isBlank() || collector.isBlank())) {
            return CompletableFuture.completedFuture(null);
        }
        String query = DeckImportQuery.newest(name, set, collector, LocalDate.now(ZoneOffset.UTC));
        return RECENT.getOrFetch(query, () -> ScryfallService.supplyAsync("deck name lookup " + name, () -> {
            var card = newestMatchingCard(query, name);
            if (card != null) ScryfallExactFetch.rememberCollection(List.of(card));
            return card;
        })).thenCompose(card -> card == null ? CompletableFuture.completedFuture(null)
                : ScryfallExactFetch.fetchBySetCollectorAsync(world, card.set, card.collectorNumber));
    }

    private static ScryfallModels.Card newestMatchingCard(String query, String name) throws Exception {
        String url = "https://api.scryfall.com/cards/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&unique=prints&order=released&dir=desc&include_extras=true";
        ScryfallModels.Card frontFaceMatch = null;
        do {
            String body = ScryfallHttp.get(url);
            // Scryfall's exact-name search also matches individual faces of other cards.
            // Check the returned whole name before choosing a printing by release date.
            for (var card : ScryfallJson.parseCollection(body)) {
                String front = card.faces == null || card.faces.isEmpty() ? "" : card.faces.getFirst().name;
                int rank = DeckImportQuery.nameMatchRank(name, card.name, front);
                if (rank == 0) return card;
                if (rank == 1 && frontFaceMatch == null) frontFaceMatch = card;
            }
            var page = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            url = page.has("has_more") && page.get("has_more").getAsBoolean() && page.has("next_page")
                    ? page.get("next_page").getAsString() : null;
        } while (url != null);
        return frontFaceMatch;
    }
}
