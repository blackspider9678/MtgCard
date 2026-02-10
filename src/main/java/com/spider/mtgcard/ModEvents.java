package com.spider.mtgcard;

import com.spider.mtgcard.registry.ModBlocks;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;

public final class ModEvents {
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hit == null) return ActionResult.PASS; // just in case

            var pos = hit.getBlockPos();
            var state = world.getBlockState(pos);

            // Only react to our block
            if (!state.isOf(ModBlocks.CARD_DB)) return ActionResult.PASS;

            if (world.isClient()) {
                // Let the hand animation play; server will actually open the screen
                return ActionResult.SUCCESS;
            }

            // Server: open the screen using the factory provided by the block state
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
                // Tell Fabric/Minecraft the interaction was fully handled
                return ActionResult.CONSUME;
            }

            return ActionResult.PASS;
        });
    }
}
