package com.spider.mtgcard.neoforge;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.config.MtgcardConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public record MtgGameEnabledCondition() implements ICondition {
    public static final MapCodec<MtgGameEnabledCondition> CODEC = MapCodec.unit(MtgGameEnabledCondition::new);
    @Override public boolean test(IContext context) { return MtgcardConfig.mtgGameEnabled(); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
