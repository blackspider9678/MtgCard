package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
public final class ServerTickEvents {
    public static final EndTick END_SERVER_TICK = new EndTick();
    public static final class EndTick {
        public void register(java.util.function.Consumer<MinecraftServer> callback) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer()));
        }
    }
}
