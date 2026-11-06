package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.item.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class PackMobDropEvents {
    private static final Item[] DICE_DROP_POOL = {
            ModItems.D4_DICE,
            ModItems.D6_DICE,
            ModItems.D8_DICE,
            ModItems.D10_DICE,
            ModItems.D12_DICE,
            ModItems.D20_DICE,
            ModItems.D100_DICE
    };

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }

            handleDeath(level, entity, damageSource);
        });
    }

    private static void handleDeath(ServerLevel level, LivingEntity entity, DamageSource damageSource) {
        MtgcardConfig cfg = MtgcardConfig.get();
        if (cfg == null) {
            return;
        }

        boolean killedByPlayer = wasKilledByPlayer(entity, damageSource);
        boolean hostileMob = entity instanceof Enemy;
        int packBossDropCount = cfg.mobPackBossDropCount(entity.getType());
        int diceBossDropCount = cfg.diceMobBossDropCount(entity.getType());
        boolean packBlacklisted = cfg.isPackMobDropBlacklisted(entity.getType());
        boolean diceBlacklisted = cfg.isDiceMobDropBlacklisted(entity.getType());

        if (!hostileMob && packBossDropCount <= 0 && diceBossDropCount <= 0) {
            return;
        }

        if (!packBlacklisted) {
            dropPackRewards(level, entity, cfg, killedByPlayer, packBossDropCount, hostileMob);
        }

        if (!diceBlacklisted) {
            dropDiceReward(level, entity, cfg, killedByPlayer, diceBossDropCount, hostileMob);
        }
    }

    private static void dropPackRewards(ServerLevel level, LivingEntity entity, MtgcardConfig cfg, boolean killedByPlayer, int bossDropCount, boolean hostileMob) {
        if (!cfg.mobPackDropsEnabled()) {
            return;
        }

        if (cfg.mobPackDropsRequirePlayerKill() && !killedByPlayer) {
            return;
        }

        if (bossDropCount > 0) {
            spawnDrop(level, entity, new ItemStack(ModItems.MTG_PACK, bossDropCount));
            return;
        }

        if (hostileMob && level.getRandom().nextDouble() < cfg.mobPackDropChance()) {
            spawnDrop(level, entity, new ItemStack(ModItems.MTG_PACK));
        }
    }

    private static void dropDiceReward(ServerLevel level, LivingEntity entity, MtgcardConfig cfg, boolean killedByPlayer, int bossDropCount, boolean hostileMob) {
        if (!cfg.diceMobDropsEnabled()) {
            return;
        }

        if (cfg.diceMobDropsRequirePlayerKill() && !killedByPlayer) {
            return;
        }

        if (bossDropCount > 0) {
            spawnRandomDiceDrops(level, entity, bossDropCount);
            return;
        }

        if (!hostileMob || level.getRandom().nextDouble() >= cfg.diceMobDropChance()) {
            return;
        }

        spawnRandomDiceDrops(level, entity, 1);
    }

    private static boolean wasKilledByPlayer(LivingEntity entity, DamageSource damageSource) {
        Entity sourceEntity = damageSource.getEntity();
        if (sourceEntity instanceof Player) {
            return true;
        }

        return entity.getKillCredit() instanceof Player;
    }

    private static void spawnDrop(ServerLevel level, LivingEntity entity, ItemStack stack) {
        ItemEntity itemEntity = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    private static void spawnRandomDiceDrops(ServerLevel level, LivingEntity entity, int count) {
        for (int i = 0; i < count; i++) {
            Item dice = DICE_DROP_POOL[level.getRandom().nextInt(DICE_DROP_POOL.length)];
            spawnDrop(level, entity, new ItemStack(dice));
        }
    }

    private PackMobDropEvents() {
    }
}
