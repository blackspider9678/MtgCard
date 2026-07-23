package net.fabricmc.fabric.api.networking.v1;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.net.NeoForgeClientPayloadHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PayloadTypeRegistry {
    private static final DirectionalRegistry SERVERBOUND = new DirectionalRegistry(Direction.SERVERBOUND);
    private static final DirectionalRegistry CLIENTBOUND = new DirectionalRegistry(Direction.CLIENTBOUND);

    private PayloadTypeRegistry() {
    }

    public static DirectionalRegistry serverboundPlay() {
        return SERVERBOUND;
    }

    public static DirectionalRegistry clientboundPlay() {
        return CLIENTBOUND;
    }

    public static void reset() {
        SERVERBOUND.entries.clear();
        CLIENTBOUND.entries.clear();
        ServerPlayNetworking.clearReceivers();
    }

    public static void apply(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Mtgcard.MOD_ID);

        for (Entry<?> entry : CLIENTBOUND.entries.values()) {
            registerClientbound(registrar, entry);
        }
        for (Entry<?> entry : SERVERBOUND.entries.values()) {
            registerServerbound(registrar, entry);
        }
    }

    private static <T extends CustomPacketPayload> void registerClientbound(PayloadRegistrar registrar, Entry<T> entry) {
        registrar.playToClient(
                entry.type,
                entry.codec,
                (payload, context) -> context.enqueueWork(() ->
                        NeoForgeClientPayloadHandlers.handle(actionName(payload.type()), payload)
                )
        );
    }

    private static <T extends CustomPacketPayload> void registerServerbound(PayloadRegistrar registrar, Entry<T> entry) {
        ServerPlayNetworking.PlayPayloadHandler<T> handler = ServerPlayNetworking.receiver(entry.type);
        if (handler == null) {
            registrar.playToServer(entry.type, entry.codec, (payload, context) -> {
            });
            return;
        }

        registrar.playToServer(
                entry.type,
                entry.codec,
                (payload, context) -> handler.receive(payload, new ServerPlayNetworking.Context(context))
        );
    }

    private static String actionName(CustomPacketPayload.Type<?> type) {
        String path = type.id().getPath();
        return switch (path) {
            case "deck_overlay" -> "deck_control_overlay";
            case "deck_cascade_overlay" -> "deck_control_cascade";
            case "open_custom_import_gui" -> "custom_import_open";
            case "custom_art_ready" -> "custom_art_ready";
            default -> path;
        };
    }

    private enum Direction {
        SERVERBOUND,
        CLIENTBOUND
    }

    private record Entry<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec
    ) {
    }

    public static final class DirectionalRegistry {
        private final Direction direction;
        private final Map<CustomPacketPayload.Type<?>, Entry<?>> entries = new LinkedHashMap<>();

        private DirectionalRegistry(Direction direction) {
            this.direction = direction;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec codec) {
            entries.put(type, new Entry<>(type, (StreamCodec<? super RegistryFriendlyByteBuf, T>) codec));
        }

        public boolean isServerbound() {
            return direction == Direction.SERVERBOUND;
        }
    }
}
