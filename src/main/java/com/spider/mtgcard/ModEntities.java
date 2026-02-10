// com/spider/mtgcard/ModEntities.java
package com.spider.mtgcard;

import com.spider.mtgcard.display.CardDisplayEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final Identifier CARD_DISPLAY_ID = Identifier.of("mtgcard", "card_display");

    public static final RegistryKey<EntityType<?>> CARD_DISPLAY_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, CARD_DISPLAY_ID);

    public static final EntityType<CardDisplayEntity> CARD_DISPLAY = Registry.register(
            Registries.ENTITY_TYPE,
            CARD_DISPLAY_ID,
            FabricEntityTypeBuilder
                    .<CardDisplayEntity>create(SpawnGroup.MISC, CardDisplayEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build(CARD_DISPLAY_KEY) // ✅ required in your Fabric API
    );

    /** Call from your mod initializer to guarantee classloading. */
    public static void init() {}

    private ModEntities() {}
}
