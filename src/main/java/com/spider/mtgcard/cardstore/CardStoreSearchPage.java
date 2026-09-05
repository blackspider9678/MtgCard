package com.spider.mtgcard.cardstore;

import java.util.ArrayList;
import java.util.List;

/** Pages a stable interleaving of provider results without discarding any game's matches. */
final class CardStoreSearchPage {
    static <T> List<T> slice(List<? extends List<T>> providers, int page, int pageSize) {
        long offset = (long) (page - 1) * pageSize;
        long seen = 0;
        ArrayList<T> result = new ArrayList<>();
        for (int index = 0; ; index++) {
            boolean found = false;
            for (List<T> entries : providers) {
                if (index >= entries.size()) continue;
                found = true;
                if (seen++ >= offset) result.add(entries.get(index));
                if (result.size() == pageSize) return result;
            }
            if (!found) return result;
        }
    }

    private CardStoreSearchPage() {}
}
