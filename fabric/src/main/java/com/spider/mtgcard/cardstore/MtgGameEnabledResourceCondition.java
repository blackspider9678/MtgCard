package com.spider.mtgcard.cardstore;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

public final class MtgGameEnabledResourceCondition implements ResourceCondition {
    private static final ResourceConditionType<MtgGameEnabledResourceCondition> TYPE = ResourceConditionType.create(ModRegistry.id("mtg_game_enabled"), MapCodec.unit(MtgGameEnabledResourceCondition::new));
    public static void register() { ResourceConditions.register(TYPE); }
    @Override public ResourceConditionType<?> getType() { return TYPE; }
    @Override public boolean test(RegistryOps.@Nullable RegistryInfoLookup registryInfo) { return MtgcardConfig.mtgGameEnabled(); }
}
