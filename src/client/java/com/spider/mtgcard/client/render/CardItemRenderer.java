package com.spider.mtgcard.client.render;

import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.util.StackData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public class CardItemRenderer implements SpecialModelRenderer<CardItemRenderer.Data> {

    public record Data(ItemStack stack, int face) {}

    private static final Identifier FALLBACK_FRONT = Identifier.of("mtgcard", "textures/item/card.png");
    private static final Identifier FALLBACK_BACK  = Identifier.of("mtgcard", "textures/item/card_back.png");

    private static int readFaceIndex(ItemStack st) {
        NbtCompound root = StackData.readCustom(st);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        return meta.getInt("mtg_face").orElse(0);
    }

    @Override
    public @Nullable Data getData(ItemStack stack) {
        // ALWAYS run the special renderer so fallback can draw while loading.
        return new Data(stack, readFaceIndex(stack));
    }

    @Override
    public void render(@Nullable Data data,
                       ItemDisplayContext displayContext,
                       MatrixStack matrices,
                       OrderedRenderCommandQueue queue,
                       int light,
                       int overlay,
                       boolean glint,
                       int seed) {

        if (data == null) return;

        int face = data.face();
        ItemStack stack = data.stack();

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(stack, face);

        Identifier tex = (ref != null && ref.id() != null)
                ? ref.id()
                : (face == 1 ? FALLBACK_BACK : FALLBACK_FRONT);

        matrices.push();

        matrices.translate(0f, 0f, 0.5f);
        matrices.translate(1f / 16f, 1f / 16f, 0f);
        matrices.scale(14f / 16f, 14f / 16f, 14f / 16f);

        RenderLayer layer = RenderLayers.entityCutoutNoCull(tex);

        queue.submitCustom(matrices, layer, (matrix, buffer) -> {
            Matrix4f m = matrix.getPositionMatrix();

            buffer.vertex(m, 1f, 0f, 0f).color(0xffffffff).texture(1f, 1f).overlay(overlay).light(light).normal(matrix, 0f, 0f, 1f);
            buffer.vertex(m, 1f, 1f, 0f).color(0xffffffff).texture(1f, 0f).overlay(overlay).light(light).normal(matrix, 0f, 0f, 1f);
            buffer.vertex(m, 0f, 1f, 0f).color(0xffffffff).texture(0f, 0f).overlay(overlay).light(light).normal(matrix, 0f, 0f, 1f);
            buffer.vertex(m, 0f, 0f, 0f).color(0xffffffff).texture(0f, 1f).overlay(overlay).light(light).normal(matrix, 0f, 0f, 1f);
        });

        matrices.pop();
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> vertices) {
        vertices.accept(new Vector3f(1f, 0f, 0f));
        vertices.accept(new Vector3f(1f, 1f, 0f));
        vertices.accept(new Vector3f(0f, 1f, 0f));
        vertices.accept(new Vector3f(0f, 0f, 0f));
    }

    public static class Unbaked implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public SpecialModelRenderer<?> bake(BakeContext context) {
            return new CardItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> getCodec() {
            return MAP_CODEC;
        }
    }
}

