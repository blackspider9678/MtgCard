package com.spider.mtgcard.client.dice;

import com.spider.mtgcard.dice.DiceCustomizerPackets;
import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.network.chat.Component;

public final class DiceClientHooks {
    private static boolean initialized;

    public static void init() {
        if (initialized) return;
        initialized = true;
        var renderer = new com.spider.mtgcard.client.render.CustomizedDiceRenderer();
        net.minecraft.client.renderer.item.ItemProperties.register(com.spider.mtgcard.item.ModItems.CARD,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mtgcard","sleeved_card"),
            (stack,level,entity,seed)->com.spider.mtgcard.api.CardSleeves.get(stack).isPresent()?1f:0f);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
            com.spider.mtgcard.item.ModItems.CARD, renderer::renderByItem);

        for (var type : com.spider.mtgcard.dice.DiceType.values()) {
            net.minecraft.client.renderer.item.ItemProperties.register(type.item(), net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mtgcard", "customized_dice"),
                (stack,level,entity,seed) -> stack.has(com.spider.mtgcard.data.ModDataComponents.DICE_APPEARANCE) ? 1f : 0f);
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(type.item(), renderer::renderByItem);
        }



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
