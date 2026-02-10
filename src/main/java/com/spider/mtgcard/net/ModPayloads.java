// src/main/java/com/spider/mtgcard/net/ModPayloads.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.net.payload.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Single source of truth for payload CODEC registration + server receivers.
 *
 * IMPORTANT:
 * - This expects SetFacePayload to be SLOT-based: (slot, face) not (hand, face).
 * - After adding this, REMOVE ModNetworking.initCommon() from your onInitialize() and
 *   remove any duplicate receiver registrations elsewhere.
 */
public final class ModPayloads {
    private static boolean typesRegistered = false;
    private static boolean serverReceiversRegistered = false;

    /** CODEC/type registration only (safe to call on both sides). */
    public static void registerTypes() {
        if (typesRegistered) return;
        typesRegistered = true;

        Mtgcard.LOGGER.info("[ModPayloads] registerTypes() starting...");

        // ---- Art streaming (chunked) ----
        PayloadTypeRegistry.playC2S().register(ArtPackets.ArtRequest.ID, ArtPackets.ArtRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(ArtPackets.ArtChunk.ID, ArtPackets.ArtChunk.CODEC);

        // ---- Custom import GUI (server -> client) ----
        PayloadTypeRegistry.playS2C().register(CustomImportPackets.OpenImportGui.ID, CustomImportPackets.OpenImportGui.CODEC);

        // ---- Custom cards ----
        CustomCardPackets.registerTypes();

        // ---- Counters / tabs ----
        PayloadTypeRegistry.playC2S().register(SetCounterValuePayload.ID, SetCounterValuePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCounterMetaPayload.ID, SetCounterMetaPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DeleteCounterPayload.ID, DeleteCounterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DeckboxTabNamesPayload.ID, DeckboxTabNamesPayload.CODEC);

        // ---- Deck Control types ONLY (no receivers here) ----
        com.spider.mtgcard.deckcontrol.DeckControlPackets.registerTypes();

        // ---- Card Store types ----
        CardStorePackets.registerTypes();

        // ---- Hidden flags / display payloads ----
        PayloadTypeRegistry.playC2S().register(SetHiddenPayload.ID, SetHiddenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDisplayPayloads.DisplaySetHiddenC2S.ID, CardDisplayPayloads.DisplaySetHiddenC2S.CODEC);

        PayloadTypeRegistry.playS2C().register(CardDisplayPayloads.OpenDisplayViewS2C.ID, CardDisplayPayloads.OpenDisplayViewS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDisplayPayloads.DisplaySetFaceC2S.ID, CardDisplayPayloads.DisplaySetFaceC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDisplayPayloads.DisplaySetCounterValueC2S.ID, CardDisplayPayloads.DisplaySetCounterValueC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDisplayPayloads.DisplaySetCounterMetaC2S.ID, CardDisplayPayloads.DisplaySetCounterMetaC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(CardDisplayPayloads.DisplayDeleteCounterC2S.ID, CardDisplayPayloads.DisplayDeleteCounterC2S.CODEC);

        // ---- From old ModNetworking (moved here) ----
        // NOTE: SetFacePayload is expected to be SLOT-based: (slot, face)
        PayloadTypeRegistry.playC2S().register(SetFacePayload.ID, SetFacePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(XmlArtUploadPayload.ID, XmlArtUploadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UnbundleProgressPayload.ID, UnbundleProgressPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GraveyardActionPayload.ID, GraveyardActionPayload.CODEC);

        // ---- Deck export/list (server -> client) ----
        PayloadTypeRegistry.playS2C().register(DeckPayloads.DeckExportRequestS2C.ID, DeckPayloads.DeckExportRequestS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(DeckPayloads.DeckListRequestS2C.ID, DeckPayloads.DeckListRequestS2C.CODEC);

        Mtgcard.LOGGER.info("[ModPayloads] registerTypes() DONE");
    }

    /** Server-only global receivers + connection hooks. Call ONLY from ModInitializer. */
    public static void registerServerReceivers() {
        registerTypes();

        if (serverReceiversRegistered) return;
        serverReceiversRegistered = true;

        Mtgcard.LOGGER.info("[ModPayloads] registerServerReceivers() starting...");

        // Deck Control receivers (SERVER side)
        com.spider.mtgcard.deckcontrol.DeckControlPackets.registerReceivers();

        // Card Store receivers (SERVER side)
        CardStorePackets.registerServer();

        // -------------------------
        // Stack-based mutations
        // -------------------------

        // Hide/show stack in inventory slot
        ServerPlayNetworking.registerGlobalReceiver(SetHiddenPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                int slot = payload.slot();
                boolean hidden = payload.hidden();

                if (slot < 0 || slot >= player.getInventory().size()) return;

                ItemStack st = player.getInventory().getStack(slot);
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.writeHidden(st, hidden);

                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
            });
        });

        // Hide/show display entity stack
        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetHiddenC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.getEntityWorld();

                var ent = world.getEntityById(payload.entityId());
                if (!(ent instanceof com.spider.mtgcard.display.CardDisplayEntity display)) return;

                ItemStack st = display.getStack().copy();
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.writeHidden(st, payload.hidden());
                display.setStack(st); // tracked data syncs
            });
        });

        // ✅ FIXED: SetFacePayload is SLOT-based (slot, face) for CardLargeView handSlot
        ServerPlayNetworking.registerGlobalReceiver(SetFacePayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                int slot = payload.slot();
                int face = payload.face();

                if (slot < 0 || slot >= player.getInventory().size()) return;

                var st = player.getInventory().getStack(slot);
                if (st.isEmpty()) return;

                // write mtg_face into CUSTOM_DATA -> mtg_meta
                var comp = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
                net.minecraft.nbt.NbtCompound root = (comp == null)
                        ? new net.minecraft.nbt.NbtCompound()
                        : comp.copyNbt();

                net.minecraft.nbt.NbtCompound meta = root.getCompound("mtg_meta")
                        .orElseGet(net.minecraft.nbt.NbtCompound::new);

                meta.putInt("mtg_face", Math.max(0, face));
                root.put("mtg_meta", meta);

                st.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                        net.minecraft.component.type.NbtComponent.of(root));

                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteCounterPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                int slot = payload.slot();
                String key = payload.key();

                if (slot < 0 || slot >= player.getInventory().size()) return;

                ItemStack st = player.getInventory().getStack(slot);
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.deleteCounterKey(st, key);

                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplayDeleteCounterC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.getEntityWorld();

                var ent = world.getEntityById(payload.entityId());
                if (!(ent instanceof com.spider.mtgcard.display.CardDisplayEntity display)) return;

                ItemStack st = display.getStack().copy();
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.deleteCounterKey(st, payload.key());
                display.setStack(st); // tracked data sync
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteCounterPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                int slot = payload.slot();
                String key = payload.key();

                if (key == null || key.isBlank()) return;
                if (slot < 0 || slot >= player.getInventory().size()) return;

                ItemStack st = player.getInventory().getStack(slot);
                if (st.isEmpty()) return;

                // remove from mtg_meta
                com.spider.mtgcard.util.StackData.deleteCounterKey(st, key);

                player.getInventory().markDirty();
                player.currentScreenHandler.sendContentUpdates();
            });
        });

        // -------------------------
        // Graveyard actions
        // -------------------------
        ServerPlayNetworking.registerGlobalReceiver(GraveyardActionPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();

                // Must have the graveyard screen open and match sync/pos
                if (!(player.currentScreenHandler instanceof com.spider.mtgcard.graveyard.GraveyardScreenHandler sh)) return;
                if (sh.syncId != payload.syncId()) return;
                if (!sh.pos.equals(payload.pos())) return;

                var world = player.getEntityWorld();
                var be = world.getBlockEntity(payload.pos());
                if (!(be instanceof GraveyardBlockEntity gy)) return;

                switch (payload.action()) {
                    case EXILE_ALL -> gy.exileAll();
                    case RETURN_ALL -> gy.returnAll();
                }
            });
        });

        // -------------------------
        // XML art upload save-to-world
        // -------------------------
        ServerPlayNetworking.registerGlobalReceiver(XmlArtUploadPayload.ID, (payload, ctx) -> {
            var server = ctx.server();
            var player = ctx.player();

            byte[] png = payload.imgBytes();
            String fileName = payload.fileName();
            String sourceUrl = payload.sourceUrl();

            server.execute(() -> {
                try {
                    String safe = (fileName == null ? "card.png" : fileName.trim());
                    if (safe.isEmpty()) safe = "card.png";
                    safe = safe.replaceAll("[^a-zA-Z0-9._-]+", "_");
                    if (!safe.toLowerCase(Locale.ROOT).endsWith(".png")) safe += ".png";

                    // If sourceUrl present, stabilize collisions with hash suffix
                    if (sourceUrl != null && !sourceUrl.isEmpty()) {
                        String hex = Integer.toHexString(sourceUrl.hashCode());
                        int dot = safe.lastIndexOf('.');
                        String stem = (dot > 0) ? safe.substring(0, dot) : safe;
                        String ext  = (dot > 0) ? safe.substring(dot) : ".png";
                        safe = stem + "_" + hex + ext;
                    }

                    Path worldRoot = resolveWorldRoot(server);
                    Path artDir = worldRoot.resolve("mtgcard").resolve("art");
                    Files.createDirectories(artDir);

                    Path out = artDir.resolve(safe);
                    int counter = 1;
                    while (Files.exists(out)) {
                        int dot = safe.lastIndexOf('.');
                        String stem = (dot > 0) ? safe.substring(0, dot) : safe;
                        String ext  = (dot > 0) ? safe.substring(dot) : ".png";
                        out = artDir.resolve(stem + "_" + counter++ + ext);
                    }

                    Files.write(out, png);
                    player.sendMessage(Text.literal("[MTGCard] Saved XML art to world: " + out.getFileName()), false);
                } catch (Throwable t) {
                    player.sendMessage(Text.literal("[MTGCard] Failed to save XML art: " + t.getClass().getSimpleName()), false);
                }
            });
        });

        // -------------------------
        // Disconnect hook: cancel pending deck-control cascades for player
        // -------------------------
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var uuid = player.getUuid();

            server.execute(() -> {
                var positions = com.spider.mtgcard.deckcontrol.DeckControlBlockEntity.getPendingPositions(uuid);
                if (positions.isEmpty()) return;

                for (var world : server.getWorlds()) {
                    for (var p : positions) {
                        var be = world.getBlockEntity(p);
                        if (be instanceof com.spider.mtgcard.deckcontrol.DeckControlBlockEntity dc) {
                            dc.cancelPendingCascade(player);
                        }
                    }
                }
            });
        });

        Mtgcard.LOGGER.info("[ModPayloads] registerServerReceivers() DONE");
    }

    /** Server helper: send unbundle progress to a player (0..100). Thread-safe. */
    public static void sendUnpackProgress(ServerPlayerEntity player, int percent) {
        if (player == null) return;

        int p = Math.max(0, Math.min(100, percent));
        var server = player.getEntityWorld().getServer();
        if (server == null) return;

        Runnable send = () -> {
            if (player.networkHandler == null) return;
            ServerPlayNetworking.send(player, new UnbundleProgressPayload(p));
        };

        if (server.isOnThread()) send.run();
        else server.execute(send);
    }

    // Portable world root that works for both dedicated and dev client
    private static Path resolveWorldRoot(MinecraftServer server) {
        String levelName = server.getSaveProperties().getLevelName();
        Path run = server.getRunDirectory();

        Path dedicatedStyle = run.resolve(levelName);
        Path clientStyle    = run.resolve("saves").resolve(levelName);

        return Files.exists(dedicatedStyle) ? dedicatedStyle : clientStyle;
    }

    private ModPayloads() {}
}
