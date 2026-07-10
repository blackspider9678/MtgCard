package com.spider.mtgcard.neoforge;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.command.MtgRootCommand;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.NeoForgePackServerEvents;
import com.spider.mtgcard.guidebook.GuideBook;
import com.spider.mtgcard.life.LifePlayGroups;
import com.spider.mtgcard.loot.NeoForgeMtgLootInject;
import com.spider.mtgcard.net.CustomCardServer;
import com.spider.mtgcard.net.CustomCardSync;
import com.spider.mtgcard.net.ModPayloads;
import com.spider.mtgcard.util.ArtImageStorage;
import com.spider.mtgcard.util.ModDispenserBehaviors;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Mtgcard.MOD_ID)
public final class MtgcardNeoForge {
    public MtgcardNeoForge(IEventBus modBus) {
        Mtgcard.LOGGER.info("[MtgcardNeoForge] bootstrap");
        ArtImageStorage.ensureWebpCodecsRegistered();
        MtgcardConfig.load();
        NeoForgeRegistries.register(modBus);
        modBus.addListener(MtgcardNeoForge::onCommonSetup);
        modBus.addListener(MtgcardNeoForge::registerPayloads);
        CustomCardSync.initServerHooks(null);

        NeoForge.EVENT_BUS.addListener(MtgRootCommand::register);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onServerTick);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onLevelTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeMtgLootInject::onLootTableLoad);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            com.spider.mtgcard.client.MtgcardNeoForgeClient.register(modBus);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ModPayloads.tickUnpackProgressBars(event.getServer());
        CustomCardServer.tick(event.getServer());
        CustomCardSync.tick(event.getServer());
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModDispenserBehaviors.init();
            GuideBook.init();
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadTypeRegistry.reset();

        ModPayloads.registerServerReceivers();
        com.spider.mtgcard.net.ArtServerPackets.registerServerReceiver();
        com.spider.mtgcard.net.CardDisplayServerNetworking.registerReceivers();
        com.spider.mtgcard.net.CustomCardPackets.registerServerReceiver();
        com.spider.mtgcard.net.DBPackets.registerTypes();
        com.spider.mtgcard.net.DBPackets.registerServerReceivers();
        com.spider.mtgcard.net.GuideBookPackets.init();
        com.spider.mtgcard.life.LifePointPackets.registerCommon();

        PayloadTypeRegistry.apply(event);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            CustomCardSync.sendFullTo(player);
            NeoForgePackServerEvents.onPlayerLoggedIn(player);
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            NeoForgePackServerEvents.onPlayerLoggedOut(player);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            LifePlayGroups.tickWorld(serverLevel);
        }
    }
}
