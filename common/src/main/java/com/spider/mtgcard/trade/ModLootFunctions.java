package com.spider.mtgcard.trade;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.Mtgcard;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

public final class ModLootFunctions {
    public static final Identifier RANDOMIZE_DICE_APPEARANCE_ID =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "randomize_dice_appearance");
    public static final MapCodec<? extends LootItemFunction> RANDOMIZE_DICE_APPEARANCE =
            RandomizeDiceAppearanceFunction.CODEC;

    private ModLootFunctions() {}
}
