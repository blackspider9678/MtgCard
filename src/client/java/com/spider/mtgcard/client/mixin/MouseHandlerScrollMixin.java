package com.spider.mtgcard.client.mixin;

import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = false)
    private void mtgcard$lifePointScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;
        if (client.gui.screen() != null) return; // only in-world

        HitResult hr = client.hitResult;
        if (!(hr instanceof BlockHitResult bhr)) return;

        var pos = bhr.getBlockPos();
        var state = client.level.getBlockState(pos);
        if (!state.is(com.spider.mtgcard.registry.ModBlocks.LIFE_POINT)) return;

        int delta = vertical > 0 ? 1 : -1;
        if (client.options.keyShift.isDown()) delta *= 10;

        ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, delta));
    }
}
