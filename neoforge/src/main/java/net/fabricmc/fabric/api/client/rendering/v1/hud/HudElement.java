package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface HudElement {
    void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tickCounter);
}
