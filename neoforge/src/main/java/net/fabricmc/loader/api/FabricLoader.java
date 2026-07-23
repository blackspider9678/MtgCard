package net.fabricmc.loader.api;

import net.fabricmc.api.EnvType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;

import java.nio.file.Path;

public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();

    private FabricLoader() {
    }

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public EnvType getEnvironmentType() {
        return FMLEnvironment.getDist().isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }
}
