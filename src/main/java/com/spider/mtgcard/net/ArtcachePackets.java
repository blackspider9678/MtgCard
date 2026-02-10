package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ArtcachePackets {

    public static final Identifier ART_CACHE_REQ_ID = Identifier.of("mtgcard", "artcache_request");

    public enum Action {
        STATS,
        PURGE,
        REBUILD
    }

    /** Server -> Client request */
    public record ArtcacheRequest(Action action) implements CustomPayload {
        public static final Id<ArtcacheRequest> ID = new Id<>(ART_CACHE_REQ_ID);

        public static final PacketCodec<RegistryByteBuf, ArtcacheRequest> CODEC =
                PacketCodec.ofStatic(
                        (buf, p) -> buf.writeVarInt(p.action().ordinal()),
                        (buf) -> {
                            int ord = buf.readVarInt();
                            Action[] vals = Action.values();
                            if (ord < 0 || ord >= vals.length) ord = 0;
                            return new ArtcacheRequest(vals[ord]);
                        }
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Call from your ModInitializer (common). */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(ArtcacheRequest.ID, ArtcacheRequest.CODEC);
    }

    private ArtcachePackets() {}
}
