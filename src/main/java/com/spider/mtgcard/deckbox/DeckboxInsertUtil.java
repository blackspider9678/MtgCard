package com.spider.mtgcard.deckbox;

import net.minecraft.item.ItemStack;

/**
 * Helper for inserting store-printed cards into a Deckbox.
 * Card Store is ONLY allowed to use main grid slots 0..98.
 * Side slots 99..101 are reserved.
 */
public final class DeckboxInsertUtil {

    private DeckboxInsertUtil() {}

    /**
     * Try to insert exactly ONE item (count=1) into the deckbox main grid (0..98).
     * Returns true if fully inserted.
     */
    public static boolean tryInsertOneIntoMainGrid(DeckboxBlockEntity deck, ItemStack one) {
        if (one.isEmpty()) return false;

        // Always treat as a single-card insert
        if (one.getCount() != 1) one = one.copyWithCount(1);

        final int firstSide = DeckboxBlockEntity.FIRST_SIDE_SLOT; // 99
        // 1) Merge into existing stacks first
        for (int i = 0; i < firstSide; i++) {
            ItemStack cur = deck.getStack(i);
            if (cur.isEmpty()) continue;

            if (ItemStack.areItemsAndComponentsEqual(cur, one) && cur.getCount() < cur.getMaxCount()) {
                cur.increment(1);
                deck.sync();
                return true;
            }
        }

        // 2) Place into an empty slot
        for (int i = 0; i < firstSide; i++) {
            ItemStack cur = deck.getStack(i);
            if (cur.isEmpty()) {
                deck.setStack(i, one.copyWithCount(1));
                deck.sync();
                return true;
            }
        }

        // No room in 0..98
        return false;
    }
}
