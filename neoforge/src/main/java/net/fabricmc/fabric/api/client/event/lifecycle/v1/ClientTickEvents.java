package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    public static final EndTick END_CLIENT_TICK = new EndTick();

    private ClientTickEvents() {
    }

    public interface EndTickCallback {
        void onEndTick(Minecraft client);
    }

    public static final class EndTick {
        public void register(EndTickCallback callback) {
            // NeoForge client ticks are wired by MtgcardNeoForgeClient.
        }
    }
}
