package com.spider.mtgcard.deckcontrol;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class DeckControlFx {
    private DeckControlFx() {}

    private static final MethodHandle CLIENT_TICK;

    static {
        MethodHandle mh = null;

        // Only attempt to link the client class when we're actually on the client
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            try {
                Class<?> clazz = Class.forName("com.spider.mtgcard.client.deckcontrol.DeckControlClientFx");
                mh = MethodHandles.publicLookup().findStatic(
                        clazz,
                        "tickParticles",
                        MethodType.methodType(void.class, DeckControlBlockEntity.class)
                );
            } catch (Throwable ignored) {
                // If anything goes wrong, particles just won't run (safe fallback)
            }
        }

        CLIENT_TICK = mh;
    }

    public static void tickParticles(DeckControlBlockEntity be) {
        if (CLIENT_TICK == null) return;

        try {
            CLIENT_TICK.invoke(be);
        } catch (Throwable ignored) {
        }
    }
}
