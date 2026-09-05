package com.spider.mtgcard.cardstore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class SequentialPurchaseLookupTest {
    public static void main(String[] args) {
        var pending = new ArrayList<CompletableFuture<Integer>>();
        var keys = IntStream.range(0, 100).boxed().toList();
        var order = SequentialPurchaseLookup.resolve(keys, key -> {
            var result = new CompletableFuture<Integer>();
            pending.add(result);
            return result;
        });
        check(pending.size() == 1, "only first lookup starts");
        for (int i = 0; i < 100; i++) {
            check(pending.size() == i + 1, "no concurrent card lookups");
            check(!order.isDone(), "whole quote waits for all lines");
            pending.get(i).complete(i);
        }
        check(order.join().equals(keys), "selection/order preserved");
        check(order.join().stream().mapToInt(Integer::intValue).sum() == 4950, "full total available before payment");

        var calls = new AtomicInteger();
        var duplicates = SequentialPurchaseLookup.resolve(List.of("a", "a", "missing", "b", "missing"), key -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(key.equals("missing") ? null : key);
        }).join();
        check(calls.get() == 3, "one lookup per distinct key including missing cards");
        check(duplicates.size() == 5 && "a".equals(duplicates.get(1)) && duplicates.get(4) == null,
                "duplicates and failure positions preserved");

        var failure = SequentialPurchaseLookup.resolve(List.of(1, 2), key -> {
            throw new IllegalStateException("resolver failure");
        });
        check(failure.isCompletedExceptionally(), "synchronous errors finish exceptionally instead of hanging");
        check(SequentialPurchaseLookup.resolve(List.of(), key -> CompletableFuture.completedFuture(key)).join().isEmpty(),
                "empty order completes");

        var slow = new CompletableFuture<String>();
        var buyer1 = SequentialPurchaseLookup.resolve(List.of("a"), key -> slow);
        var buyer2 = SequentialPurchaseLookup.resolve(List.of("b"), CompletableFuture::completedFuture);
        check(buyer2.join().equals(List.of("b")) && !buyer1.isDone(), "buyers have independent quote state");
        slow.complete("a");

        var reservation = new CompletableFuture<Void>();
        var active = new CompletableFuture<Integer>();
        var starts = new AtomicInteger();
        var cancelled = SequentialPurchaseLookup.resolve(List.of(1, 2, 3), key -> {
            if (reservation.isCancelled()) throw new java.util.concurrent.CancellationException();
            starts.incrementAndGet();
            return active;
        });
        reservation.cancel(false);
        active.complete(1);
        check(cancelled.isCompletedExceptionally() && starts.get() == 1,
                "removing store prevents all subsequent remote lookups");
        System.out.println("Sequential whole-order lookup checks passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
