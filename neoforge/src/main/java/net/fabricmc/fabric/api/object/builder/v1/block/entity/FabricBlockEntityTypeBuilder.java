package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class FabricBlockEntityTypeBuilder<T extends BlockEntity> {
    private final Factory<? extends T> factory;
    private final Block[] blocks;

    private FabricBlockEntityTypeBuilder(Factory<? extends T> factory, Block[] blocks) {
        this.factory = factory;
        this.blocks = blocks;
    }

    public static <T extends BlockEntity> FabricBlockEntityTypeBuilder<T> create(Factory<? extends T> factory, Block... blocks) {
        return new FabricBlockEntityTypeBuilder<>(factory, blocks);
    }

    public BlockEntityType<T> build() {
        return new BlockEntityType<>(factory::create, Set.of(blocks));
    }

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }
}
