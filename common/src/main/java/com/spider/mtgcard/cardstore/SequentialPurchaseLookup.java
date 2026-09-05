package com.spider.mtgcard.cardstore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Resolves a whole-order quote with at most one outstanding lookup, retaining line order/duplicates. */
final class SequentialPurchaseLookup {
    static <K, V> CompletableFuture<List<V>> resolve(List<K> keys,
            Function<K, CompletableFuture<V>> lookup) {
        Map<K, V> resolved = new HashMap<>();
        List<V> ordered = new ArrayList<>(keys.size());
        CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
        for (K key : keys) {
            tail = tail.thenCompose(ignored -> {
                if (resolved.containsKey(key)) {
                    ordered.add(resolved.get(key));
                    return CompletableFuture.completedFuture(null);
                }
                // The caller supplies its existing failure policy; do not retry duplicates
                // in the same order when that policy returns a failed/null result.
                return lookup.apply(key).thenAccept(value -> {
                    resolved.put(key, value);
                    ordered.add(value);
                });
            });
        }
        return tail.thenApply(ignored -> ordered);
    }

    private SequentialPurchaseLookup() {}
}
