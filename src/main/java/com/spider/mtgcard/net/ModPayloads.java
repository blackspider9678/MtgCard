// src/main/java/com/spider/mtgcard/net/ModPayloads.java
package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.graveyard.GraveyardBlockEntity;
import com.spider.mtgcard.net.payload.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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

        PayloadTypeRegistry.playC2S().register(FlipHeldCardFacePayload.ID, FlipHeldCardFacePayload.CODEC);

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

                if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

                ItemStack st = player.getInventory().getItem(slot);
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.writeHidden(st, hidden);

                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            });
        });

        // Hide/show display entity stack
        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetHiddenC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.level();

                var ent = world.getEntity(payload.entityId());
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

                if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

                var st = player.getInventory().getItem(slot);
                if (st.isEmpty()) return;

                // write mtg_face into CUSTOM_DATA -> mtg_meta
                var comp = st.get(DataComponents.CUSTOM_DATA);
                CompoundTag root = (comp == null)
                        ? new CompoundTag()
                        : comp.copyTag();

                CompoundTag meta = root.getCompound("mtg_meta")
                        .orElseGet(CompoundTag::new);

                meta.putInt("mtg_face", Math.max(0, face));
                root.put("mtg_meta", meta);

                st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeleteCounterPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                int slot = payload.slot();
                String key = payload.key();

                if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

                ItemStack st = player.getInventory().getItem(slot);
                if (st.isEmpty()) return;

                com.spider.mtgcard.util.StackData.deleteCounterKey(st, key);

                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplayDeleteCounterC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();
                var world = player.level();

                var ent = world.getEntity(payload.entityId());
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
                if (slot < 0 || slot >= player.getInventory().getContainerSize()) return;

                ItemStack st = player.getInventory().getItem(slot);
                if (st.isEmpty()) return;

                // remove from mtg_meta
                com.spider.mtgcard.util.StackData.deleteCounterKey(st, key);

                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(FlipHeldCardFacePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var player = context.player();
                if (player == null) return;

                var stack = player.getItemInHand(payload.hand());
                if (stack == null || stack.isEmpty()) return;

                // Make sure it's your card item
                if (!(stack.getItem() instanceof com.spider.mtgcard.item.CardItem)) return;

                // Make sure it's actually double-faced
                if (!isDoubleFaced(stack)) return;

                int faceCount = getFaceCount(stack);
                if (faceCount <= 1) return;

                int cur = readFaceIndex(stack);
                int next = (cur + 1) % faceCount;

                writeFaceIndex(stack, next);

                // force inventory sync (usually not necessary, but helps in edge cases)
                player.containerMenu.broadcastChanges();
            });
        });

        // -------------------------
        // Graveyard actions
        // -------------------------
        ServerPlayNetworking.registerGlobalReceiver(GraveyardActionPayload.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                var player = ctx.player();

                // Must have the graveyard screen open and match sync/pos
                if (!(player.containerMenu instanceof com.spider.mtgcard.graveyard.GraveyardScreenHandler sh)) return;
                if (sh.containerId != payload.syncId()) return;
                if (!sh.pos.equals(payload.pos())) return;

                var world = player.level();
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
                    player.sendSystemMessage(Component.literal("[MTGCard] Saved XML art to world: " + out.getFileName()));
                } catch (Throwable t) {
                    player.sendSystemMessage(Component.literal("[MTGCard] Failed to save XML art: " + t.getClass().getSimpleName()));
                }
            });
        });

        // -------------------------
        // Disconnect hook: cancel pending deck-control cascades for player
        // -------------------------
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var uuid = player.getUUID();

            server.execute(() -> {
                var positions = com.spider.mtgcard.deckcontrol.DeckControlBlockEntity.getPendingPositions(uuid);
                if (positions.isEmpty()) return;

                for (var world : server.getAllLevels()) {
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
    public static void sendUnpackProgress(ServerPlayer player, int percent) {
        if (player == null) return;

        int p = Math.max(0, Math.min(100, percent));
        var server = player.level().getServer();
        if (server == null) return;

        Runnable send = () -> {
            if (player.connection == null) return;
            ServerPlayNetworking.send(player, new UnbundleProgressPayload(p));
        };

        if (server.isSameThread()) send.run();
        else server.execute(send);
    }

    // Portable world root that works for both dedicated and dev client
    private static Path resolveWorldRoot(MinecraftServer server) {
        String levelName = server.getWorldData().getLevelName();
        Path run = server.getServerDirectory();

        Path dedicatedStyle = run.resolve(levelName);
        Path clientStyle    = run.resolve("saves").resolve(levelName);

        return Files.exists(dedicatedStyle) ? dedicatedStyle : clientStyle;
    }

    private static net.minecraft.nbt.CompoundTag getMeta(net.minecraft.world.item.ItemStack st) {
        var comp = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);
    }

    private static boolean isDoubleFaced(net.minecraft.world.item.ItemStack st) {
        var meta = getMeta(st);
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.ListTag list && list.size() >= 2;
    }

    private static int getFaceCount(net.minecraft.world.item.ItemStack st) {
        var meta = getMeta(st);
        var el = meta.get("card_faces");
        if (el instanceof net.minecraft.nbt.ListTag list) return Math.max(1, list.size());
        return 1;
    }

    private static int readFaceIndex(net.minecraft.world.item.ItemStack st) {
        var meta = getMeta(st);
        return meta.getInt("mtg_face").orElse(0);
    }

    private static void writeFaceIndex(net.minecraft.world.item.ItemStack st, int idx) {
        var comp = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        net.minecraft.nbt.CompoundTag meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);

        meta.putInt("mtg_face", idx);
        root.put("mtg_meta", meta);

        st.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
    }

    private ModPayloads() {}
}
