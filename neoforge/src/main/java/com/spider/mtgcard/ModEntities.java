package com.spider.mtgcard;

import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final ResourceLocation CARD_DISPLAY_ID = ResourceLocation.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_display");
    public static final ResourceKey<EntityType<?>> CARD_DISPLAY_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, CARD_DISPLAY_ID);

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Mtgcard.MOD_ID);
    public static EntityType<CardDisplayEntity> CARD_DISPLAY;

    public static final DeferredHolder<EntityType<?>, EntityType<CardDisplayEntity>> CARD_DISPLAY_HOLDER =
            ENTITIES.register(
                    "card_display",
                    () -> EntityType.Builder
                            .<CardDisplayEntity>of(CardDisplayEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(CARD_DISPLAY_ID.toString())
            );

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
                CARD_DISPLAY = CARD_DISPLAY_HOLDER.get();
            }
        });
    }

    public static void init() {
    }
}
