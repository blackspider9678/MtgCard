package com.spider.mtgcard.loot;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

public final class ModLootFunctions {
    public static final Identifier RANDOMIZE_DICE_APPEARANCE_ID =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "randomize_dice_appearance");

    public static final MapCodec<? extends LootItemFunction> RANDOMIZE_DICE_APPEARANCE = Registry.register(
            BuiltInRegistries.LOOT_FUNCTION_TYPE,
            RANDOMIZE_DICE_APPEARANCE_ID,
            RandomizeDiceAppearanceFunction.CODEC
    );

    public static void init() {
        // Class load registers loot item functions before data-driven trades are parsed.
    }

    private ModLootFunctions() {}
}
