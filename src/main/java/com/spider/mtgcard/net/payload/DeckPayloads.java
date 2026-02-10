package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class DeckPayloads {
    private DeckPayloads() {}

    // -------------------------
    // S2C: request export
    // -------------------------
    public record DeckExportRequestS2C(String name) implements CustomPayload {
        public static final Id<DeckExportRequestS2C> ID =
                new Id<>(Identifier.of("mtgcard", "deck_export_request"));

        public static final PacketCodec<RegistryByteBuf, DeckExportRequestS2C> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, DeckExportRequestS2C::name, DeckExportRequestS2C::new);

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // -------------------------
    // S2C: request list
    // -------------------------
    public record DeckListRequestS2C() implements CustomPayload {
        public static final Id<DeckListRequestS2C> ID =
                new Id<>(Identifier.of("mtgcard", "deck_list_request"));

        public static final PacketCodec<RegistryByteBuf, DeckListRequestS2C> CODEC =
                PacketCodec.unit(new DeckListRequestS2C());

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
