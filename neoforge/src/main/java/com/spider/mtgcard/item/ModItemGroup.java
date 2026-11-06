package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItemGroup {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Mtgcard.MOD_ID);
    public static final ResourceKey<CreativeModeTab> MAIN_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, "main"));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GROUP =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.CARD_HOLDER.get()))
                    .title(Component.translatable("itemGroup.mtgcard"))
                    .build());

    private ModItemGroup() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
        modBus.addListener(ModItemGroup::buildCreativeTab);
    }

    private static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!MAIN_KEY.equals(event.getTabKey())) {
            return;
        }

        for (String path : ModItems.getCreativeTabOrder()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, path));
            if (item != null && item != Items.AIR) {
                event.accept(new ItemStack(item));
            }
        }
    }
}
