package com.spider.mtgcard.api;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Add-on API for registering cosmetic card sleeves. No-sleeve is intentionally not an ID. */
public final class SleeveRegistry {
    private static final Map<Identifier, CardSleeve> SLEEVES = new LinkedHashMap<>();

    public static synchronized CardSleeve register(CardSleeve sleeve) {
        if (SLEEVES.putIfAbsent(sleeve.id(), sleeve) != null) {
            throw new IllegalArgumentException("Duplicate sleeve id: " + sleeve.id());
        }
        return sleeve;
    }

    public static synchronized Optional<CardSleeve> get(Identifier id) {
        return Optional.ofNullable(SLEEVES.get(id));
    }

    public static synchronized List<CardSleeve> values() {
        List<CardSleeve> result = new ArrayList<>(SLEEVES.values());
        result.sort(Comparator.comparingInt(CardSleeve::sortOrder).thenComparing(s -> s.id().toString()));
        return List.copyOf(result);
    }

    private SleeveRegistry() {}
}
