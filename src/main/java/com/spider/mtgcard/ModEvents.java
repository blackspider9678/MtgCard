package com.spider.mtgcard;

import com.spider.mtgcard.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionResult;

public final class ModEvents {
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hit == null) return InteractionResult.PASS; // just in case

            var pos = hit.getBlockPos();
            var state = world.getBlockState(pos);

            // Only react to our block
            if (!state.is(ModBlocks.CARD_DB)) return InteractionResult.PASS;

            if (world.isClientSide()) {
                // Let the hand animation play; server will actually open the screen
                return InteractionResult.SUCCESS;
            }

            // Server: open the screen using the factory provided by the block state
            MenuProvider factory = state.getMenuProvider(world, pos);
            if (factory != null) {
                player.openMenu(factory);
                // Tell Fabric/Minecraft the interaction was fully handled
                return InteractionResult.CONSUME;
            }

            return InteractionResult.PASS;
        });
    }
}
