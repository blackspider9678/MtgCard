package com.spider.mtgcard.client.net;

import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.client.content.pack.custom.ClientCardIndex;
import com.spider.mtgcard.client.life.LifePointClientState;
import com.spider.mtgcard.deckcontrol.DeckControlPackets;
import com.spider.mtgcard.life.LifePointPackets;
import com.spider.mtgcard.net.ArtPackets;
import com.spider.mtgcard.net.CustomCardPackets;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import com.spider.mtgcard.net.payload.DeckPayloads;
import com.spider.mtgcard.net.payload.DeckboxTabNamesPayload;
import com.spider.mtgcard.net.payload.UnbundleProgressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NeoForgeClientPayloadHandlers {
    private static int unpackProgress;
    private static final Map<String, IncomingArt> INCOMING_ART = new ConcurrentHashMap<>();
    private static final Map<UUID, FullSyncAccumulator> FULL_SYNCS = new ConcurrentHashMap<>();

    private NeoForgeClientPayloadHandlers() {}

    public static void handle(String action, Object payload) {
        switch (action) {
            case "unbundle_progress" -> handleUnbundleProgress((UnbundleProgressPayload) payload);
            case "art_chunk" -> artChunk((ArtPackets.ArtChunk) payload);
            case "guide_open" -> openGuideBook();
            case "custom_import_open" -> openCustomImport();
            case "open_life" -> openLife((LifePointPackets.OpenLifeScreenPayload) payload);
            case "sync_life" -> syncLife((LifePointPackets.SyncLifePayload) payload);
            case "life_your_preset_id" -> yourPresetId((LifePointPackets.YourPresetIdS2C) payload);
            case "life_nearby_result" -> nearbyResult((LifePointPackets.NearbyResultS2C) payload);
            case "life_groups_list" -> groupsList((LifePointPackets.GroupsListS2C) payload);
            case "life_group_snapshot" -> groupSnapshot((LifePointPackets.GroupSnapshotS2C) payload);
            case "life_group_removed" -> groupRemoved((LifePointPackets.GroupRemovedS2C) payload);
            case "deck_control_overlay" -> invokeOnCurrentScreen("onOverlayPayload", DeckControlPackets.OverlayS2C.class, payload);
            case "deck_control_cascade" -> invokeOnCurrentScreen("onCascadePayload", DeckControlPackets.CascadeS2C.class, payload);
            case "card_display_open" -> openCardDisplay((CardDisplayPayloads.OpenDisplayViewS2C) payload);
            case "card_display_attachments_open" -> openCardDisplayAttachments((CardDisplayPayloads.OpenAttachmentsS2C) payload);
            case "card_display_close_screens" -> closeCardDisplayScreens((CardDisplayPayloads.CloseDisplayScreensS2C) payload);
            case "deckbox_tab_names" -> deckboxTabNames((DeckboxTabNamesPayload) payload);
            case "deck_export_request" -> deckExport((DeckPayloads.DeckExportRequestS2C) payload);
            case "deck_list_request" -> deckList();
            case "card_store_search_result" -> invokeOnCurrentScreen("onSearchResult", CardStorePackets.SearchS2C.class, payload);
            case "card_store_search_prints_start" -> invokeOnCurrentScreen("onPrintsStart", CardStorePackets.SearchPrintsStartS2C.class, payload);
            case "card_store_search_prints_add" -> invokeOnCurrentScreen("onPrintsAdd", CardStorePackets.SearchPrintsAddS2C.class, payload);
            case "card_store_search_prints_done" -> invokeOnCurrentScreen("onPrintsDone", CardStorePackets.SearchPrintsDoneS2C.class, payload);
            case "cardstore_import_deck_result" -> invokeOnCurrentScreen("onImportDeckResult", CardStorePackets.ImportDeckS2C.class, payload);
            case "custom_sync_full" -> customSyncFull((CustomCardPackets.CustomSyncFull) payload);
            case "custom_sync_full_chunk" -> customSyncFullChunk((CustomCardPackets.CustomSyncFullChunk) payload);
            case "custom_sync_delta" -> customSyncDelta((CustomCardPackets.CustomSyncDelta) payload);
            case "custom_art_ready" -> customArtReady((CustomCardPackets.CustomArtReady) payload);
            case "custom_art_invalidate" -> customArtInvalidate((CustomCardPackets.CustomArtInvalidate) payload);
            default -> {
            }
        }
    }

    private static void handleUnbundleProgress(UnbundleProgressPayload payload) {
        unpackProgress = Math.max(0, Math.min(100, payload.percent()));
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.UnpackHud",
                "setProgressFromServer",
                new Class<?>[] { int.class },
                unpackProgress
        );
    }

    private static void artChunk(ArtPackets.ArtChunk payload) {
        final String key = payload.artKey();
        if (key == null || key.isBlank()) {
            return;
        }

        IncomingArt acc = INCOMING_ART.get(key);
        if (acc == null || acc.total != payload.total()) {
            acc = new IncomingArt(payload.total());
            INCOMING_ART.put(key, acc);
        }

        boolean done = acc.add(payload.index(), payload.data());
        if (done) {
            INCOMING_ART.remove(key);
            invokeStaticIfPresent(
                    "com.spider.mtgcard.client.java.CardArtManager",
                    "onArtResponse",
                    new Class<?>[] { String.class, byte[].class },
                    key,
                    acc.join()
            );
        }
    }

    private static void openGuideBook() {
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.guidebook.GuideBookClient",
                "open",
                new Class<?>[] { Identifier.class },
                new Object[] { null }
        );
    }

    private static void openCustomImport() {
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.gui.CustomImportScreen",
                "open",
                new Class<?>[] {}
        );
    }

    private static void deckExport(DeckPayloads.DeckExportRequestS2C payload) {
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.command.DeckClientIO",
                "handleExport",
                new Class<?>[] { String.class },
                payload.name()
        );
    }

    private static void deckList() {
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.command.DeckClientIO",
                "handleList",
                new Class<?>[] {}
        );
    }

    private static void openLife(LifePointPackets.OpenLifeScreenPayload payload) {
        LifePointClientState.onSync(payload.pos(), payload.state());
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.life.LifePointScreen",
                "open",
                new Class<?>[] { BlockPos.class, CompoundTag.class },
                payload.pos(),
                payload.state()
        );
    }

    private static void syncLife(LifePointPackets.SyncLifePayload payload) {
        LifePointClientState.onSync(payload.pos(), payload.state());
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return;
        }

        try {
            Method getPos = screen.getClass().getMethod("getPos");
            Object pos = getPos.invoke(screen);
            if (payload.pos().equals(pos)) {
                screen.getClass().getMethod("refresh").invoke(screen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void yourPresetId(LifePointPackets.YourPresetIdS2C payload) {
        invokeOnCurrentScreen("setYourPresetId", String.class, payload.id());
    }

    private static void nearbyResult(LifePointPackets.NearbyResultS2C payload) {
        LifePointClientState.onNearby(payload.origin(), payload.found());
        invokeOnCurrentScreen("onScanArrived", BlockPos.class, payload.origin());
    }

    private static void groupsList(LifePointPackets.GroupsListS2C payload) {
        LifePointClientState.onGroupsList(payload.groups());
        refreshCurrentScreen();
    }

    private static void groupSnapshot(LifePointPackets.GroupSnapshotS2C payload) {
        LifePointClientState.onGroup(
                payload.groupId(),
                payload.name(),
                payload.started(),
                payload.activeIndex(),
                payload.members(),
                payload.memberNames(),
                payload.dead()
        );
        refreshCurrentScreen();
    }

    private static void groupRemoved(LifePointPackets.GroupRemovedS2C payload) {
        LifePointClientState.onGroupRemoved(payload.groupId());
        refreshCurrentScreen();
    }

    private static void openCardDisplay(CardDisplayPayloads.OpenDisplayViewS2C payload) {
        try {
            Class<?> screenClass = Class.forName("com.spider.mtgcard.client.CardLargeViewScreen");
            Constructor<?> ctor = screenClass.getConstructor(
                    ItemStack.class,
                    int.class,
                    int.class,
                    UUID.class,
                    long.class,
                    UUID.class,
                    int.class
            );
            Object screen = ctor.newInstance(
                    payload.stack(),
                    -1,
                    payload.entityId(),
                    payload.hostId(),
                    payload.version(),
                    payload.selectedCardId(),
                    payload.attachmentCount()
            );
            if (screen instanceof Screen clientScreen) {
                Minecraft.getInstance().gui.setScreen(clientScreen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void openCardDisplayAttachments(CardDisplayPayloads.OpenAttachmentsS2C payload) {
        try {
            Class<?> screenClass = Class.forName("com.spider.mtgcard.client.AttachedCardsScreen");
            Constructor<?> ctor = screenClass.getConstructor(
                    int.class,
                    UUID.class,
                    long.class,
                    UUID.class,
                    ItemStack.class,
                    List.class
            );
            Object screen = ctor.newInstance(
                    payload.entityId(),
                    payload.hostId(),
                    payload.version(),
                    payload.selectedCardId(),
                    payload.hostStack(),
                    payload.attachments()
            );
            if (screen instanceof Screen clientScreen) {
                Minecraft.getInstance().gui.setScreen(clientScreen);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void closeCardDisplayScreens(CardDisplayPayloads.CloseDisplayScreensS2C payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && payload.message() != null && !payload.message().isBlank()) {
            mc.player.sendSystemMessage(Component.literal(payload.message()));
        }

        Screen screen = mc.gui.screen();
        if (screen == null) {
            return;
        }

        String className = screen.getClass().getName();
        if ("com.spider.mtgcard.client.CardLargeViewScreen".equals(className)
                || "com.spider.mtgcard.client.AttachedCardsScreen".equals(className)) {
            mc.gui.setScreen(null);
        }
    }

    private static void deckboxTabNames(DeckboxTabNamesPayload payload) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return;
        }

        try {
            Method method = screen.getClass().getMethod("applyDeckboxTabNames", int.class, List.class);
            method.invoke(screen, payload.syncId(), payload.tabNames());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void customSyncFull(CustomCardPackets.CustomSyncFull payload) {
        ClientCardIndex.applyFull(toClientList(payload.entries()));
    }

    private static void customSyncFullChunk(CustomCardPackets.CustomSyncFullChunk payload) {
        if (payload == null || payload.syncId() == null) return;
        int total = Math.max(1, payload.total());
        int index = payload.index();
        if (index < 0 || index >= total) return;

        FullSyncAccumulator acc = FULL_SYNCS.get(payload.syncId());
        if (acc == null || acc.total != total) {
            acc = new FullSyncAccumulator(total);
            FULL_SYNCS.put(payload.syncId(), acc);
        }

        if (acc.seen[index]) return;
        acc.seen[index] = true;
        acc.seenCount++;
        acc.entries.addAll(toClientList(payload.entries()));

        if (acc.seenCount >= acc.total) {
            FULL_SYNCS.remove(payload.syncId());
            ClientCardIndex.applyFull(acc.entries);
        }
    }

    private static void customSyncDelta(CustomCardPackets.CustomSyncDelta payload) {
        ClientCardIndex.applyDelta(toClient(payload.entry()));
    }

    private static void customArtReady(CustomCardPackets.CustomArtReady payload) {
        String key = payload.artKey();
        String setCode = payload.setCode();
        invokeStaticIfPresent(
                "com.spider.mtgcard.client.java.CardArtManager",
                "refreshWorldArt",
                new Class<?>[] { String.class, String.class },
                key,
                setCode
        );
    }

    private static void customArtInvalidate(CustomCardPackets.CustomArtInvalidate payload) {
        if (payload == null || payload.artKeys() == null) return;
        for (String key : payload.artKeys()) {
            invokeStaticIfPresent(
                    "com.spider.mtgcard.client.java.CardArtManager",
                    "forgetWorldArt",
                    new Class<?>[] { String.class },
                    key
            );
        }
    }

    private static List<ClientCardIndex.WireMeta> toClientList(List<CustomCardPackets.WireMeta> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        ArrayList<ClientCardIndex.WireMeta> out = new ArrayList<>(entries.size());
        for (CustomCardPackets.WireMeta entry : entries) {
            ClientCardIndex.WireMeta mapped = toClient(entry);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return out;
    }

    private static ClientCardIndex.WireMeta toClient(CustomCardPackets.WireMeta entry) {
        if (entry == null) {
            return null;
        }

        return new ClientCardIndex.WireMeta(
                entry.id(),
                entry.name(),
                entry.manaCost(),
                entry.typeLine(),
                entry.rarity(),
                entry.set(),
                entry.oracleText(),
                entry.power(),
                entry.toughness(),
                entry.loyalty(),
                entry.doubleFaced(),
                entry.backName(),
                entry.backTypeLine(),
                entry.backOracleText(),
                entry.backPower(),
                entry.backToughness(),
                entry.backLoyalty()
        );
    }

    private static final class IncomingArt {
        final int total;
        final byte[][] parts;
        int received;

        IncomingArt(int total) {
            this.total = Math.max(1, total);
            this.parts = new byte[this.total][];
        }

        boolean add(int index, byte[] data) {
            if (index < 0 || index >= total) return false;
            if (data == null) data = new byte[0];
            if (parts[index] == null) {
                parts[index] = data;
                received++;
            }
            return received >= total;
        }

        byte[] join() {
            int size = 0;
            for (byte[] part : parts) {
                size += part == null ? 0 : part.length;
            }

            byte[] out = new byte[size];
            int offset = 0;
            for (byte[] part : parts) {
                if (part == null) continue;
                System.arraycopy(part, 0, out, offset, part.length);
                offset += part.length;
            }
            return out;
        }
    }

    private static final class FullSyncAccumulator {
        final int total;
        final boolean[] seen;
        final ArrayList<ClientCardIndex.WireMeta> entries = new ArrayList<>();
        int seenCount;

        FullSyncAccumulator(int total) {
            this.total = Math.max(1, total);
            this.seen = new boolean[this.total];
        }
    }

    private static void refreshCurrentScreen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return;
        }

        try {
            screen.getClass().getMethod("refresh").invoke(screen);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invokeOnCurrentScreen(String methodName, Class<?> argType, Object arg) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return;
        }

        try {
            Method method = screen.getClass().getMethod(methodName, argType);
            method.invoke(screen, arg);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void invokeStaticIfPresent(String className, String methodName, Class<?>[] argTypes, Object... args) {
        try {
            Class<?> target = Class.forName(className);
            Method method = target.getMethod(methodName, argTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
