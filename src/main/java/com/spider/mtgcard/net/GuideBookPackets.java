package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class GuideBookPackets {

    public static final Identifier OPEN_ID = Identifier.of(Mtgcard.MOD_ID, "guide_open");

    // C2S isn't needed. We do S2C: server tells client to open.
    public record OpenPayload() implements CustomPayload {
        public static final CustomPayload.Id<OpenPayload> ID = new CustomPayload.Id<>(OPEN_ID);
        public static final PacketCodec<RegistryByteBuf, OpenPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_INT, ignored -> 0, v -> new OpenPayload()); // minimal no-data codec

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenPayload.ID, OpenPayload.CODEC);
    }

    public static void sendOpen(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new OpenPayload());
    }

    private GuideBookPackets() {}
}
