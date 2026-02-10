package com.spider.mtgcard.life;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class LifePointRegistry {
    private static Identifier id(String path) { return Identifier.of("mtgcard", path); }

    public static final RegistryKey<Block> LIFE_POINT_KEY = RegistryKey.of(RegistryKeys.BLOCK, id("life_point"));
    public static final RegistryKey<Item>  LIFE_POINT_ITEM_KEY = RegistryKey.of(RegistryKeys.ITEM, id("life_point"));

    public static final Block LIFE_POINT_BLOCK = Registry.register(
            Registries.BLOCK,
            LIFE_POINT_KEY,
            new LifePointBlock(
                    Block.Settings.create()
                            .mapColor(MapColor.BLACK)
                            .strength(2.5f)
                            .nonOpaque()
                            .registryKey(LIFE_POINT_KEY)
                            .solidBlock((s, w, p) -> false)
            )
    );

    public static final Item LIFE_POINT_ITEM = Registry.register(
            Registries.ITEM,
            LIFE_POINT_ITEM_KEY,
            new BlockItem(LIFE_POINT_BLOCK, new Item.Settings().registryKey(LIFE_POINT_ITEM_KEY))
    );

    public static final BlockEntityType<LifePointBlockEntity> LIFE_POINT_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id("life_point"),
            FabricBlockEntityTypeBuilder.create(LifePointBlockEntity::new, LIFE_POINT_BLOCK).build()
    );

    private LifePointRegistry() {}
}
