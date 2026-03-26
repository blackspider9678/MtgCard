package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

public final class DeckPayloads {
    private DeckPayloads() {}

    // -------------------------
    // S2C: request export
    // -------------------------
    public record DeckExportRequestS2C(String name) implements CustomPacketPayload {
        public static final Type<DeckExportRequestS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "deck_export_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DeckExportRequestS2C> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, DeckExportRequestS2C::name, DeckExportRequestS2C::new);

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // -------------------------
    // S2C: request list
    // -------------------------
    public record DeckListRequestS2C() implements CustomPacketPayload {
        public static final Type<DeckListRequestS2C> ID =
                new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "deck_list_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DeckListRequestS2C> CODEC =
                StreamCodec.unit(new DeckListRequestS2C());

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
