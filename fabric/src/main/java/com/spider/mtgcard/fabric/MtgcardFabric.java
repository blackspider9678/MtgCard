package com.spider.mtgcard.fabric;

import com.spider.mtgcard.ModEvents;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.cardstore.CardStoreEnabledResourceCondition;
import com.spider.mtgcard.command.MtgRootCommand;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.PackServerEvents;
import com.spider.mtgcard.guidebook.GuideBook;
import com.spider.mtgcard.life.LifePlayGroups;
import com.spider.mtgcard.life.LifePointPackets;
import com.spider.mtgcard.loot.MtgLootInject;
import com.spider.mtgcard.net.ArtServerPackets;
import com.spider.mtgcard.net.CardDisplayServerNetworking;
import com.spider.mtgcard.net.CounterPackets;
import com.spider.mtgcard.net.CustomCardPackets;
import com.spider.mtgcard.net.CustomCardServer;
import com.spider.mtgcard.net.CustomCardSync;
import com.spider.mtgcard.net.DBPackets;
import com.spider.mtgcard.net.GuideBookPackets;
import com.spider.mtgcard.net.ModPayloads;
import com.spider.mtgcard.trade.ModTrades;
import com.spider.mtgcard.util.ArtImageStorage;
import com.spider.mtgcard.util.ModDispenserBehaviors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class MtgcardFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Mtgcard.LOGGER.info("[MtgcardFabric] init");
        ArtImageStorage.ensureWebpCodecsRegistered();

        ModPayloads.registerTypes();
        FabricRegistries.register();
        ModDispenserBehaviors.init();

        ModPayloads.registerServerReceivers();
        CounterPackets.registerReceivers();

        MtgcardConfig.load();
        CardStoreEnabledResourceCondition.register();
        ArtServerPackets.registerServerReceiver();
        CardDisplayServerNetworking.registerReceivers();

        DBPackets.registerTypes();
        DBPackets.registerServerReceivers();

        CustomCardPackets.registerServerReceiver();
        CustomCardSync.initServerHooks();

        LifePointPackets.registerCommon();

        ModTrades.init();

        MtgRootCommand.register();

        ServerTickEvents.END_LEVEL_TICK.register(LifePlayGroups::tickWorld);
        ServerTickEvents.END_SERVER_TICK.register(CustomCardServer::tick);
        ServerTickEvents.END_SERVER_TICK.register(ModPayloads::tickUnpackProgressBars);
        ServerTickEvents.END_SERVER_TICK.register(CustomCardSync::tick);
        MtgLootInject.init();
        ModEvents.register();
        PackServerEvents.init();

        GuideBookPackets.init();
        GuideBook.init();
    }
}
