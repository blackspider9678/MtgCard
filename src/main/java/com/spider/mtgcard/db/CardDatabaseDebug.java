package com.spider.mtgcard.db;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.MtgcardConfig;

public final class CardDatabaseDebug {
    private CardDatabaseDebug() {
    }

    public static boolean enabled() {
        return MtgcardConfig.cardDatabaseDebugEnabled();
    }

    public static void log(String message, Object... args) {
        if (enabled()) {
            Mtgcard.LOGGER.info(message, args);
        }
    }
}
