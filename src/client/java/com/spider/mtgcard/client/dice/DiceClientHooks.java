package com.spider.mtgcard.client.dice;

import com.spider.mtgcard.client.render.DiceItemRenderer;
import com.spider.mtgcard.dice.DiceCustomizerPackets;
import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;

public final class DiceClientHooks {
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;

        SpecialModelRenderers.ID_MAPPER.put(ModRegistry.id("dice"), DiceItemRenderer.Unbaked.MAP_CODEC);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof LoomScreen)) {
                return;
            }

            int loomLeft = (scaledWidth - 176) / 2;
            int loomTop = (scaledHeight - 166) / 2;
            int buttonX = Math.min(scaledWidth - 98, loomLeft + 180);
            int buttonY = Math.max(4, loomTop + 4);

            Screens.getButtons(screen).add(Button.builder(
                    Component.translatable("screen.mtgcard.dice_customizer.button"),
                    button -> ClientPlayNetworking.send(new DiceCustomizerPackets.OpenDiceCustomizerC2S())
            ).bounds(buttonX, buttonY, 94, 20).build());
        });
    }

    private DiceClientHooks() {}
}
