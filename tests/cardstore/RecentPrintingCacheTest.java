package com.spider.mtgcard.content.pack.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone checks; no live API requests, game client, or timing sleeps. */
public final class RecentPrintingCacheTest {
    public static void main(String[] args) {
        var now = new AtomicLong();
        var cache = new RecentPrintingCache<String, String>(100, 1000, now::get, v -> v != null);
        var calls = new AtomicInteger();
        for (int i = 0; i < 100; i++) cache.put("card" + i, "data" + i);
        for (int i = 0; i < 100; i++) {
            String value = cache.getOrFetch("card" + i, () -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture("unexpected");
            }).join();
            check(value.equals("data" + i), "checkout reuses server search/import data");
        }
        check(calls.get() == 0, "100 cached cards require zero remote lookups");
        now.set(1000);
        check(cache.getOrFetch("card0", () -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture("fresh");
        }).join().equals("fresh"), "expired prices refresh");
        check(calls.get() == 1, "one refresh");

        var remote = new CompletableFuture<String>();
        var first = cache.getOrFetch("shared", () -> { calls.incrementAndGet(); return remote; });
        var second = cache.getOrFetch("shared", () -> { throw new AssertionError("duplicate HTTP request"); });
        first.cancel(false);
        remote.complete("shared data");
        check(second.join().equals("shared data"), "cancelling one buyer cannot cancel another buyer");

        cache.getOrFetch("failed", () -> CompletableFuture.<String>failedFuture(new IllegalStateException()))
                .handle((v, ex) -> null).join();
        check(cache.getOrFetch("failed", () -> CompletableFuture.completedFuture("retry")).join().equals("retry"),
                "failed requests must not poison cache");
        cache.getOrFetch("null", () -> CompletableFuture.completedFuture(null)).join();
        check(cache.getOrFetch("null", () -> CompletableFuture.completedFuture("retry")).join().equals("retry"),
                "missing cards must not poison cache");
        cache.getOrFetch("throw", () -> { throw new IllegalStateException(); }).handle((v, ex) -> null).join();
        check(cache.getOrFetch("throw", () -> CompletableFuture.completedFuture("retry")).join().equals("retry"),
                "synchronous lookup failure releases in-flight entry");

        var small = new RecentPrintingCache<String, String>(1, 1000, now::get, v -> v != null);
        small.put("old", "old");
        small.put("new", "new");
        check(small.getOrFetch("old", () -> CompletableFuture.completedFuture("refetched")).join().equals("refetched"),
                "cache capacity bounded");
        System.out.println("Recent printing cache checks passed (100-card warm checkout: 0 remote lookups).");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
