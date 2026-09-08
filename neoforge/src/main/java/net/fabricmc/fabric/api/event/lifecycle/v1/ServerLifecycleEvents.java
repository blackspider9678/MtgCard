package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
public final class ServerLifecycleEvents {
    public static final Stopped SERVER_STOPPED = new Stopped();
    public static final class Stopped {
        public void register(java.util.function.Consumer<MinecraftServer> callback) {
            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> callback.accept(event.getServer()));
        }
    }
}
