package com.spider.mtgcard.client.sleeve;

import com.spider.mtgcard.sleeve.SleeveCustomizerPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.network.chat.Component;

public final class SleeveClientHooks {
    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof LoomScreen)) return;
            int left = (width - 176) / 2, top = (height - 166) / 2;
            Screens.getButtons(screen).add(Button.builder(Component.literal("Card Sleeves"),
                    b -> ClientPlayNetworking.send(new SleeveCustomizerPackets.Open()))
                    .bounds(Math.min(width - 98, left + 180), Math.max(26, top + 26), 94, 20).build());
        });
    }
    private SleeveClientHooks() {}
}
