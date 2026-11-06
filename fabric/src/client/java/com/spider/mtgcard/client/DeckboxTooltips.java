package com.spider.mtgcard.client;

import com.spider.mtgcard.deckbox.DeckboxNaming;
import com.spider.mtgcard.registry.ModBlocks;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class DeckboxTooltips {

    private DeckboxTooltips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, displayComponent, lines) -> {
            if (!ModBlocks.isDeckbox(stack)) return;

            Component commanderName = DeckboxNaming.getCommanderName(stack);
            Component partnerName = DeckboxNaming.getPartnerName(stack);

            if (commanderName != null) {
                lines.add(Component.literal("Commander: ").append(commanderName));
            }
            if (partnerName != null) {
                lines.add(Component.literal("Partner: ").append(partnerName));
            }
        });
    }
}
