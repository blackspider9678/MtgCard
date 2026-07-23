package com.spider.mtgcard.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

public final class ModParticles {
    private ModParticles() {}

    public static final SimpleParticleType ORBIT_GLYPH = FabricParticleTypes.simple();

    public static void init() {
        // Registration is performed by the loader-specific entrypoint.
    }
}
