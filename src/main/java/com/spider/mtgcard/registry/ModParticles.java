package com.spider.mtgcard.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModParticles {
    private ModParticles() {}

    public static final SimpleParticleType ORBIT_GLYPH = FabricParticleTypes.simple();

    public static void init() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of("mtgcard", "orbit_glyph"), ORBIT_GLYPH);
    }
}
