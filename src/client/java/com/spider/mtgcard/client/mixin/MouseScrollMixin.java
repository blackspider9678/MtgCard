package com.spider.mtgcard.client.mixin;

import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseScrollMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = false)
    private void mtgcard$lifePointScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;
        if (client.currentScreen != null) return; // only in-world

        HitResult hr = client.crosshairTarget;
        if (!(hr instanceof BlockHitResult bhr)) return;

        var pos = bhr.getBlockPos();
        var state = client.world.getBlockState(pos);
        if (!state.isOf(com.spider.mtgcard.registry.ModBlocks.LIFE_POINT)) return;

        int delta = vertical > 0 ? 1 : -1;
        if (client.options.sneakKey.isPressed()) delta *= 10;

        ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, delta));
    }
}
