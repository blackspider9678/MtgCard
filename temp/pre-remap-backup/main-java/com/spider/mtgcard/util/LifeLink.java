package com.spider.mtgcard.util;

import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

public record LifeLink(Identifier dimId, BlockPos pos) {
    public boolean isValid() { return dimId != null && pos != null; }
}
