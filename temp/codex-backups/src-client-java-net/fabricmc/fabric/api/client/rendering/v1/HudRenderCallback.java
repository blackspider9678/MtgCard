package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface HudRenderCallback {
    Event EVENT = new Event();

    void onHudRender(GuiGraphics graphics, DeltaTracker tickCounter);

    final class Event {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        public void register(HudRenderCallback callback) {
            Identifier id = Identifier.fromNamespaceAndPath("mtgcard", "hud_compat/" + COUNTER.getAndIncrement());
            HudElementRegistry.addLast(id, (graphics, tickCounter) -> callback.onHudRender(GuiGraphics.wrap(graphics), tickCounter));
        }
    }
}
