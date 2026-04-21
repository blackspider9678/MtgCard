package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

public final class BlockRenderLayerMap {
    private BlockRenderLayerMap() {
    }

    public static void putBlock(Block block, ChunkSectionLayer layer) {
        // Compatibility no-op for newer Fabric versions where this helper is no longer bundled.
    }
}
