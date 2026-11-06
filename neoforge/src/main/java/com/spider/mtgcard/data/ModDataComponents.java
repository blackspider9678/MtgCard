package com.spider.mtgcard.data;

import com.mojang.serialization.Codec;
import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Mtgcard.MOD_ID);

    public static DataComponentType<String> CARD_ART_ID;
    public static DataComponentType<com.spider.mtgcard.dice.DiceAppearance> DICE_APPEARANCE;
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.spider.mtgcard.dice.DiceAppearance>> DICE_APPEARANCE_HOLDER =
        DATA_COMPONENTS.registerComponentType("dice_appearance", b -> b.persistent(com.spider.mtgcard.dice.DiceAppearance.CODEC).networkSynchronized(com.spider.mtgcard.dice.DiceAppearance.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARD_ART_ID_HOLDER =
            DATA_COMPONENTS.registerComponentType("card_art_id",
                    builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {}

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.DATA_COMPONENT_TYPE)) {
                CARD_ART_ID = CARD_ART_ID_HOLDER.get();
                DICE_APPEARANCE = DICE_APPEARANCE_HOLDER.get();
            }
        });
    }

    public static void init() {
    }
}
