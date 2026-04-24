package com.spider.mtgcard;

import com.spider.mtgcard.cardstore.CardStorePackets;
import com.spider.mtgcard.command.MtgRootCommand;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.PackServerEvents;
import com.spider.mtgcard.guidebook.GuideBook;
import com.spider.mtgcard.item.ModItemGroup;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.life.LifePlayGroups;
import com.spider.mtgcard.life.LifePointPackets;
import com.spider.mtgcard.loot.MtgLootInject;
import com.spider.mtgcard.net.*;
import com.spider.mtgcard.registry.ModBlockEntities;
import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.screen.ModScreenHandlers;
import com.spider.mtgcard.trade.ModTrades;

import com.spider.mtgcard.util.ArtImageStorage;
import com.spider.mtgcard.util.ModDispenserBehaviors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Mtgcard implements ModInitializer {
    public static final String MOD_ID = "mtgcard";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Mtgcard.LOGGER.info("[Mtgcard] Mod Unpacked");
        ArtImageStorage.ensureWebpCodecsRegistered();

        // 1) Register ALL payload CODECs (safe on both sides, must happen before receiver registration)
        ModPayloads.registerTypes();

        // 2) Register blocks/items + block entities BEFORE anything might reference them
        ModBlocks.init();
        ModBlockEntities.init();
        ModDispenserBehaviors.init();

        // 3) Register screen handlers (common)
        ModScreenHandlers.register();

        // 4) Register server-side networking receivers (must be after types)
        ModPayloads.registerServerReceivers();
        CounterPackets.registerReceivers();

        // Other server/common systems
        MtgcardConfig.load();
        ArtServerPackets.registerServerReceiver();
        CardDisplayServerNetworking.registerReceivers();

        // DB networking
        DBPackets.registerTypes();
        DBPackets.registerServerReceivers();

        // Custom cards
        CustomCardPackets.registerServerReceiver();
        CustomCardSync.initServerHooks();

        // Life (common)
        LifePointPackets.registerCommon();

        // Entities/items/registry
        ModEntities.init();
        ModItems.initialize();
        ModTrades.init();
        ModItemGroup.register();

        // Commands
        MtgRootCommand.register();

        // Ticks / loot / events
        ServerTickEvents.END_LEVEL_TICK.register(LifePlayGroups::tickWorld);
        MtgLootInject.init();
        ModEvents.register();
        PackServerEvents.init();

        //Guide Book
        GuideBookPackets.init();
        GuideBook.init();
    }
}
