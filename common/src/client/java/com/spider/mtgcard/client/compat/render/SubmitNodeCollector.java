package com.spider.mtgcard.client.compat.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.BiConsumer;

public final class SubmitNodeCollector {
    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    public void submitCustomGeometry(
            PoseStack matrices,
            RenderType layer,
            BiConsumer<PoseStack.Pose, VertexConsumer> renderer
    ) {
        renderer.accept(matrices.last(), buffers.getBuffer(layer));
    }

    public void submitCustomGeometry(
            PoseStack matrices,
            com.spider.mtgcard.client.compat.render.rendertype.RenderType layer,
            BiConsumer<PoseStack.Pose, VertexConsumer> renderer
    ) {
        submitCustomGeometry(matrices, layer.unwrap(), renderer);
    }

    public void submitText(
            PoseStack matrices,
            float x,
            float y,
            FormattedCharSequence text,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int color,
            int backgroundColor,
            int light,
            int overlay
    ) {
        Minecraft.getInstance().font.drawInBatch(
                text,
                x,
                y,
                color,
                dropShadow,
                matrices.last().pose(),
                buffers,
                displayMode,
                backgroundColor,
                light
        );
    }
}
