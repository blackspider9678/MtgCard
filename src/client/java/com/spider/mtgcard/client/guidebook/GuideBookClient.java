package com.spider.mtgcard.client.guidebook;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class GuideBookClient {
    public static void open(Identifier chapterId) {
        MinecraftClient.getInstance().setScreen(new GuideBookScreen(chapterId));
    }
    private GuideBookClient() {}
}
