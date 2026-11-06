package com.spider.mtgcard.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;

public record LifeLink(ResourceLocation dimId, BlockPos pos) {
    public boolean isValid() { return dimId != null && pos != null; }
}
