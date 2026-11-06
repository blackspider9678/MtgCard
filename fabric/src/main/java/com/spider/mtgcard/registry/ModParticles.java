package com.spider.mtgcard.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public final class ModParticles {
    private ModParticles() {}

    public static final SimpleParticleType ORBIT_GLYPH = FabricParticleTypes.simple();

    public static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, ResourceLocation.fromNamespaceAndPath("mtgcard", "orbit_glyph"), ORBIT_GLYPH);
    }
}
