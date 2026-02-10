package com.spider.mtgcard.deckbox.net;

import net.minecraft.util.math.BlockPos;

/*package com.spider.mtgcard.deckbox.net;

import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Simple packet set for Deckbox:
 *  - UPDATE_CMD: commander name + mana cost
 *  - UPDATE_COLOR: rgb tint + CMYK floats (if you want client UI sliders later)
 *  - REQUEST_SYNC: client asks server to send both updates for a BE

public final class DeckboxPackets {

    public static final Identifier UPDATE_CMD = id("deckbox_update_cmd");
    public static final Identifier UPDATE_COLOR = id("deckbox_update_color");
    public static final Identifier REQUEST_SYNC = id("deckbox_request_sync");

    private static Identifier id(String path) {
        return new Identifier("mtg", path);
    }

    private DeckboxPackets() {}

    // ======== SERVER SIDE ========

    // Call when commander slot changes.
    public static void sendCommanderUpdate(ServerPlayerEntity to, BlockPos pos, String name, String mana) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(name == null ? "" : name);
        buf.writeString(mana == null ? "" : mana);
        ServerPlayNetworking.send(to, UPDATE_CMD, buf);
    }

    // Broadcast to all viewers tracking the chunk.
    public static void broadcastCommander(World world, BlockPos pos, String name, String mana) {
        if (world.isClient()) return;
        world.getServer().getPlayerManager().getPlayerList().forEach(p -> {
            if (p.getEntityWorld() == world && p.getChunkManager().isWatchable(pos)) {
                sendCommanderUpdate(p, pos, name, mana);
            }
        });
    }

    // Call when color changes.
    public static void sendColorUpdate(ServerPlayerEntity to, BlockPos pos, int rgb, float c, float m, float y, float k) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeInt(rgb);
        buf.writeFloat(c); buf.writeFloat(m); buf.writeFloat(y); buf.writeFloat(k);
        ServerPlayNetworking.send(to, UPDATE_COLOR, buf);
    }

    public static void broadcastColor(World world, BlockPos pos, int rgb, float c, float m, float y, float k) {
        if (world.isClient()) return;
        world.getServer().getPlayerManager().getPlayerList().forEach(p -> {
            if (p.getEntityWorld() == world && p.getChunkManager().isWatchable(pos)) {
                sendColorUpdate(p, pos, rgb, c, m, y, k);
            }
        });
    }

    // Server handler: when a client requests a sync, respond with both packets.
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_SYNC, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                BlockEntity be = player.getWorld().getBlockEntity(pos);
                if (be instanceof DeckboxBlockEntity d) {
                    sendCommanderUpdate(player, pos, d.getCommanderName(), d.getCommanderManaCost());
                    float[] cmyk = d.getCMYK();
                    sendColorUpdate(player, pos, d.getRgbTint(), cmyk[0], cmyk[1], cmyk[2], cmyk[3]);
                }
            });
        });
    }

    // ======== CLIENT SIDE ========

    @Environment(EnvType.CLIENT)
    public static void requestSync(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(REQUEST_SYNC, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(UPDATE_CMD, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String name = buf.readString();
            String mana = buf.readString();
            client.execute(() -> {
                World w = client.world;
                if (w == null) return;
                BlockEntity be = w.getBlockEntity(pos);
                if (be instanceof DeckboxBlockEntity d) {
                    // Update local cache and re-render open screens/BER
                    try {
                        // Reflective calls are avoided; if you expose setters, use them:
                        d.getClass().getMethod("markDirtyAndSync").invoke(d); // harmless if exists
                    } catch (Throwable ignored) {}
                    // If your BE exposes direct fields, better: d.setClientCommanderCache(name, mana);
                    // For now, rely on screens reading packets directly (below).
                }
                // If the deckbox screen is open, push data to it:
                if (client.currentScreen instanceof com.spider.mtgcard.deckbox.DeckboxScreen screen) {
                    screen.setCommanderData(name, mana); // add a setter on your screen; see note below
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(UPDATE_COLOR, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int rgb = buf.readInt();
            float c = buf.readFloat(), m = buf.readFloat(), y = buf.readFloat(), k = buf.readFloat();
            client.execute(() -> {
                World w = client.world;
                if (w == null) return;
                BlockEntity be = w.getBlockEntity(pos);
                if (be instanceof DeckboxBlockEntity d) {
                    // If you expose a client setter, call it; else the BER tint will re-query when chunk rerenders.
                    w.updateListeners(pos, d.getCachedState(), d.getCachedState(), 3);
                }
            });
        });
    }
}
*/

