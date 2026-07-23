package com.spider.mtgcard.content.pack.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public final class ScryfallService {
    private static final Logger LOGGER = LoggerFactory.getLogger("MtgCard/Scryfall");

    // Parallel HTTP: make packs feel instant.
    // 6 is a good baseline; scales with CPU for higher-end boxes.
    private static final int THREADS = Math.max(6, Runtime.getRuntime().availableProcessors() / 2);
    private static final long SLOW_TASK_WARNING_MILLIS = 20_000L;

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "mtg-scryfall-fetch");
        t.setDaemon(true);
        return t;
    });

    public static <T> CompletableFuture<T> supplyAsync(Callable<T> work) {
        return supplyAsync("scryfall task", work);
    }

    public static <T> CompletableFuture<T> supplyAsync(String label, Callable<T> work) {
        long startedNs = System.nanoTime();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXEC);

        future.whenComplete((result, ex) -> {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
            if (elapsedMs >= SLOW_TASK_WARNING_MILLIS) {
                LOGGER.warn("Slow Scryfall task ({} ms): {}", elapsedMs, label);
            }
        });
        return future;
    }

    public static void shutdown() {
        EXEC.shutdownNow();
    }
    private ScryfallService() {}
}
