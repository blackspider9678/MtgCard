package net.fabricmc.fabric.api.creativetab.v1;

import net.minecraft.world.item.CreativeModeTab;

public final class FabricCreativeModeTab {
    private FabricCreativeModeTab() {
    }

    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    }
}
