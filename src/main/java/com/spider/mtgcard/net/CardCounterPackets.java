package com.spider.mtgcard.net;

import com.spider.mtgcard.util.CardCounterNbt;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public final class CardCounterPackets {

    private static final StreamCodec<RegistryFriendlyByteBuf, InteractionHand> HAND_CODEC = new StreamCodec<>() {
        @Override
        public InteractionHand decode(RegistryFriendlyByteBuf buf) {
            int i = buf.readVarInt();
            InteractionHand[] values = InteractionHand.values();
            if (i < 0 || i >= values.length) i = 0;
            return values[i];
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, InteractionHand value) {
            buf.writeVarInt(value.ordinal());
        }
    };

    public record SetCardCounterC2S(InteractionHand hand, String key, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetCardCounterC2S> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_card_counter"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetCardCounterC2S> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SetCardCounterC2S decode(RegistryFriendlyByteBuf buf) {
                InteractionHand hand = HAND_CODEC.decode(buf);
                String key = buf.readUtf();
                int value = buf.readVarInt();
                return new SetCardCounterC2S(hand, key, value);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SetCardCounterC2S value) {
                HAND_CODEC.encode(buf, value.hand());
                buf.writeUtf(value.key());
                buf.writeVarInt(value.value());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetCardCounterIconC2S(InteractionHand hand, String key, String icon) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SetCardCounterIconC2S> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "set_card_counter_icon"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SetCardCounterIconC2S> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public SetCardCounterIconC2S decode(RegistryFriendlyByteBuf buf) {
                InteractionHand hand = HAND_CODEC.decode(buf);
                String key = buf.readUtf();
                String icon = buf.readUtf();
                return new SetCardCounterIconC2S(hand, key, icon);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SetCardCounterIconC2S value) {
                HAND_CODEC.encode(buf, value.hand());
                buf.writeUtf(value.key());
                buf.writeUtf(value.icon());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RemoveCardCounterC2S(InteractionHand hand, String key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RemoveCardCounterC2S> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "remove_card_counter"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveCardCounterC2S> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public RemoveCardCounterC2S decode(RegistryFriendlyByteBuf buf) {
                InteractionHand hand = HAND_CODEC.decode(buf);
                String key = buf.readUtf();
                return new RemoveCardCounterC2S(hand, key);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, RemoveCardCounterC2S value) {
                HAND_CODEC.encode(buf, value.hand());
                buf.writeUtf(value.key());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(SetCardCounterC2S.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getItemInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.setCounter(stack, payload.key(), payload.value());
                player.getInventory().setChanged();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCardCounterIconC2S.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getItemInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.setIcon(stack, payload.key(), payload.icon());
                player.getInventory().setChanged();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveCardCounterC2S.TYPE, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getItemInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.removeCounter(stack, payload.key());
                player.getInventory().setChanged();
            });
        });
    }

    private CardCounterPackets() {}
}
