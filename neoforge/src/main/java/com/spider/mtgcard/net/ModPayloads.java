package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.content.pack.PackProgressBars;
import com.spider.mtgcard.db.CardDatabaseScreenHandler;
import com.spider.mtgcard.deckcontrol.DeckControlPackets;
import com.spider.mtgcard.life.LifePointPackets;
import com.spider.mtgcard.net.payload.XmlArtUploadPayload;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import com.spider.mtgcard.net.payload.DeckPayloads;
import com.spider.mtgcard.net.payload.DeckboxTabNamesPayload;
import com.spider.mtgcard.net.payload.GraveyardActionPayload;
import com.spider.mtgcard.net.payload.SearchPayload;
import com.spider.mtgcard.net.payload.UnbundleProgressPayload;
import com.spider.mtgcard.net.payload.DeleteCounterPayload;
import com.spider.mtgcard.net.payload.FlipHeldCardFacePayload;
import com.spider.mtgcard.net.payload.SetCounterMetaPayload;
import com.spider.mtgcard.net.payload.SetCounterValuePayload;
import com.spider.mtgcard.net.payload.SetFacePayload;
import com.spider.mtgcard.net.payload.SetHiddenPayload;
import com.spider.mtgcard.util.ArtImageStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class ModPayloads {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Mtgcard.MOD_ID).versioned("1");
        registrar.playToServer(com.spider.mtgcard.dice.DiceCustomizerPackets.OpenDiceCustomizerC2S.ID, com.spider.mtgcard.dice.DiceCustomizerPackets.OpenDiceCustomizerC2S.CODEC,
            (payload, context) -> context.enqueueWork(() -> com.spider.mtgcard.dice.DiceCustomizerPackets.open((ServerPlayer)context.player())));
        registrar.playToServer(com.spider.mtgcard.dice.DiceCustomizerPackets.CraftDiceC2S.ID, com.spider.mtgcard.dice.DiceCustomizerPackets.CraftDiceC2S.CODEC,
            (payload, context) -> context.enqueueWork(() -> com.spider.mtgcard.dice.DiceCustomizerPackets.craft((ServerPlayer)context.player(), payload)));
        registrar.playToServer(com.spider.mtgcard.sleeve.SleeveCustomizerPackets.Open.ID, com.spider.mtgcard.sleeve.SleeveCustomizerPackets.Open.CODEC,
            (payload, context) -> context.enqueueWork(() -> com.spider.mtgcard.sleeve.SleeveCustomizerPackets.open((ServerPlayer)context.player())));

        registrar.playToClient(UnbundleProgressPayload.ID, UnbundleProgressPayload.CODEC,
                (payload, context) -> handleClient("unbundle_progress", payload, context));
        registrar.playToClient(ArtPackets.ArtChunk.ID, ArtPackets.ArtChunk.CODEC,
                (payload, context) -> handleClient("art_chunk", payload, context));
        registrar.playToClient(GuideBookPackets.OpenPayload.ID, GuideBookPackets.OpenPayload.CODEC,
                (payload, context) -> handleClient("guide_open", payload, context));
        registrar.playToClient(CustomImportPackets.OpenImportGui.ID, CustomImportPackets.OpenImportGui.CODEC,
                (payload, context) -> handleClient("custom_import_open", payload, context));
        registrar.playToClient(LifePointPackets.OpenLifeScreenPayload.ID, LifePointPackets.OpenLifeScreenPayload.CODEC,
                (payload, context) -> handleClient("open_life", payload, context));
        registrar.playToClient(LifePointPackets.SyncLifePayload.ID, LifePointPackets.SyncLifePayload.CODEC,
                (payload, context) -> handleClient("sync_life", payload, context));
        registrar.playToClient(LifePointPackets.YourPresetIdS2C.ID, LifePointPackets.YourPresetIdS2C.CODEC,
                (payload, context) -> handleClient("life_your_preset_id", payload, context));
        registrar.playToClient(LifePointPackets.NearbyResultS2C.ID, LifePointPackets.NearbyResultS2C.CODEC,
                (payload, context) -> handleClient("life_nearby_result", payload, context));
        registrar.playToClient(LifePointPackets.GroupsListS2C.ID, LifePointPackets.GroupsListS2C.CODEC,
                (payload, context) -> handleClient("life_groups_list", payload, context));
        registrar.playToClient(LifePointPackets.GroupSnapshotS2C.ID, LifePointPackets.GroupSnapshotS2C.CODEC,
                (payload, context) -> handleClient("life_group_snapshot", payload, context));
        registrar.playToClient(LifePointPackets.GroupRemovedS2C.ID, LifePointPackets.GroupRemovedS2C.CODEC,
                (payload, context) -> handleClient("life_group_removed", payload, context));
        registrar.playToClient(DeckControlPackets.OverlayS2C.ID, DeckControlPackets.OverlayS2C.CODEC,
                (payload, context) -> handleClient("deck_control_overlay", payload, context));
        registrar.playToClient(DeckControlPackets.CascadeS2C.ID, DeckControlPackets.CascadeS2C.CODEC,
                (payload, context) -> handleClient("deck_control_cascade", payload, context));
        registrar.playToClient(CardDisplayPayloads.OpenDisplayViewS2C.ID, CardDisplayPayloads.OpenDisplayViewS2C.CODEC,
                (payload, context) -> handleClient("card_display_open", payload, context));
        registrar.playToClient(DeckboxTabNamesPayload.ID, DeckboxTabNamesPayload.CODEC,
                (payload, context) -> handleClient("deckbox_tab_names", payload, context));
        registrar.playToClient(DeckPayloads.DeckExportRequestS2C.ID, DeckPayloads.DeckExportRequestS2C.CODEC,
                (payload, context) -> handleClient("deck_export_request", payload, context));
        registrar.playToClient(DeckPayloads.DeckListRequestS2C.ID, DeckPayloads.DeckListRequestS2C.CODEC,
                (payload, context) -> handleClient("deck_list_request", payload, context));
        registrar.playToClient(CardStorePackets.SearchS2C.ID, CardStorePackets.SearchS2C.CODEC,
                (payload, context) -> handleClient("card_store_search", payload, context));
        registrar.playToClient(CardStorePackets.SearchPrintsS2C.ID, CardStorePackets.SearchPrintsS2C.CODEC,
                (payload, context) -> handleClient("card_store_prints", payload, context));
        registrar.playToClient(CardStorePackets.SearchPrintsStartS2C.ID, CardStorePackets.SearchPrintsStartS2C.CODEC,
                (payload, context) -> handleClient("card_store_prints_start", payload, context));
        registrar.playToClient(CardStorePackets.SearchPrintsAddS2C.ID, CardStorePackets.SearchPrintsAddS2C.CODEC,
                (payload, context) -> handleClient("card_store_prints_add", payload, context));
        registrar.playToClient(CardStorePackets.SearchPrintsDoneS2C.ID, CardStorePackets.SearchPrintsDoneS2C.CODEC,
                (payload, context) -> handleClient("card_store_prints_done", payload, context));
        registrar.playToClient(CardStorePackets.ImportDeckS2C.ID, CardStorePackets.ImportDeckS2C.CODEC,
                (payload, context) -> handleClient("card_store_import_deck", payload, context));
        registrar.playToClient(CustomCardPackets.CustomSyncFull.ID, CustomCardPackets.CustomSyncFull.CODEC,
                (payload, context) -> handleClient("custom_sync_full", payload, context));
        registrar.playToClient(CustomCardPackets.CustomSyncDelta.ID, CustomCardPackets.CustomSyncDelta.CODEC,
                (payload, context) -> handleClient("custom_sync_delta", payload, context));
        registrar.playToClient(CustomCardPackets.CustomArtReady.ID, CustomCardPackets.CustomArtReady.CODEC,
                (payload, context) -> handleClient("custom_art_ready", payload, context));
        registrar.playToServer(SearchPayload.ID, SearchPayload.CODEC, ModPayloads::handleDbSearch);
        registrar.playToServer(ArtPackets.ArtRequest.ID, ArtPackets.ArtRequest.CODEC, ModPayloads::handleArtRequest);
        registrar.playToServer(GraveyardActionPayload.ID, GraveyardActionPayload.CODEC, ModPayloads::handleGraveyardAction);
        registrar.playToServer(XmlArtUploadPayload.ID, XmlArtUploadPayload.CODEC, ModPayloads::handleXmlArtUpload);
        registrar.playToServer(SetHiddenPayload.ID, SetHiddenPayload.CODEC, ModPayloads::handleSetHidden);
        registrar.playToServer(CardDisplayPayloads.DisplaySetHiddenC2S.ID, CardDisplayPayloads.DisplaySetHiddenC2S.CODEC,
                ModPayloads::handleDisplaySetHidden);
        registrar.playToServer(SetFacePayload.ID, SetFacePayload.CODEC, ModPayloads::handleSetFace);
        registrar.playToServer(FlipHeldCardFacePayload.ID, FlipHeldCardFacePayload.CODEC,
                ModPayloads::handleFlipHeldCardFace);
        registrar.playToServer(DeleteCounterPayload.ID, DeleteCounterPayload.CODEC, ModPayloads::handleDeleteCounter);
        registrar.playToServer(SetCounterValuePayload.ID, SetCounterValuePayload.CODEC,
                ModPayloads::handleSetCounterValue);
        registrar.playToServer(SetCounterMetaPayload.ID, SetCounterMetaPayload.CODEC,
                ModPayloads::handleSetCounterMeta);
        registrar.playToServer(CardDisplayPayloads.DisplaySetFaceC2S.ID, CardDisplayPayloads.DisplaySetFaceC2S.CODEC,
                ModPayloads::handleDisplaySetFace);
        registrar.playToServer(CardDisplayPayloads.DisplaySetCounterValueC2S.ID,
                CardDisplayPayloads.DisplaySetCounterValueC2S.CODEC, ModPayloads::handleDisplaySetCounterValue);
        registrar.playToServer(CardDisplayPayloads.DisplaySetCounterMetaC2S.ID,
                CardDisplayPayloads.DisplaySetCounterMetaC2S.CODEC, ModPayloads::handleDisplaySetCounterMeta);
        registrar.playToServer(CardDisplayPayloads.DisplayDeleteCounterC2S.ID,
                CardDisplayPayloads.DisplayDeleteCounterC2S.CODEC, ModPayloads::handleDisplayDeleteCounter);
        registrar.playToServer(CustomCardPackets.CustomBatchCreate.ID, CustomCardPackets.CustomBatchCreate.CODEC,
                ModPayloads::handleCustomBatchCreate);
        registrar.playToServer(CustomCardPackets.CustomArtBegin.ID, CustomCardPackets.CustomArtBegin.CODEC,
                ModPayloads::handleCustomArtBegin);
        registrar.playToServer(CustomCardPackets.CustomArtChunk.ID, CustomCardPackets.CustomArtChunk.CODEC,
                ModPayloads::handleCustomArtChunk);
        registrar.playToServer(CustomCardPackets.CustomArtFinish.ID, CustomCardPackets.CustomArtFinish.CODEC,
                ModPayloads::handleCustomArtFinish);
        registrar.playToServer(DeckControlPackets.ActionC2S.ID, DeckControlPackets.ActionC2S.CODEC,
                DeckControlPackets::handleAction);
        registrar.playToServer(DeckControlPackets.CascadeStartC2S.ID, DeckControlPackets.CascadeStartC2S.CODEC,
                DeckControlPackets::handleCascadeStart);
        registrar.playToServer(DeckControlPackets.CascadeResolveC2S.ID, DeckControlPackets.CascadeResolveC2S.CODEC,
                DeckControlPackets::handleCascadeResolve);
        registrar.playToServer(DeckControlPackets.ResolveOrderedC2S.ID, DeckControlPackets.ResolveOrderedC2S.CODEC,
                DeckControlPackets::handleResolveOrdered);
        registrar.playToServer(CardStorePackets.ConfirmPurchaseC2S.ID, CardStorePackets.ConfirmPurchaseC2S.CODEC,
                CardStorePackets::handleConfirm);
        registrar.playToServer(CardStorePackets.SetCartC2S.ID, CardStorePackets.SetCartC2S.CODEC,
                CardStorePackets::handleSetCart);
        registrar.playToServer(CardStorePackets.SearchC2S.ID, CardStorePackets.SearchC2S.CODEC,
                CardStorePackets::handleSearch);
        registrar.playToServer(CardStorePackets.SearchPrintsC2S.ID, CardStorePackets.SearchPrintsC2S.CODEC,
                CardStorePackets::handleSearchPrints);
        registrar.playToServer(CardStorePackets.ImportDeckC2S.ID, CardStorePackets.ImportDeckC2S.CODEC,
                CardStorePackets::handleImportDeck);
        LifePointPackets.registerNeoForge(registrar);
    }

    public static void sendUnpackProgress(ServerPlayer player, int percent) {
        PackProgressBars.update(player, percent);
    }

    public static void clearUnpackProgress(ServerPlayer player) {
        PackProgressBars.clear(player);
    }

    public static void tickUnpackProgressBars(net.minecraft.server.MinecraftServer server) {
        PackProgressBars.tick(server);
    }

    private static void handleClient(String action, Object payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }

            try {
                Class<?> handlers = Class.forName("com.spider.mtgcard.client.net.NeoForgeClientPayloadHandlers");
                handlers.getMethod("handle", String.class, Object.class).invoke(null, action, payload);
            } catch (Throwable throwable) {
                Mtgcard.LOGGER.warn("[MtgcardNeoForge] Failed to handle client payload {}", action, throwable);
            }
        });
    }

    private static void handleDbSearch(SearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().containerMenu instanceof CardDatabaseScreenHandler handler)) {
                return;
            }

            String query = payload.q() == null ? "" : payload.q();
            if (payload.rememberSort()) {
                handler.rememberSortPreference(payload.order(), payload.dir(), true);
            }
            handler.setActiveQuery(query);
            handler.applySearch(query, payload.order(), payload.dir());
        });
    }

    private static void handleArtRequest(ArtPackets.ArtRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.level().getServer() != null) {
                ArtServerPackets.handleRequest(payload, player, player.level().getServer());
            }
        });
    }

    private static void handleGraveyardAction(GraveyardActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu == null || player.containerMenu.containerId != payload.syncId()) {
                return;
            }
            var be = player.level().getBlockEntity(payload.pos());
            if (!(be instanceof com.spider.mtgcard.graveyard.GraveyardBlockEntity graveyard)) {
                return;
            }
            if (!graveyard.getBlockPos().closerThan(player.blockPosition(), 8.0)) {
                return;
            }

            switch (payload.action()) {
                case EXILE_ALL -> graveyard.exileAll();
                case RETURN_ALL -> graveyard.returnAll();
            }
        });
    }

    private static void handleSetHidden(SetHiddenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = inventoryStack(player, payload.slot());
            if (stack.isEmpty()) return;

            com.spider.mtgcard.util.StackData.writeHidden(stack, payload.hidden());
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleDisplaySetHidden(CardDisplayPayloads.DisplaySetHiddenC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            mutateDisplayStack(player, payload.entityId(), stack ->
                    com.spider.mtgcard.util.StackData.writeHidden(stack, payload.hidden()));
        });
    }

    private static void handleSetFace(SetFacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = inventoryStack(player, payload.slot());
            if (stack.isEmpty()) return;

            writeFaceIndex(stack, Math.max(0, payload.face()));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleFlipHeldCardFace(FlipHeldCardFacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getItemInHand(payload.hand());
            if (stack == null || stack.isEmpty()) return;
            if (!(stack.getItem() instanceof com.spider.mtgcard.item.CardItem)) return;
            if (!isDoubleFaced(stack)) return;

            int faceCount = getFaceCount(stack);
            if (faceCount <= 1) return;

            int next = (readFaceIndex(stack) + 1) % faceCount;
            writeFaceIndex(stack, next);
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleDeleteCounter(DeleteCounterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = inventoryStack(player, payload.slot());
            if (stack.isEmpty()) return;

            com.spider.mtgcard.util.StackData.deleteCounterKey(stack, payload.key());
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleSetCounterValue(SetCounterValuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = inventoryStack(player, payload.hand());
            if (stack.isEmpty()) return;

            applyCounterValue(stack, payload.key(), payload.value());
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleSetCounterMeta(SetCounterMetaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = inventoryStack(player, payload.hand());
            if (stack.isEmpty()) return;

            applyCounterMeta(stack, payload.key(), payload.displayName(), payload.iconKey());
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void handleDisplaySetFace(CardDisplayPayloads.DisplaySetFaceC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                mutateDisplayStack(player, payload.entityId(), stack -> writeFaceIndex(stack, Math.max(0, payload.face())));
            }
        });
    }

    private static void handleDisplaySetCounterValue(CardDisplayPayloads.DisplaySetCounterValueC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                mutateDisplayStack(player, payload.entityId(), stack -> applyCounterValue(stack, payload.key(), payload.value()));
            }
        });
    }

    private static void handleDisplaySetCounterMeta(CardDisplayPayloads.DisplaySetCounterMetaC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                mutateDisplayStack(player, payload.entityId(), stack ->
                        applyCounterMeta(stack, payload.key(), payload.displayName(), payload.iconKey()));
            }
        });
    }

    private static void handleDisplayDeleteCounter(CardDisplayPayloads.DisplayDeleteCounterC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                mutateDisplayStack(player, payload.entityId(), stack ->
                        com.spider.mtgcard.util.StackData.deleteCounterKey(stack, payload.key()));
            }
        });
    }

    private static void handleCustomBatchCreate(CustomCardPackets.CustomBatchCreate payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CustomCardServer.handleBatch(payload, player);
            }
        });
    }

    private static void handleCustomArtBegin(CustomCardPackets.CustomArtBegin payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CustomArtUploadServer.handleBegin(payload, player);
            }
        });
    }

    private static void handleCustomArtChunk(CustomCardPackets.CustomArtChunk payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CustomArtUploadServer.handleChunk(payload, player);
            }
        });
    }

    private static void handleCustomArtFinish(CustomCardPackets.CustomArtFinish payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.level().getServer() != null) {
                CustomArtUploadServer.handleFinish(payload, player, player.level().getServer());
            }
        });
    }

    private static void handleXmlArtUpload(XmlArtUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.level().getServer() == null) {
                return;
            }

            MinecraftServer server = player.level().getServer();
            byte[] imageBytes = payload.imgBytes();
            String fileName = payload.fileName();
            String sourceUrl = payload.sourceUrl();

            try {
                ArtImageStorage.StorageDecision decision = ArtImageStorage.normalizeForStorage(imageBytes, fileName);
                ArtImageStorage.StoredArt art = decision.art();
                if (art == null) {
                    Mtgcard.LOGGER.warn("[MTGCard] XML art {} from {} could not be stored: {}",
                            fileName, sourceUrl == null ? "" : sourceUrl, decision.note());
                    return;
                }

                if (decision.fellBackFromWebp()) {
                    Mtgcard.LOGGER.warn("[MTGCard] XML art {} from {} fell back to .{} (source .{}): {}",
                            fileName, sourceUrl == null ? "" : sourceUrl, art.ext(), decision.sourceExt(), decision.note());
                } else {
                    Mtgcard.LOGGER.info("[MTGCard] XML art {} from {} stored as .{}",
                            fileName, sourceUrl == null ? "" : sourceUrl, art.ext());
                }

                String safe = (fileName == null ? "card" : fileName.trim());
                if (safe.isEmpty()) safe = "card";
                safe = safe.replaceAll("[^a-zA-Z0-9._-]+", "_");
                safe = forceImageExt(safe, art.ext());

                if (sourceUrl != null && !sourceUrl.isEmpty()) {
                    String hex = Integer.toHexString(sourceUrl.hashCode());
                    int dot = safe.lastIndexOf('.');
                    String stem = (dot > 0) ? safe.substring(0, dot) : safe;
                    String ext = (dot > 0) ? safe.substring(dot) : "." + art.ext();
                    safe = stem + "_" + hex + ext;
                }

                Path worldRoot = resolveWorldRoot(server);
                Path artDir = com.spider.mtgcard.shared.MtgCardPaths.customArtDir(server, payload.setCode());
                Files.createDirectories(artDir);

                Path out = artDir.resolve(safe);
                int counter = 1;
                while (Files.exists(out)) {
                    int dot = safe.lastIndexOf('.');
                    String stem = (dot > 0) ? safe.substring(0, dot) : safe;
                    String ext = (dot > 0) ? safe.substring(dot) : "." + art.ext();
                    out = artDir.resolve(stem + "_" + counter++ + ext);
                }

                Files.write(out, art.bytes());
                player.sendSystemMessage(Component.literal("[MTGCard] Saved XML art to world: " + out.getFileName()));
            } catch (Throwable t) {
                player.sendSystemMessage(Component.literal("[MTGCard] Failed to save XML art: " + t.getClass().getSimpleName()));
            }
        });
    }

    private static Path resolveWorldRoot(MinecraftServer server) {
        String levelName = server.getWorldData().getLevelName();
        Path run = server.getServerDirectory();

        Path dedicatedStyle = run.resolve(levelName);
        Path clientStyle = run.resolve("saves").resolve(levelName);

        return Files.exists(dedicatedStyle) ? dedicatedStyle : clientStyle;
    }

    private static String forceImageExt(String fileName, String ext) {
        String safeExt = "." + ArtImageStorage.normalizeExt(ext);
        String lower = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".webp") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            int dot = fileName.lastIndexOf('.');
            return (dot > 0 ? fileName.substring(0, dot) : fileName) + safeExt;
        }

        return fileName + safeExt;
    }

    private static ItemStack inventoryStack(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    private static void mutateDisplayStack(ServerPlayer player, int entityId, java.util.function.Consumer<ItemStack> edit) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        var entity = level.getEntity(entityId);
        if (!(entity instanceof com.spider.mtgcard.display.CardDisplayEntity display)) {
            return;
        }

        double dx = display.getX() - player.getX();
        double dy = display.getY() - player.getY();
        double dz = display.getZ() - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            return;
        }

        ItemStack current = display.getStack();
        if (current.isEmpty()) {
            return;
        }

        ItemStack copy = current.copy();
        edit.accept(copy);
        display.setStack(copy);
    }

    private static CompoundTag getRoot(ItemStack stack) {
        var comp = stack.get(DataComponents.CUSTOM_DATA);
        return (comp == null) ? new CompoundTag() : comp.copyTag();
    }

    private static CompoundTag getMeta(ItemStack stack) {
        CompoundTag root = getRoot(stack);
        return root.getCompound("mtg_meta");
    }

    private static void writeMeta(ItemStack stack, CompoundTag meta) {
        CompoundTag root = getRoot(stack);
        root.put("mtg_meta", meta);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void writeFaceIndex(ItemStack stack, int index) {
        CompoundTag meta = getMeta(stack);
        meta.putInt("mtg_face", index);
        writeMeta(stack, meta);
    }

    private static boolean isDoubleFaced(ItemStack stack) {
        CompoundTag meta = getMeta(stack);
        var faces = meta.get("card_faces");
        return faces instanceof net.minecraft.nbt.ListTag list && list.size() >= 2;
    }

    private static int getFaceCount(ItemStack stack) {
        CompoundTag meta = getMeta(stack);
        var faces = meta.get("card_faces");
        if (faces instanceof net.minecraft.nbt.ListTag list) {
            return Math.max(1, list.size());
        }
        return 1;
    }

    private static int readFaceIndex(ItemStack stack) {
        return getMeta(stack).getInt("mtg_face");
    }

    private static void applyCounterValue(ItemStack stack, String key, int value) {
        if (key == null) return;
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) return;

        CompoundTag meta = getMeta(stack);
        CompoundTag counters = meta.getCompound("counters");
        if (value <= 0) counters.remove(normalized);
        else counters.putInt(normalized, value);
        meta.put("counters", counters);
        writeMeta(stack, meta);
    }

    private static void applyCounterMeta(ItemStack stack, String key, String displayName, String iconKey) {
        if (key == null) return;
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) return;

        CompoundTag meta = getMeta(stack);

        CompoundTag names = meta.getCompound("counter_names");
        String name = displayName == null ? "" : displayName.trim();
        if (name.isBlank()) names.remove(normalized);
        else names.putString(normalized, name);
        meta.put("counter_names", names);

        CompoundTag icons = meta.getCompound("counter_icons");
        String icon = iconKey == null ? "none" : iconKey.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (icon.isBlank() || "none".equals(icon)) icons.remove(normalized);
        else icons.putString(normalized, icon);
        meta.put("counter_icons", icons);

        writeMeta(stack, meta);
    }

    private ModPayloads() {}
}
