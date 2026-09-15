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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class CardItemRenderer implements SpecialModelRenderer<CardItemRenderer.Data> {

    public record Data(ItemStack stack) {}

    @Override
    public @Nullable Data extractArgument(ItemStack stack) {
        // ALWAYS run the special renderer so fallback can draw while loading.
        return new Data(stack);
    }

    @Override
    public void submit(@Nullable Data data,
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

            buffer.addVertex(m, 1f, 0f, 0f).setColor(0xffffffff).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 1f, 1f, 0f).setColor(0xffffffff).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 0f, 1f, 0f).setColor(0xffffffff).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
            buffer.addVertex(m, 0f, 0f, 0f).setColor(0xffffffff).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
        });

        if (foil) {
            CardFoilUtil.Sweep sweep = CardFoilUtil.computeSweep(System.currentTimeMillis(), 1040);
            if (sweep != null) {
                matrices.pushPose();
                matrices.translate(0f, 0f, 0.001f);

                RenderType foilLayer = RenderTypes.entityTranslucent(tex);
                int foilColor = (CardFoilUtil.WORLD_SWEEP_ALPHA << 24) | 0x00FFFFFF;

                queue.submitCustomGeometry(matrices, foilLayer, (matrix, buffer) -> {
                    Matrix4f m = matrix.pose();
                    float x0 = sweep.u0();
                    float x1 = sweep.u1();

                    buffer.addVertex(m, x1, 0f, 0f).setColor(foilColor).setUv(x1, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
                    buffer.addVertex(m, x1, 1f, 0f).setColor(foilColor).setUv(x1, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
                    buffer.addVertex(m, x0, 1f, 0f).setColor(foilColor).setUv(x0, 0f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
                    buffer.addVertex(m, x0, 0f, 0f).setColor(foilColor).setUv(x0, 1f).setOverlay(overlay).setLight(light).setNormal(matrix, 0f, 0f, 1f);
                });

                matrices.popPose();
            }
        }

        matrices.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> vertices) {
        vertices.accept(new Vector3f(1f, 0f, 0f));
        vertices.accept(new Vector3f(1f, 1f, 0f));
        vertices.accept(new Vector3f(0f, 1f, 0f));
        vertices.accept(new Vector3f(0f, 0f, 0f));
    }

    public static class Unbaked implements SpecialModelRenderer.Unbaked<Data> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public SpecialModelRenderer<Data> bake(BakingContext context) {
            return new CardItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}

