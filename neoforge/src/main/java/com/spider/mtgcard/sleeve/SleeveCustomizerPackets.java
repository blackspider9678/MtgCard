package com.spider.mtgcard.sleeve;

import com.spider.mtgcard.registry.ModRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.LoomMenu;

public final class SleeveCustomizerPackets {
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> ID = new Type<>(ModRegistry.id("open_sleeve_customizer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> {}, b -> new Open());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

 public static void open(ServerPlayer player) {
  if(player.containerMenu instanceof LoomMenu loom && loom.stillValid(player))
   player.openMenu(new SimpleMenuProvider((id,inv,p)->new SleeveCustomizerMenu(id,inv),Component.literal("Card Sleeves")));
 }
}
