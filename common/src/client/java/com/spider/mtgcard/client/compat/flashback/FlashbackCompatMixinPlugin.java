package com.spider.mtgcard.client.compat.flashback;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class FlashbackCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean flashbackPresent;

    @Override
    public void onLoad(String mixinPackage) {
        this.flashbackPresent = isModLoaded("flashback");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return flashbackPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    static boolean isModLoaded(String modId) {
        Boolean fabricLoaded = isFabricModLoaded(modId);
        if (fabricLoaded != null) {
            return fabricLoaded;
        }

        Boolean neoForgeLoaded = isNeoForgeModLoaded(modId);
        return neoForgeLoaded != null && neoForgeLoaded;
    }

    private static Boolean isFabricModLoaded(String modId) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader", false,
                    FlashbackCompatMixinPlugin.class.getClassLoader());
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (Boolean) loaderClass.getMethod("isModLoaded", String.class).invoke(loader, modId);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Boolean isNeoForgeModLoaded(String modId) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList", false,
                    FlashbackCompatMixinPlugin.class.getClassLoader());
            Object modList = modListClass.getMethod("get").invoke(null);
            return (Boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
