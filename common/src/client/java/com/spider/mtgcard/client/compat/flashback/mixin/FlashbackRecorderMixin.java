package com.spider.mtgcard.client.compat.flashback.mixin;

import com.spider.mtgcard.client.compat.flashback.FlashbackArtBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(targets = "com.moulberry.flashback.record.Recorder", remap = false)
public abstract class FlashbackRecorderMixin {
    @Inject(method = "writeCustomSnapshot", at = @At("HEAD"), remap = false)
    private void mtgcard$writeFlashbackArtSnapshot(Consumer<?> consumer, CallbackInfo ci) {
        FlashbackArtBridge.writeSnapshotArt(this);
    }
}
