package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.CardLargeViewScreen;
import com.spider.mtgcard.client.AttachedCardsScreen;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CardDisplayClientPackets {

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
                com.spider.mtgcard.net.payload.CardDisplayPayloads.OpenDisplayViewS2C.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc == null) return;

                    // handSlot is irrelevant for display mode; pass -1
                    mc.gui.setScreen(new com.spider.mtgcard.client.CardLargeViewScreen(
                            payload.stack(),
                            -1,
                            payload.entityId(),
                            payload.hostId(),
                            payload.version(),
                            payload.selectedCardId(),
                            payload.attachmentCount()
                    ));
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CardDisplayPayloads.OpenAttachmentsS2C.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    var mc = Minecraft.getInstance();
                    if (mc == null) return;

                    mc.gui.setScreen(new AttachedCardsScreen(
                            payload.entityId(),
                            payload.hostId(),
                            payload.version(),
                            payload.selectedCardId(),
                            payload.hostStack(),
                            payload.attachments()
                    ));
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                CardDisplayPayloads.CloseDisplayScreensS2C.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    var mc = Minecraft.getInstance();
                    if (mc == null) return;
                    if (mc.player != null && payload.message() != null && !payload.message().isBlank()) {
                        mc.player.sendSystemMessage(Component.literal(payload.message()));
                    }
                    if (mc.gui.screen() instanceof CardLargeViewScreen || mc.gui.screen() instanceof AttachedCardsScreen) {
                        mc.gui.setScreen(null);
                    }
                })
        );
    }

    private CardDisplayClientPackets() {}
}
