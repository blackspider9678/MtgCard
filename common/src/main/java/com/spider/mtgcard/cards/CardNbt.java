package com.spider.mtgcard.cards;

import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class CardNbt {
    private CardNbt() {}

    /** "{W}{U}{2}" etc. Fallbacks to first face when needed. */
    public static String getManaCost(ItemStack stack) {
        return TcgCardMeta.read(stack).manaCost();
    }

    /** Simple legality: legendary creature OR explicit commander legality not "illegal". */
    public static boolean isCommanderLegal(ItemStack stack) {
        TcgCardMeta.Info meta = TcgCardMeta.read(stack);

        boolean legendary = meta.legendary();
        String typeLine = meta.typeLine().toLowerCase(Locale.ROOT);
        boolean creature = typeLine.contains("creature");
        boolean result = legendary && creature;

        // If explicit legality exists, prefer it
        String cmd = meta.commanderLegality();
        if (!cmd.isEmpty()) {
            result = !"illegal".equalsIgnoreCase(cmd);
        }
        return result;
    }
}
