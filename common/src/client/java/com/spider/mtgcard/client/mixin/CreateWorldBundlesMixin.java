package com.spider.mtgcard.client.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;

/** Select bundles before the new world's initial data-pack load. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldBundlesMixin {
    @ModifyArg(method = "openFresh", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;createDefaultLoadConfig(Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/world/level/WorldDataConfiguration;)Lnet/minecraft/server/WorldLoader$InitConfig;"), index = 1)
    private static WorldDataConfiguration mtgcard$defaultBundles(WorldDataConfiguration defaults) {
        var enabled = new ArrayList<>(defaults.dataPacks().getEnabled());
        if (!enabled.contains("bundle")) enabled.add("bundle");
        var disabled = new ArrayList<>(defaults.dataPacks().getDisabled());
        disabled.remove("bundle");
        return new WorldDataConfiguration(new DataPackConfig(enabled, disabled),
                defaults.enabledFeatures().join(FeatureFlagSet.of(FeatureFlags.BUNDLE)));
    }
}
