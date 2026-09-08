package net.fabricmc.fabric.api.client.networking.v1;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
public final class ClientPlayConnectionEvents {
    public static final Disconnect DISCONNECT = new Disconnect();
    public static final class Disconnect {
        public void register(java.util.function.BiConsumer<Object, Minecraft> callback) {
            NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
                com.spider.mtgcard.client.net.NeoForgeClientPayloadHandlers.clearIncomingArt();
                callback.accept(event, Minecraft.getInstance());
            });
        }
    }
}
