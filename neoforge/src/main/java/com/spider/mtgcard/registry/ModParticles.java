package com.spider.mtgcard.registry;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Mtgcard.MOD_ID);

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> ORBIT_GLYPH_HOLDER =
            PARTICLES.register("orbit_glyph", () -> new SimpleParticleType(false));
    public static SimpleParticleType ORBIT_GLYPH;

    private ModParticles() {}

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.PARTICLE_TYPE)) {
                ORBIT_GLYPH = ORBIT_GLYPH_HOLDER.get();
            }
        });
    }

    public static void init() {
    }
}
