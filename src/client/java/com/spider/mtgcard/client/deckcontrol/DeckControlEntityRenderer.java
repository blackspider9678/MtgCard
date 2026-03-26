package com.spider.mtgcard.client.deckcontrol;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class DeckControlEntityRenderer implements BlockEntityRenderer<DeckControlBlockEntity, DeckControlEntityRenderer.State> {
    public static final class State extends BlockEntityRenderState {
    }

    public DeckControlEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            DeckControlBlockEntity be,
            State state,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(be, state, tickProgress, cameraPos, crumblingOverlay);
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
    }
}
