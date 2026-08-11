package com.spider.mtgcard.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.client.compat.ClientCompat;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.item.DiceItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class DiceItemRenderer implements SpecialModelRenderer<DiceItemRenderer.Data> {
    public record Data(int sides, DiceAppearance appearance) {}

    @Override
    public @Nullable Data extractArgument(ItemStack stack) {
        if (!(stack.getItem() instanceof DiceItem diceItem)) {
            return null;
        }

        DiceAppearance appearance = stack.get(ModDataComponents.DICE_APPEARANCE);
        if (appearance == null) {
            return null;
        }

        return new Data(diceItem.getSides(), appearance);
    }

    @Override
    public void submit(@Nullable Data data,
                       PoseStack matrices,
                       SubmitNodeCollector queue,
                       int light,
                       int overlay,
                       boolean glint,
                       int seed) {
        if (data == null) {
            return;
        }

        DiceTextureCache.TextureRef texture = DiceTextureCache.getTexture(data.sides(), data.appearance());

        matrices.pushPose();
        matrices.translate(0.0F, 0.0F, 0.5F);
        matrices.translate(1.0F / 16.0F, 1.0F / 16.0F, 0.0F);
        matrices.scale(14.0F / 16.0F, 14.0F / 16.0F, 14.0F / 16.0F);

        RenderType layer = RenderTypes.entityCutout(texture.id());
        queue.submitCustomGeometry(matrices, layer, (matrix, buffer) -> {
            Matrix4f pose = matrix.pose();
            buffer.addVertex(pose, 1.0F, 0.0F, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, 1.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, 0.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
            buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
        });

        if (glint || data.appearance().foil()) {
            matrices.pushPose();
            queue.submitCustomGeometry(matrices, ClientCompat.itemGlint(texture.id()), (matrix, buffer) -> {
                Matrix4f pose = matrix.pose();
                buffer.addVertex(pose, 1.0F, 0.0F, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
                buffer.addVertex(pose, 1.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
                buffer.addVertex(pose, 0.0F, 1.0F, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
                buffer.addVertex(pose, 0.0F, 0.0F, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(matrix, 0.0F, 0.0F, 1.0F);
            });
            matrices.popPose();
        }

        matrices.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> vertices) {
        vertices.accept(new Vector3f(1.0F, 0.0F, 0.0F));
        vertices.accept(new Vector3f(1.0F, 1.0F, 0.0F));
        vertices.accept(new Vector3f(0.0F, 1.0F, 0.0F));
        vertices.accept(new Vector3f(0.0F, 0.0F, 0.0F));
    }

    public static class Unbaked implements SpecialModelRenderer.Unbaked<Data> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public SpecialModelRenderer<Data> bake(BakingContext context) {
            return new DiceItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
