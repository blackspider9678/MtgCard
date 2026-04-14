package com.spider.mtgcard.net;

import com.spider.mtgcard.config.ImportPerms;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

public final class CustomImportPackets {
    public static final Identifier OPEN_GUI_ID = Identifier.fromNamespaceAndPath("mtgcard","open_custom_import_gui");

    public record OpenImportGui() implements CustomPacketPayload {
        public static final Type<OpenImportGui> ID = new Type<>(OPEN_GUI_ID);

        // safer than PacketCodec.unit (not always present depending on mappings/version)
        public static final StreamCodec<FriendlyByteBuf, OpenImportGui> CODEC =
                new StreamCodec<>() {
                    @Override
                    public OpenImportGui decode(FriendlyByteBuf buf) {
                        return new OpenImportGui();
                    }

                    @Override
                    public void encode(FriendlyByteBuf buf, OpenImportGui value) {
                        // nothing to write
                    }
                };

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** Call during common init. Registers the S2C payload type. */
    public static void registerTypes() {
        PayloadTypeRegistry.playS2C().register(OpenImportGui.ID, OpenImportGui.CODEC);
    }

    /** Server-side helper to open the GUI on a client. */
    public static boolean openImportGui(ServerPlayer to) {
        if (to == null || !ImportPerms.canImport(to)) {
            return false;
        }
        ServerPlayNetworking.send(to, new OpenImportGui());
        return true;
    }

    private CustomImportPackets() {}
}
