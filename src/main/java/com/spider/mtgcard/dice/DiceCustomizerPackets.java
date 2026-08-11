package com.spider.mtgcard.dice;

import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.LoomMenu;

public final class DiceCustomizerPackets {
    private static boolean typesRegistered;
    private static boolean serverRegistered;

    public record OpenDiceCustomizerC2S() implements CustomPacketPayload {
        public static final Type<OpenDiceCustomizerC2S> ID = new Type<>(ModRegistry.id("open_dice_customizer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDiceCustomizerC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {},
                buf -> new OpenDiceCustomizerC2S()
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CraftDiceC2S(int containerId, int sides, DiceAppearance appearance) implements CustomPacketPayload {
        public static final Type<CraftDiceC2S> ID = new Type<>(ModRegistry.id("craft_custom_dice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CraftDiceC2S> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.containerId());
                    buf.writeVarInt(payload.sides());
                    DiceAppearance.STREAM_CODEC.encode(buf, payload.appearance());
                },
                buf -> new CraftDiceC2S(
                        buf.readVarInt(),
                        buf.readVarInt(),
                        DiceAppearance.STREAM_CODEC.decode(buf)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public static void registerTypes() {
        if (typesRegistered) return;
        typesRegistered = true;
        PayloadTypeRegistry.playC2S().register(OpenDiceCustomizerC2S.ID, OpenDiceCustomizerC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CraftDiceC2S.ID, CraftDiceC2S.CODEC);
    }

    public static void registerServer() {
        if (serverRegistered) return;
        serverRegistered = true;

        ServerPlayNetworking.registerGlobalReceiver(OpenDiceCustomizerC2S.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.containerMenu instanceof LoomMenu loom
                        && loom.stillValid(player)
                        && DiceCustomizerScreenHandler.playerNearLoom(player)) {
                    player.openMenu(new SimpleMenuProvider(
                            (syncId, inventory, p) -> new DiceCustomizerScreenHandler(syncId, inventory),
                            Component.translatable("screen.mtgcard.dice_customizer")
                    ));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CraftDiceC2S.ID, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.containerMenu instanceof DiceCustomizerScreenHandler handler
                        && handler.containerId == payload.containerId()) {
                    handler.craft(player, payload.appearance(), payload.sides());
                }
            });
        });
    }

    private DiceCustomizerPackets() {}
}
