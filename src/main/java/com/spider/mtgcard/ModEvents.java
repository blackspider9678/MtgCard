package com.spider.mtgcard;

import com.spider.mtgcard.content.pack.PackMobDropEvents;

public final class ModEvents {
    private ModEvents() {
    }

    public static void register() {
        PackMobDropEvents.init();
    }
}
