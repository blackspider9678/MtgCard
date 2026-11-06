package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.resources.ResourceLocation;

public final class HudElementRegistry {
    private HudElementRegistry() {
    }

    public static void addLast(ResourceLocation id, HudElement element) {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> element.render(graphics, tickCounter));
    }
}
