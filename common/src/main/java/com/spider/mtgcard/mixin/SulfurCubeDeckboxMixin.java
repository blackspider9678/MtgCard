package com.spider.mtgcard.mixin;

import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.deckbox.SulfurCubeDeckboxCards;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.SulfurCubeArchetype;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SulfurCube.class)
public abstract class SulfurCubeDeckboxMixin {
    @Unique
    private boolean mtgcard$previousHorizontalCollision;
    @Unique
    private boolean mtgcard$previousVerticalCollision;
    @Unique
    private boolean mtgcard$collisionStateInitialized;

    @Inject(method = "isSwallowableItem", at = @At("RETURN"), cancellable = true)
    private static void mtgcard$allowDeckboxes(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof DeckboxBlockItem) cir.setReturnValue(true);
    }

    @Inject(method = "matchingArchetypes", at = @At("HEAD"), cancellable = true)
    private void mtgcard$useWoodPhysics(ItemStack stack,
                                        CallbackInfoReturnable<List<SulfurCubeArchetype>> cir) {
        if (!(stack.getItem() instanceof DeckboxBlockItem)) return;

        SulfurCube cube = (SulfurCube) (Object) this;
        SulfurCubeArchetype bouncy = cube.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.SULFUR_CUBE_ARCHETYPE)
                .getValue(Identifier.withDefaultNamespace("bouncy"));
        if (bouncy != null) cir.setReturnValue(List.of(bouncy));
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void mtgcard$dropCardWhenHit(ServerLevel level, DamageSource source, float amount,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && source.getDirectEntity() instanceof Player) {
            SulfurCubeDeckboxCards.dropRandomCard((SulfurCube) (Object) this, level);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void mtgcard$dropCardWhenBouncing(CallbackInfo ci) {
        SulfurCube cube = (SulfurCube) (Object) this;
        if (!mtgcard$collisionStateInitialized) {
            mtgcard$previousHorizontalCollision = cube.horizontalCollision;
            mtgcard$previousVerticalCollision = cube.verticalCollision;
            mtgcard$collisionStateInitialized = true;
            return;
        }
        boolean bounced = (cube.horizontalCollision && !mtgcard$previousHorizontalCollision)
                || (cube.verticalCollision && !mtgcard$previousVerticalCollision);

        mtgcard$previousHorizontalCollision = cube.horizontalCollision;
        mtgcard$previousVerticalCollision = cube.verticalCollision;

        if (bounced && cube.level() instanceof ServerLevel level) {
            SulfurCubeDeckboxCards.dropRandomCard(cube, level);
        }
    }
}
