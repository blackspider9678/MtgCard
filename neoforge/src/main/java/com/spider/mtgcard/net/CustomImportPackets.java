package com.spider.mtgcard.net;

import com.spider.mtgcard.config.ImportPerms;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CustomImportPackets {
    public static final Identifier OPEN_GUI_ID = Identifier.fromNamespaceAndPath("mtgcard", "open_custom_import_gui");

    public record OpenImportGui() implements CustomPacketPayload {
        public static final Type<OpenImportGui> ID = new Type<>(OPEN_GUI_ID);
        public static final StreamCodec<FriendlyByteBuf, OpenImportGui> CODEC =
                new StreamCodec<>() {
                    @Override
                    public OpenImportGui decode(FriendlyByteBuf buf) {
                        return new OpenImportGui();
                    }

                    @Override
                    public void encode(FriendlyByteBuf buf, OpenImportGui value) {
                    }
                };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static boolean openImportGui(ServerPlayer to) {
        if (to == null || !ImportPerms.canImport(to)) {
            return false;
        }
        PacketDistributor.sendToPlayer(to, new OpenImportGui());
        return true;
    }

    private CustomImportPackets() {}
}
