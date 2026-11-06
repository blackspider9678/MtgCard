package com.spider.mtgcard.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ShulkerRenderer;
import net.minecraft.client.renderer.entity.layers.ShulkerHeadLayer;
import net.minecraft.world.entity.monster.Shulker;

/** Keeps the health block's glowing shell without 1.21.1's visible shulker head. */
public final class LifePointMarkerShulkerRenderer extends ShulkerRenderer {
    public LifePointMarkerShulkerRenderer(EntityRendererProvider.Context context) {
        super(context);
        layers.removeIf(layer -> layer instanceof ShulkerHeadLayer);
        addLayer(new ShulkerHeadLayer(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buffers, int light,
                               Shulker shulker, float limbSwing, float limbSwingAmount,
                               float partialTick, float age, float headYaw, float headPitch) {
                // These synchronized flags identify the health block's outline marker.
                // Leave ordinary shulkers' head rendering unchanged.
                if (shulker.isNoAi() && shulker.isInvisible() && shulker.isCurrentlyGlowing()) return;
                super.render(pose, buffers, light, shulker, limbSwing, limbSwingAmount,
                        partialTick, age, headYaw, headPitch);
            }
        });
    }
}
