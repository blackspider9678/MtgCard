package com.spider.mtgcard.life;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

public final class LifePointRegistry {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("mtgcard", path); }

    public static final ResourceKey<Block> LIFE_POINT_KEY = ResourceKey.create(Registries.BLOCK, id("life_point"));
    public static final ResourceKey<Item>  LIFE_POINT_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("life_point"));

    public static final Block LIFE_POINT_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            LIFE_POINT_KEY,
            new LifePointBlock(
                    Block.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(2.5f)
                            .noOcclusion()
                            .setId(LIFE_POINT_KEY)
                            .isRedstoneConductor((s, w, p) -> false)
            )
    );

    public static final Item LIFE_POINT_ITEM = Registry.register(
            BuiltInRegistries.ITEM,
            LIFE_POINT_ITEM_KEY,
            new BlockItem(LIFE_POINT_BLOCK, new net.minecraft.world.item.Item.Properties().setId(LIFE_POINT_ITEM_KEY))
    );

    public static final BlockEntityType<LifePointBlockEntity> LIFE_POINT_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("life_point"),
            FabricBlockEntityTypeBuilder.create(LifePointBlockEntity::new, LIFE_POINT_BLOCK).build()
    );

    private LifePointRegistry() {}
}
