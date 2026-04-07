package com.spider.mtgcard.content.pack.cache;

import java.util.concurrent.*;

public final class ScryfallService {

    // Parallel HTTP: make packs feel instant.
    // 6 is a good baseline; scales with CPU for higher-end boxes.
    private static final int THREADS = Math.max(6, Runtime.getRuntime().availableProcessors() / 2);

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "mtg-scryfall-fetch");
        t.setDaemon(true);
        return t;
    });

    public static <T> CompletableFuture<T> supplyAsync(Callable<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, EXEC);
    }

    public static void shutdown() {
        EXEC.shutdownNow();
    }

    private ScryfallService() {}
}
