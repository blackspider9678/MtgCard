package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ClientPlayNetworking {
    private ClientPlayNetworking() {
    }

    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static final class Context {
        public net.minecraft.client.Minecraft client() {
            return net.minecraft.client.Minecraft.getInstance();
        }
    }

    public static void send(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler
    ) {
        // Clientbound registrations are handled by the NeoForge payload registrar.
    }
}
