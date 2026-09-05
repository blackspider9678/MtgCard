package com.spider.mtgcard.content.pack.cache;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded, short-lived remote-data cache; never contains player inventory or cart data. */
final class RecentPrintingCache<K, V> {
    private record Entry<V>(V value, long created) {}
    private final Map<K, Entry<V>> cached = new LinkedHashMap<>();
    private final Map<K, CompletableFuture<V>> inFlight = new HashMap<>();
    private final int capacity;
    private final long lifetime;
    private final LongSupplier clock;
    private final Predicate<V> valid;

    RecentPrintingCache(int capacity, long lifetime, LongSupplier clock, Predicate<V> valid) {
        this.capacity = capacity;
        this.lifetime = lifetime;
        this.clock = clock;
        this.valid = valid;
    }

    synchronized void put(K key, V value) {
        if (!valid.test(value)) return;
        cached.remove(key);
        cached.put(key, new Entry<>(value, clock.getAsLong()));
        while (cached.size() > capacity) cached.remove(cached.keySet().iterator().next());
    }

    synchronized CompletableFuture<V> getOrFetch(K key, Supplier<CompletableFuture<V>> fetch) {
        Entry<V> hit = cached.get(key);
        if (hit != null) {
            if (clock.getAsLong() - hit.created() < lifetime) {
                return CompletableFuture.completedFuture(hit.value());
            }
            cached.remove(key);
        }
        CompletableFuture<V> pending = inFlight.get(key);
        if (pending != null) return pending.copy();

        CompletableFuture<V> shared = new CompletableFuture<>();
        inFlight.put(key, shared);
        try {
            fetch.get().whenComplete((value, error) -> {
                synchronized (this) {
                    inFlight.remove(key);
                    if (error == null) put(key, value);
                }
                if (error == null) shared.complete(value);
                else shared.completeExceptionally(error);
            });
        } catch (Exception error) {
            inFlight.remove(key);
            shared.completeExceptionally(error);
        }
        // A caller cancelling its own checkout must not cancel another player's lookup.
        return shared.copy();
    }
}
