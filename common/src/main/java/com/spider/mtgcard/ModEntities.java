package com.spider.mtgcard;

import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.dice.DiceEntity;
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

    public static final EntityType<CardDisplayEntity> CARD_DISPLAY =
            EntityType.Builder
                    .<CardDisplayEntity>of(CardDisplayEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(CARD_DISPLAY_KEY);

    public static final Identifier D4_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d4_dice");
    public static final EntityType<DiceEntity> D4_DICE = createDie(D4_DICE_ID);
    public static final Identifier D6_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d6_dice");
    public static final EntityType<DiceEntity> D6_DICE = createDie(D6_DICE_ID);
    public static final Identifier D8_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d8_dice");
    public static final EntityType<DiceEntity> D8_DICE = createDie(D8_DICE_ID);
    public static final Identifier D10_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d10_dice");
    public static final EntityType<DiceEntity> D10_DICE = createDie(D10_DICE_ID);
    public static final Identifier D12_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d12_dice");
    public static final EntityType<DiceEntity> D12_DICE = createDie(D12_DICE_ID);
    public static final Identifier D20_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d20_dice");
    public static final EntityType<DiceEntity> D20_DICE = createDie(D20_DICE_ID);
    public static final Identifier D100_DICE_ID = Identifier.fromNamespaceAndPath("mtgcard", "d100_dice");
    public static final EntityType<DiceEntity> D100_DICE = createDie(D100_DICE_ID);

    private static EntityType<DiceEntity> createDie(Identifier id) {
        return EntityType.Builder.<DiceEntity>of(DiceEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(1)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id));
    }

    public static void init() {}

    private ModEntities() {}
}
