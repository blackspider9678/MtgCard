package com.spider.mtgcard.net.payload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class CardDisplayPayloads {

    // ---------- S2C: open large view ----------
    public record OpenDisplayViewS2C(int entityId, ItemStack stack) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenDisplayViewS2C> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDisplayViewS2C> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, OpenDisplayViewS2C::entityId,
                        ItemStack.STREAM_CODEC, OpenDisplayViewS2C::stack,
                        OpenDisplayViewS2C::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set face ----------
    public record DisplaySetFaceC2S(int entityId, int face) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetFaceC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_face"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetFaceC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DisplaySetFaceC2S::entityId,
                        ByteBufCodecs.VAR_INT, DisplaySetFaceC2S::face,
                        DisplaySetFaceC2S::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set counter value ----------
    public record DisplaySetCounterValueC2S(int entityId, String key, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetCounterValueC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_counter_value"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetCounterValueC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DisplaySetCounterValueC2S::entityId,
                        ByteBufCodecs.STRING_UTF8, DisplaySetCounterValueC2S::key,
                        ByteBufCodecs.VAR_INT, DisplaySetCounterValueC2S::value,
                        DisplaySetCounterValueC2S::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: delete counter key ----------
    public record DisplayDeleteCounterC2S(int entityId, String key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplayDeleteCounterC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_delete_counter"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplayDeleteCounterC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DisplayDeleteCounterC2S::entityId,
                        ByteBufCodecs.STRING_UTF8,  DisplayDeleteCounterC2S::key,
                        DisplayDeleteCounterC2S::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set hidden ----------
    public record DisplaySetHiddenC2S(int entityId, boolean hidden) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetHiddenC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_hidden"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetHiddenC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DisplaySetHiddenC2S::entityId,
                        ByteBufCodecs.BOOL,    DisplaySetHiddenC2S::hidden,
                        DisplaySetHiddenC2S::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    // ---------- C2S: set counter meta ----------
    public record DisplaySetCounterMetaC2S(int entityId, String key, String displayName, String iconKey) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetCounterMetaC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_counter_meta"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetCounterMetaC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, DisplaySetCounterMetaC2S::entityId,
                        ByteBufCodecs.STRING_UTF8,  DisplaySetCounterMetaC2S::key,
                        ByteBufCodecs.STRING_UTF8,  DisplaySetCounterMetaC2S::displayName,
                        ByteBufCodecs.STRING_UTF8,  DisplaySetCounterMetaC2S::iconKey,
                        DisplaySetCounterMetaC2S::new
                );

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private CardDisplayPayloads() {}
}
