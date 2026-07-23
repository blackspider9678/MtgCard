package com.spider.mtgcard.displayblock;

public final class DisplayScreenClientHooks {
    /** Set by client init. No-op on dedicated server. */
    public static Runnable INVALIDATE_BOUNDS_CACHE = () -> {};

    public static void invalidateBoundsCacheClient() {
        INVALIDATE_BOUNDS_CACHE.run();
    }

    private DisplayScreenClientHooks() {}
}
