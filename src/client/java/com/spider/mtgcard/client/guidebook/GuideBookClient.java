package com.spider.mtgcard.client.guidebook;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class GuideBookClient {
    public static void open(Identifier chapterId) {
        Minecraft.getInstance().setScreen(new GuideBookScreen(chapterId));
    }
    private GuideBookClient() {}
}
