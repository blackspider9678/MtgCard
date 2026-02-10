package com.spider.mtgcard.net.payload;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CardDisplayPayloads {

    // ---------- S2C: open large view ----------
    public record OpenDisplayViewS2C(int entityId, ItemStack stack) implements CustomPayload {
        public static final CustomPayload.Id<OpenDisplayViewS2C> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_open"));

        public static final PacketCodec<RegistryByteBuf, OpenDisplayViewS2C> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, OpenDisplayViewS2C::entityId,
                        ItemStack.PACKET_CODEC, OpenDisplayViewS2C::stack,
                        OpenDisplayViewS2C::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: set face ----------
    public record DisplaySetFaceC2S(int entityId, int face) implements CustomPayload {
        public static final CustomPayload.Id<DisplaySetFaceC2S> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_set_face"));

        public static final PacketCodec<RegistryByteBuf, DisplaySetFaceC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, DisplaySetFaceC2S::entityId,
                        PacketCodecs.VAR_INT, DisplaySetFaceC2S::face,
                        DisplaySetFaceC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: set counter value ----------
    public record DisplaySetCounterValueC2S(int entityId, String key, int value) implements CustomPayload {
        public static final CustomPayload.Id<DisplaySetCounterValueC2S> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_set_counter_value"));

        public static final PacketCodec<RegistryByteBuf, DisplaySetCounterValueC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, DisplaySetCounterValueC2S::entityId,
                        PacketCodecs.STRING, DisplaySetCounterValueC2S::key,
                        PacketCodecs.VAR_INT, DisplaySetCounterValueC2S::value,
                        DisplaySetCounterValueC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: delete counter key ----------
    public record DisplayDeleteCounterC2S(int entityId, String key) implements CustomPayload {
        public static final CustomPayload.Id<DisplayDeleteCounterC2S> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_delete_counter"));

        public static final PacketCodec<RegistryByteBuf, DisplayDeleteCounterC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, DisplayDeleteCounterC2S::entityId,
                        PacketCodecs.STRING,  DisplayDeleteCounterC2S::key,
                        DisplayDeleteCounterC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ---------- C2S: set hidden ----------
    public record DisplaySetHiddenC2S(int entityId, boolean hidden) implements CustomPayload {
        public static final CustomPayload.Id<DisplaySetHiddenC2S> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_set_hidden"));

        public static final PacketCodec<RegistryByteBuf, DisplaySetHiddenC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, DisplaySetHiddenC2S::entityId,
                        PacketCodecs.BOOLEAN,    DisplaySetHiddenC2S::hidden,
                        DisplaySetHiddenC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }


    // ---------- C2S: set counter meta ----------
    public record DisplaySetCounterMetaC2S(int entityId, String key, String displayName, String iconKey) implements CustomPayload {
        public static final CustomPayload.Id<DisplaySetCounterMetaC2S> ID =
                new CustomPayload.Id<>(Identifier.of("mtgcard", "card_display_set_counter_meta"));

        public static final PacketCodec<RegistryByteBuf, DisplaySetCounterMetaC2S> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, DisplaySetCounterMetaC2S::entityId,
                        PacketCodecs.STRING,  DisplaySetCounterMetaC2S::key,
                        PacketCodecs.STRING,  DisplaySetCounterMetaC2S::displayName,
                        PacketCodecs.STRING,  DisplaySetCounterMetaC2S::iconKey,
                        DisplaySetCounterMetaC2S::new
                );

        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private CardDisplayPayloads() {}
}
