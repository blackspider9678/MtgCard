package com.spider.mtgcard.advancement;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModAdvancements {
    private static final String PACK_CRITERION = "opened";

    private static final Identifier OPEN_BOOSTER_PACK = id("open_booster_pack");
    private static final Identifier OPEN_10_BOOSTER_PACKS = id("open_10_booster_packs");
    private static final Identifier OPEN_25_BOOSTER_PACKS = id("open_25_booster_packs");
    private static final Identifier OPEN_50_BOOSTER_PACKS = id("open_50_booster_packs");
    private static final Identifier OPEN_100_BOOSTER_PACKS = id("open_100_booster_packs");

    private static final Set<Identifier> MISSING_ADVANCEMENTS = ConcurrentHashMap.newKeySet();

    private ModAdvancements() {}

    public static void onBoosterPackOpened(ServerPlayer player) {
        player.awardStat(Stats.ITEM_USED.get(ModItems.MTG_PACK));
        int opened = player.getStats().getValue(Stats.ITEM_USED, ModItems.MTG_PACK);

        if (opened >= 1) {
            grant(player, OPEN_BOOSTER_PACK, PACK_CRITERION);
        }
        if (opened >= 10) {
            grant(player, OPEN_10_BOOSTER_PACKS, PACK_CRITERION);
        }
        if (opened >= 25) {
            grant(player, OPEN_25_BOOSTER_PACKS, PACK_CRITERION);
        }
        if (opened >= 50) {
            grant(player, OPEN_50_BOOSTER_PACKS, PACK_CRITERION);
        }
        if (opened >= 100) {
            grant(player, OPEN_100_BOOSTER_PACKS, PACK_CRITERION);
        }
    }

    private static void grant(ServerPlayer player, Identifier advancementId, String criterion) {
        var server = player.level().getServer();
        if (server == null) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(advancementId);
        if (advancement == null) {
            if (MISSING_ADVANCEMENTS.add(advancementId)) {
                Mtgcard.LOGGER.warn("[Mtgcard] Missing advancement '{}'", advancementId);
            }
            return;
        }

        player.getAdvancements().award(advancement, criterion);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }
}
