package com.spider.mtgcard.client.guidebook;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class GuideBookClient {
    public static void open(ResourceLocation chapterId) {
        Minecraft.getInstance().setScreen(new GuideBookScreen(chapterId));
    }
    private GuideBookClient() {}
}
