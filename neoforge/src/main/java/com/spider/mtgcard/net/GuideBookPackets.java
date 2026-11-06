package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class GuideBookPackets {
    public static final ResourceLocation OPEN_ID = ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_open");

    public record OpenPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenPayload> ID = new CustomPacketPayload.Type<>(OPEN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.unit(new OpenPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void init() {
    }

    public static void sendOpen(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenPayload());
    }

    private GuideBookPackets() {}
}
