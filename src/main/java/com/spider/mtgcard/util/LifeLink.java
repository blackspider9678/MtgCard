package com.spider.mtgcard.util;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record LifeLink(Identifier dimId, BlockPos pos) {
    public boolean isValid() { return dimId != null && pos != null; }
}
