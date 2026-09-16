package com.spider.mtgcard.client.render;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.api.CardBackTextureRegistry;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class CardItemRenderer implements SpecialModelRenderer<CardItemRenderer.Data> {

    private static final float CARD_HALF_THICKNESS = 1f / 32f;
    private static final float EDGE_UV = 1f / 64f;

    public record Data(ItemStack stack) {}

    @Override
    public @Nullable Data extractArgument(ItemStack stack) {
        // ALWAYS run the special renderer so fallback can draw while loading.
        return new Data(stack);
    }

    @Override
    public void submit(@Nullable Data data,
                       ItemDisplayContext displayContext,
                       PoseStack matrices,
                       SubmitNodeCollector queue,
                       int light,
                       int overlay,
                       boolean glint,
                       int seed) {

        if (data == null) return;

        ItemStack stack = data.stack();
        // The physical item model represents the back of the card. Detailed
        // front art continues to be rendered by the existing GUI/entity paths.
        Identifier tex = CardBackTextureRegistry.textureForStackOrDefault(stack);
        boolean foil = glint || CardFoilUtil.isFoil(stack);

        matrices.pushPose();

        matrices.translate(0f, 0f, 0.5f);
        matrices.translate(1f / 16f, 1f / 16f, 0f);
        matrices.scale(14f / 16f, 14f / 16f, 14f / 16f);

        RenderType layer = RenderTypes.entityCutout(tex);

        queue.submitCustomGeometry(matrices, layer, (matrix, buffer) -> {
            Matrix4f m = matrix.pose();
            float front = CARD_HALF_THICKNESS;
            float back = -CARD_HALF_THICKNESS;

            // Front and back faces.
            buffer.addVertex(m, 1f, 0f, front).setColor(0xffffffff).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 1f, 1f, front).setColor(0xffffffff).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 0f, 1f, front).setColor(0xffffffff).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 0f, 0f, front).setColor(0xffffffff).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);

            buffer.addVertex(m, 0f, 0f, back).setColor(0xffffffff).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, -1f);
            buffer.addVertex(m, 0f, 1f, back).setColor(0xffffffff).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, -1f);
            buffer.addVertex(m, 1f, 1f, back).setColor(0xffffffff).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, -1f);
            buffer.addVertex(m, 1f, 0f, back).setColor(0xffffffff).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, -1f);

            // Edge strips turn the formerly flat quad into a thin card body.
            buffer.addVertex(m, 1f, 0f, front).setColor(0xffffffff).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 1f, 0f, 0f);
            buffer.addVertex(m, 1f, 0f, back).setColor(0xffffffff).setUv(1f - EDGE_UV, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 1f, 0f, 0f);
            buffer.addVertex(m, 1f, 1f, back).setColor(0xffffffff).setUv(1f - EDGE_UV, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 1f, 0f, 0f);
            buffer.addVertex(m, 1f, 1f, front).setColor(0xffffffff).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 1f, 0f, 0f);

            buffer.addVertex(m, 0f, 0f, back).setColor(0xffffffff).setUv(EDGE_UV, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, -1f, 0f, 0f);
            buffer.addVertex(m, 0f, 0f, front).setColor(0xffffffff).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, -1f, 0f, 0f);
            buffer.addVertex(m, 0f, 1f, front).setColor(0xffffffff).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, -1f, 0f, 0f);
            buffer.addVertex(m, 0f, 1f, back).setColor(0xffffffff).setUv(EDGE_UV, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, -1f, 0f, 0f);

            buffer.addVertex(m, 0f, 1f, front).setColor(0xffffffff).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 1f, 0f);
            buffer.addVertex(m, 1f, 1f, front).setColor(0xffffffff).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 1f, 0f);
            buffer.addVertex(m, 1f, 1f, back).setColor(0xffffffff).setUv(1f, EDGE_UV).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 1f, 0f);
            buffer.addVertex(m, 0f, 1f, back).setColor(0xffffffff).setUv(0f, EDGE_UV).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 1f, 0f);

            buffer.addVertex(m, 0f, 0f, back).setColor(0xffffffff).setUv(0f, 1f - EDGE_UV).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, -1f, 0f);
            buffer.addVertex(m, 1f, 0f, back).setColor(0xffffffff).setUv(1f, 1f - EDGE_UV).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, -1f, 0f);
            buffer.addVertex(m, 1f, 0f, front).setColor(0xffffffff).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, -1f, 0f);
            buffer.addVertex(m, 0f, 0f, front).setColor(0xffffffff).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, -1f, 0f);
        });

        matrices.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> vertices) {
        vertices.accept(new Vector3f(1f, 0f, CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(1f, 1f, CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(0f, 1f, CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(0f, 0f, CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(1f, 0f, -CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(1f, 1f, -CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(0f, 1f, -CARD_HALF_THICKNESS));
        vertices.accept(new Vector3f(0f, 0f, -CARD_HALF_THICKNESS));
    }

    public static class Unbaked implements net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public SpecialModelRenderer<?> bake(BakingContext context) {
            return new CardItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}

