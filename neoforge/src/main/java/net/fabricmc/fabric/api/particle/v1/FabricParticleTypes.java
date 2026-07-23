package net.fabricmc.fabric.api.particle.v1;

import net.minecraft.core.particles.SimpleParticleType;

public final class FabricParticleTypes {
    private FabricParticleTypes() {
    }

    public static SimpleParticleType simple() {
        return new SimpleParticleType(false) {
        };
    }
}
