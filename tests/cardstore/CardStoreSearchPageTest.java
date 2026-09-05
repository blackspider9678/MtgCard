package com.spider.mtgcard.cardstore;

import java.util.ArrayList;
import java.util.List;

/** Standalone regression checks; run with java after compiling with CardStoreSearchPage. */
public final class CardStoreSearchPageTest {
    public static void main(String[] args) {
        var games = List.of(List.of("m1", "m2", "m3", "m4"),
                List.of("p1"), List.<String>of(), List.of("r1", "r2"));
        expect(List.of("m1", "p1", "r1"), CardStoreSearchPage.slice(games, 1, 3));
        expect(List.of("m2", "r2", "m3"), CardStoreSearchPage.slice(games, 2, 3));
        expect(List.of("m4"), CardStoreSearchPage.slice(games, 3, 3));
        expect(List.of(), CardStoreSearchPage.slice(games, 4, 3));
        expect(List.of("p1"), CardStoreSearchPage.slice(List.of(List.of(), List.of("p1")), 1, 3));
        expect(List.of(), CardStoreSearchPage.slice(List.of(), 1, 3));
        for (int size = 1; size <= 8; size++) {
            var all = new ArrayList<String>();
            for (int page = 1; page <= 8; page++) all.addAll(CardStoreSearchPage.slice(games, page, size));
            expect(List.of("m1", "p1", "r1", "m2", "r2", "m3", "m4"), all);
        }
        System.out.println("Card Store mixed-game pagination checks passed.");
    }

    private static void expect(List<?> expected, List<?> actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
