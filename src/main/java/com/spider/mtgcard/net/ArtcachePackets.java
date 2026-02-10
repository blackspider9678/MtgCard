package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class ArtcachePackets {

    public static final Identifier ART_CACHE_REQ_ID = Identifier.fromNamespaceAndPath("mtgcard", "artcache_request");

    public enum Action {
        STATS,
        PURGE,
        REBUILD
    }

    /** Server -> Client request */
    public record ArtcacheRequest(Action action) implements CustomPacketPayload {
        public static final Type<ArtcacheRequest> ID = new Type<>(ART_CACHE_REQ_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ArtcacheRequest> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeVarInt(p.action().ordinal()),
                        (buf) -> {
                            int ord = buf.readVarInt();
                            Action[] vals = Action.values();
                            if (ord < 0 || ord >= vals.length) ord = 0;
                            return new ArtcacheRequest(vals[ord]);
                        }
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Call from your ModInitializer (common). */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(ArtcacheRequest.ID, ArtcacheRequest.CODEC);
    }

    private ArtcachePackets() {}
}
