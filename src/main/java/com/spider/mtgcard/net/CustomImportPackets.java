package com.spider.mtgcard.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class CustomImportPackets {
    public static final Identifier OPEN_GUI_ID = Identifier.of("mtgcard","open_custom_import_gui");

    public record OpenImportGui() implements CustomPayload {
        public static final Id<OpenImportGui> ID = new Id<>(OPEN_GUI_ID);

        // safer than PacketCodec.unit (not always present depending on mappings/version)
        public static final PacketCodec<PacketByteBuf, OpenImportGui> CODEC =
                new PacketCodec<>() {
                    @Override
                    public OpenImportGui decode(PacketByteBuf buf) {
                        return new OpenImportGui();
                    }

                    @Override
                    public void encode(PacketByteBuf buf, OpenImportGui value) {
                        // nothing to write
                    }
                };

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Call during common init. Registers the S2C payload type. */
    public static void registerTypes() {
        PayloadTypeRegistry.playS2C().register(OpenImportGui.ID, OpenImportGui.CODEC);
    }

    /** Server-side helper to open the GUI on a client. */
    public static void openImportGui(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, new OpenImportGui());
    }

    private CustomImportPackets() {}
}
