package com.spider.mtgcard.content.pack.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class ScryfallHttp {
    private static final Logger LOGGER = LoggerFactory.getLogger("MtgCard/Scryfall");
    private static final long REQUEST_TIMEOUT_SECONDS = 20L;
    private static final long CONNECT_TIMEOUT_SECONDS = 10L;
    private static final long REQUEST_SPACING_MS = 250L;
    private static final long MAX_TRANSIENT_RETRY_BACKOFF_MS = 5_000L;
    private static final long MIN_RATE_LIMIT_BACKOFF_MS = 60_000L;
    private static final long MAX_RATE_LIMIT_BACKOFF_MS = 300_000L;
    private static final int MAX_ATTEMPTS = 2;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // ---- GLOBAL THROTTLE ----
    private static final Object LOCK = new Object();
    private static long nextAllowedAtMs = 0L;

    private static void waitTurn() {
        while (true) {
            long sleep;
            synchronized (LOCK) {
                long now = System.currentTimeMillis();
                sleep = nextAllowedAtMs - now;
                if (sleep <= 0) {
                    nextAllowedAtMs = now + REQUEST_SPACING_MS;
                    return;
                }
            }
            sleep(sleep);
        }
    }

    private static void backoff(long ms) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            nextAllowedAtMs = Math.max(nextAllowedAtMs, now + ms);
        }
    }

    static String get(String url) throws Exception {
        HttpRequest req = base(url)
                .GET()
                .build();
        return sendWithRetry(req, url);
    }

    /** POST JSON (used for /cards/collection batching). */
    static String postJson(String url, String jsonBody) throws Exception {
        if (jsonBody == null) jsonBody = "";
        HttpRequest req = base(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return sendWithRetry(req, url);
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "mtgcard-fabric-mod/1.0")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));
    }

    private static String sendWithRetry(HttpRequest req, String url) throws Exception {
        int attempts = 0;

        while (true) {
            attempts++;

            waitTurn(); // ✅ ensures only paced requests hit the wire

            HttpResponse<String> resp;
            try {
                resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("Scryfall request failed (attempt {}): {} -> {}", attempts, url, e.toString());
                if (attempts >= MAX_ATTEMPTS) throw e;
                long waitMs = 250L * attempts;
                backoff(waitMs);
                sleep(waitMs);
                continue;
            }

            int code = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body();

            if (code == 200) return body;

            // Rate limit: honor Retry-After and push GLOBAL backoff
            if (code == 429) {
                long waitMs = clampRateLimitBackoff(parseRetryAfterMs(resp));
                LOGGER.warn("Scryfall 429 retry in {}ms (attempt {}): {}", waitMs, attempts, url);
                backoff(waitMs);
                if (attempts < MAX_ATTEMPTS) continue;
            }

            // Transient server errors
            if (code >= 500 && code <= 599 && attempts < MAX_ATTEMPTS) {
                long waitMs = clampTransientBackoff(750L * attempts);
                LOGGER.warn("Scryfall {} retry in {}ms (attempt {}): {}", code, waitMs, attempts, url);
                backoff(waitMs);
                continue;
            }

            String snippet = body.length() > 400 ? body.substring(0, 400) + "..." : body;
            throw new RuntimeException("Scryfall " + code + " for " + url + " body=" + snippet);
        }
    }

    /**
     * Retry-After can be:
     * - delta-seconds (e.g. "60")
     * - HTTP-date (e.g. "Wed, 21 Oct 2015 07:28:00 GMT")
     */
    private static long parseRetryAfterMs(HttpResponse<?> resp) {
        try {
            var h = resp.headers().firstValue("Retry-After");
            if (h.isEmpty()) return MIN_RATE_LIMIT_BACKOFF_MS;
            String v = h.get().trim();

            // seconds
            if (v.matches("^\\d+$")) {
                long seconds = Long.parseLong(v);
                return Math.max(MIN_RATE_LIMIT_BACKOFF_MS, seconds * 1000L);
            }

            // HTTP-date
            try {
                ZonedDateTime dt = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME);
                long ms = dt.toInstant().toEpochMilli() - System.currentTimeMillis();
                return Math.max(MIN_RATE_LIMIT_BACKOFF_MS, ms);
            } catch (Throwable ignored) {
                // fall through
            }
        } catch (Throwable ignored) {}
        return MIN_RATE_LIMIT_BACKOFF_MS;
    }

    private static long clampTransientBackoff(long ms) {
        return Math.max(250L, Math.min(MAX_TRANSIENT_RETRY_BACKOFF_MS, ms));
    }

    private static long clampRateLimitBackoff(long ms) {
        return Math.max(MIN_RATE_LIMIT_BACKOFF_MS, Math.min(MAX_RATE_LIMIT_BACKOFF_MS, ms));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private ScryfallHttp() {}
}
