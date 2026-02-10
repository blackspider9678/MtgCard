// CardCounterPackets.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.util.CardCounterNbt;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public final class CardCounterPackets {

    // Enum codec for Hand (MAIN_HAND=0, OFF_HAND=1)
    private static final PacketCodec<RegistryByteBuf, Hand> HAND_CODEC = new PacketCodec<>() {
        @Override
        public Hand decode(RegistryByteBuf buf) {
            int i = buf.readVarInt();
            Hand[] v = Hand.values();
            if (i < 0 || i >= v.length) i = 0;
            return v[i];
        }

        @Override
        public void encode(RegistryByteBuf buf, Hand value) {
            buf.writeVarInt(value.ordinal());
        }
    };

    public record SetCardCounterC2S(Hand hand, String key, int value) implements CustomPayload {
        public static final Id<SetCardCounterC2S> ID = new Id<>(Identifier.of("mtgcard", "set_card_counter"));
        public static final PacketCodec<RegistryByteBuf, SetCardCounterC2S> CODEC = PacketCodec.tuple(
                HAND_CODEC, SetCardCounterC2S::hand,
                PacketCodecs.STRING, SetCardCounterC2S::key,
                PacketCodecs.VAR_INT, SetCardCounterC2S::value,
                SetCardCounterC2S::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetCardCounterIconC2S(Hand hand, String key, String icon) implements CustomPayload {
        public static final Id<SetCardCounterIconC2S> ID = new Id<>(Identifier.of("mtgcard", "set_card_counter_icon"));
        public static final PacketCodec<RegistryByteBuf, SetCardCounterIconC2S> CODEC = PacketCodec.tuple(
                HAND_CODEC, SetCardCounterIconC2S::hand,
                PacketCodecs.STRING, SetCardCounterIconC2S::key,
                PacketCodecs.STRING, SetCardCounterIconC2S::icon,
                SetCardCounterIconC2S::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RemoveCardCounterC2S(Hand hand, String key) implements CustomPayload {
        public static final Id<RemoveCardCounterC2S> ID = new Id<>(Identifier.of("mtgcard", "remove_card_counter"));
        public static final PacketCodec<RegistryByteBuf, RemoveCardCounterC2S> CODEC = PacketCodec.tuple(
                HAND_CODEC, RemoveCardCounterC2S::hand,
                PacketCodecs.STRING, RemoveCardCounterC2S::key,
                RemoveCardCounterC2S::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(SetCardCounterC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getStackInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.setCounter(stack, payload.key(), payload.value());
                player.getInventory().markDirty();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCardCounterIconC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getStackInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.setIcon(stack, payload.key(), payload.icon());
                player.getInventory().markDirty();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveCardCounterC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var stack = player.getStackInHand(payload.hand());
                if (stack.isEmpty()) return;

                CardCounterNbt.removeCounter(stack, payload.key());
                player.getInventory().markDirty();
            });
        });
    }

    private CardCounterPackets() {}
}
