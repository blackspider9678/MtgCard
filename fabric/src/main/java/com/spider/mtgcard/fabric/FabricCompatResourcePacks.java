package com.spider.mtgcard.fabric;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.Identifier;

final class FabricCompatResourcePacks {
    private static final String BIOMES_O_PLENTY = "biomesoplenty";
    private static final String BIOMES_WE_HAVE_GONE = "biomeswevegone";

    private FabricCompatResourcePacks() {
    }

    static void register() {
        ModContainer mtgcard = FabricLoader.getInstance()
                .getModContainer(Mtgcard.MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Unable to locate the MTGCard mod container"));

        registerWhenLoaded(mtgcard, BIOMES_O_PLENTY, "biomesoplenty_compat");
        registerWhenLoaded(mtgcard, BIOMES_WE_HAVE_GONE, "biomeswevegone_compat");
    }

    private static void registerWhenLoaded(ModContainer mtgcard, String requiredModId, String packPath) {
        if (!FabricLoader.getInstance().isModLoaded(requiredModId)) {
            return;
        }

        boolean registered = ResourceManagerHelper.registerBuiltinResourcePack(
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, packPath),
                mtgcard,
                ResourcePackActivationType.ALWAYS_ENABLED
        );
        if (!registered) {
            Mtgcard.LOGGER.warn("Could not register MTGCard compatibility resource pack {}", packPath);
        }
    }
}
