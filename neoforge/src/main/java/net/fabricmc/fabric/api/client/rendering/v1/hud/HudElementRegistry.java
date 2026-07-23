package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.resources.Identifier;

public final class HudElementRegistry {
    private HudElementRegistry() {
    }

    public static void addLast(Identifier id, HudElement element) {
        // NeoForge HUD rendering is wired directly by MtgcardNeoForgeClient.
    }
}
