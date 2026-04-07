package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

public final class GuideBookPackets {

    public static final Identifier OPEN_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_open");

    // C2S isn't needed. We do S2C: server tells client to open.
    public record OpenPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenPayload> ID = new CustomPacketPayload.Type<>(OPEN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ignored -> 0, v -> new OpenPayload()); // minimal no-data codec

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenPayload.ID, OpenPayload.CODEC);
    }

    public static void sendOpen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenPayload());
    }

    private GuideBookPackets() {}
}
