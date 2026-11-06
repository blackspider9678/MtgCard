package com.spider.mtgcard.client.compat.render.rendertype;

import net.minecraft.resources.ResourceLocation;

public final class RenderTypes {
    private RenderTypes() {
    }

    public static RenderType entityCutout(ResourceLocation texture) {
        return RenderType.wrap(net.minecraft.client.renderer.RenderType.entityCutout(texture));
    }

    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        return RenderType.wrap(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture));
    }

    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.wrap(net.minecraft.client.renderer.RenderType.entityTranslucent(texture));
    }
}
