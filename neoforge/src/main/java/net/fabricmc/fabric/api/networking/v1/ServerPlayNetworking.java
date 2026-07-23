package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public final class ServerPlayNetworking {
    private static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> RECEIVERS = new HashMap<>();

    private ServerPlayNetworking() {
    }

    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public static final class Context {
        private final IPayloadContext context;

        Context(IPayloadContext context) {
            this.context = context;
        }

        public MinecraftServer server() {
            ServerPlayer player = player();
            return player == null ? null : player.level().getServer();
        }

        public ServerPlayer player() {
            return context.player() instanceof ServerPlayer player ? player : null;
        }

        public IPayloadContext neoForgeContext() {
            return context;
        }
    }

    static void clearReceivers() {
        RECEIVERS.clear();
    }

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> PlayPayloadHandler<T> receiver(CustomPacketPayload.Type<T> type) {
        return (PlayPayloadHandler<T>) RECEIVERS.get(type);
    }

    public static <T extends CustomPacketPayload> void registerGlobalReceiver(
            CustomPacketPayload.Type<T> type,
            PlayPayloadHandler<T> handler
    ) {
        RECEIVERS.put(type, handler);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static Packet<?> createClientboundPacket(CustomPacketPayload payload) {
        return new ClientboundCustomPayloadPacket(payload);
    }
}
