package com.spider.mtgcard.neoforge;

import com.spider.mtgcard.ModEntities;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.command.MtgRootCommand;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.NeoForgePackServerEvents;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.guidebook.GuideBook;
import com.spider.mtgcard.item.ModItemGroup;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.life.LifePlayGroups;
import com.spider.mtgcard.net.CustomCardSync;
import com.spider.mtgcard.net.ModPayloads;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.registry.ModParticles;
import com.spider.mtgcard.screen.ModScreenHandlers;
import com.spider.mtgcard.util.ArtImageStorage;
import com.spider.mtgcard.util.ModDispenserBehaviors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Mtgcard.MOD_ID)
public final class MtgcardNeoForge {
    public MtgcardNeoForge(IEventBus modBus) {
        Mtgcard.LOGGER.info("[MtgcardNeoForge] bootstrap");
        ArtImageStorage.ensureWebpCodecsRegistered();
        MtgcardConfig.load();
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModScreenHandlers.register(modBus);
        ModParticles.register(modBus);
        ModItemGroup.register(modBus);
        modBus.addListener(ModPayloads::registerPayloads);
        ModDispenserBehaviors.init();
        GuideBook.init();

        NeoForge.EVENT_BUS.addListener(MtgRootCommand::register);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onServerTick);
        NeoForge.EVENT_BUS.addListener(MtgcardNeoForge::onLevelTick);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            com.spider.mtgcard.client.MtgcardNeoForgeClient.register(modBus);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ModPayloads.tickUnpackProgressBars(event.getServer());
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
