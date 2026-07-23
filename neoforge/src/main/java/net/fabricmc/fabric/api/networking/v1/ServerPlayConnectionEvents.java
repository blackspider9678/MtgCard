package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerPlayConnectionEvents {
    public static final Disconnect DISCONNECT = new Disconnect();
    public static final Join JOIN = new Join();

    private ServerPlayConnectionEvents() {
    }

    public static final class Handler {
        public final ServerPlayer player;

        public Handler(ServerPlayer player) {
            this.player = player;
        }
    }

    public interface DisconnectCallback {
        void onDisconnect(Handler handler, MinecraftServer server);
    }

    public interface JoinCallback {
        void onJoin(Handler handler, Object sender, MinecraftServer server);
    }

    public static final class Disconnect {
        public void register(DisconnectCallback callback) {
            // NeoForge login/logout hooks are registered by MtgcardNeoForge.
        }
    }

    public static final class Join {
        public void register(JoinCallback callback) {
            // NeoForge login/logout hooks are registered by MtgcardNeoForge.
        }
    }
}
