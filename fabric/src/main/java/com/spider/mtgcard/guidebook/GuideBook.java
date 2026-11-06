package com.spider.mtgcard.guidebook;

import com.spider.mtgcard.guidebook.chapters.blocks.*;
import com.spider.mtgcard.guidebook.chapters.general.CommandsChapter;
import com.spider.mtgcard.guidebook.chapters.general.ConfigChapter;
import com.spider.mtgcard.guidebook.chapters.items.*;
import com.spider.mtgcard.guidebook.chapters.home.HomeChapter;

public final class GuideBook {

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Home
        GuideChapterRegistry.register(new HomeChapter());

        // Blocks
        GuideChapterRegistry.register(new CardDatabaseChapter());
        GuideChapterRegistry.register(new CardStoreChapter());
        GuideChapterRegistry.register(new DisplayChapter());
        GuideChapterRegistry.register(new LifePointChapter());
        GuideChapterRegistry.register(new GraveyardChapter());
        GuideChapterRegistry.register(new DeckboxChapter());
        GuideChapterRegistry.register(new DeckControlChapter());

        // Items
        GuideChapterRegistry.register(new PackChapter());
        GuideChapterRegistry.register(new CardChapter());
        GuideChapterRegistry.register(new DiceChapter());

        // General
        GuideChapterRegistry.register(new CommandsChapter());
        GuideChapterRegistry.register(new ConfigChapter());

    }

    private GuideBook() {}
}
