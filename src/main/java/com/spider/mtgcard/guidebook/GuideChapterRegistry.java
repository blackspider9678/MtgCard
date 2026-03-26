package com.spider.mtgcard.guidebook;

import net.minecraft.resources.Identifier;

import java.util.*;

public final class GuideChapterRegistry {

    private static final Map<Identifier, GuideChapter> CHAPTERS = new LinkedHashMap<>();

    public static void register(GuideChapter chapter) {
        Identifier id = chapter.id();
        if (CHAPTERS.containsKey(id)) {
            throw new IllegalStateException("Duplicate guide chapter id: " + id);
        }
        CHAPTERS.put(id, chapter);
    }

    public static GuideChapter get(Identifier id) {
        return CHAPTERS.get(id);
    }

    public static List<GuideChapter> all() {
        return List.copyOf(CHAPTERS.values());
    }

    public static List<GuideChapter> byCategory(GuideCategory cat) {
        return CHAPTERS.values().stream()
                .filter(c -> c.category() == cat)
                .toList();
    }

    private GuideChapterRegistry() {}
}
