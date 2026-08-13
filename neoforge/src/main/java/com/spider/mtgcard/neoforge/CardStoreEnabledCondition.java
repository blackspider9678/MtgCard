package com.spider.mtgcard.neoforge;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.config.MtgcardConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public record CardStoreEnabledCondition() implements ICondition {
    public static final MapCodec<CardStoreEnabledCondition> CODEC =
            MapCodec.unit(CardStoreEnabledCondition::new);

    @Override
    public boolean test(IContext context) {
        return MtgcardConfig.cardStoreEnabled();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
