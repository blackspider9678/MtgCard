package com.spider.mtgcard;

import com.spider.mtgcard.display.CardDisplayEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final Identifier CARD_DISPLAY_ID = Identifier.fromNamespaceAndPath("mtgcard", "card_display");

    public static final ResourceKey<EntityType<?>> CARD_DISPLAY_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, CARD_DISPLAY_ID);

    public static final EntityType<CardDisplayEntity> CARD_DISPLAY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            CARD_DISPLAY_ID,
            EntityType.Builder
                    .<CardDisplayEntity>of(CardDisplayEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(CARD_DISPLAY_KEY)
    );

    public static void init() {}

    private ModEntities() {}
}