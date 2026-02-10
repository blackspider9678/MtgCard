package com.spider.mtgcard.client.render;

import com.spider.mtgcard.client.render.tex.CardDbBinderOverlayTex;
import com.spider.mtgcard.db.CardDatabaseBlock;
import com.spider.mtgcard.db.CardDatabaseBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class CardDatabaseBlockEntityRenderer
        implements BlockEntityRenderer<CardDatabaseBlockEntity, CardDatabaseBlockEntityRenderer.State> {

    public static final class State extends BlockEntityRenderState {
        public int frontFill, leftFill, rightFill, backFill;
        public Direction frontDir, leftDir, rightDir, backDir;
    }

    public CardDatabaseBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(
            CardDatabaseBlockEntity be,
            State s,
            float tickProgress,
            Vec3d cameraPos,
            @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay
    ) {
        s.frontFill = s.leftFill = s.rightFill = s.backFill = 0;
        s.frontDir = s.leftDir = s.rightDir = s.backDir = null;

        if (be.getWorld() == null) return;

        int binders = be.getClientBinderCount();
        if (binders <= 0) return;

        BlockState bs = be.getCachedState();
        if (!bs.contains(CardDatabaseBlock.FACING)) return;

        // Your allocation rules:
        // front face has 7-wide shelves => 7 * 5 = 35 binders max
        // other faces have 14-wide shelves => 14 * 5 = 70 binders max per face
        int front = Math.min(binders, 35);
        int rem = binders - front;

        int left  = Math.min(rem, 70); rem -= left;
        int right = Math.min(rem, 70); rem -= right;
        int back  = Math.min(rem, 70);

        Direction frontDir = bs.get(CardDatabaseBlock.FACING);
        s.frontDir = frontDir;
        s.leftDir  = frontDir.rotateYCounterclockwise();
        s.rightDir = frontDir.rotateYClockwise();
        s.backDir  = frontDir.getOpposite();

        s.frontFill = front;
        s.leftFill  = left;
        s.rightFill = right;
        s.backFill  = back;
    }

    @Override
    public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        if (s.frontFill <= 0 && s.leftFill <= 0 && s.rightFill <= 0 && s.backFill <= 0) return;

        // Pick an order; 0 is fine for “simple overlay”
        var batching = queue.getBatchingQueue(0);

        if (s.frontFill > 0) submitFace(matrices, (OrderedRenderCommandQueue) batching, s.frontDir, CardDbBinderOverlayTex.front(s.frontFill));
        if (s.leftFill  > 0) submitFace(matrices, (OrderedRenderCommandQueue) batching, s.leftDir,  CardDbBinderOverlayTex.side(s.leftFill));
        if (s.rightFill > 0) submitFace(matrices, (OrderedRenderCommandQueue) batching, s.rightDir, CardDbBinderOverlayTex.side(s.rightFill));
        if (s.backFill  > 0) submitFace(matrices, (OrderedRenderCommandQueue) batching, s.backDir,  CardDbBinderOverlayTex.side(s.backFill));
    }

    private static void submitFace(MatrixStack matrices, OrderedRenderCommandQueue queue, Direction dir, Identifier tex) {
        if (dir == null) return;

        RenderLayer layer = RenderLayers.entityCutoutNoCull(tex);

        queue.submitCustom(matrices, layer, (MatrixStack.Entry entry, VertexConsumer vc) -> {
            renderFace(entry, vc, dir);
        });
    }

    private static void renderFace(MatrixStack.Entry entry, VertexConsumer vc, Direction dir) {
        final float eps = 0.001f;
        Matrix4f mat = entry.getPositionMatrix();

        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        float x0 = 0f, y0 = 0f, z0 = 0f;
        float x1 = 1f, y1 = 1f, z1 = 1f;

        int light = 0x00F000F0; // fullbright; replace if you want world lighting
        int overlay = OverlayTexture.DEFAULT_UV;

        // NOTE: we’re not doing correct normals here because it’s a flat decal;
        // leaving normal (0,0,0) works fine for “no shading” with fullbright.
        switch (dir) {
            case NORTH -> {
                float z = z0 + eps;
                quad(vc, mat, overlay, light,
                        x0, y0, z,  u0, v1,
                        x1, y0, z,  u1, v1,
                        x1, y1, z,  u1, v0,
                        x0, y1, z,  u0, v0
                );
            }
            case SOUTH -> {
                float z = z1 - eps;
                quad(vc, mat, overlay, light,
                        x1, y0, z,  u0, v1,
                        x0, y0, z,  u1, v1,
                        x0, y1, z,  u1, v0,
                        x1, y1, z,  u0, v0
                );
            }
            case WEST -> {
                float x = x0 + eps;
                quad(vc, mat, overlay, light,
                        x, y0, z1,  u0, v1,
                        x, y0, z0,  u1, v1,
                        x, y1, z0,  u1, v0,
                        x, y1, z1,  u0, v0
                );
            }
            case EAST -> {
                float x = x1 - eps;
                quad(vc, mat, overlay, light,
                        x, y0, z0,  u0, v1,
                        x, y0, z1,  u1, v1,
                        x, y1, z1,  u1, v0,
                        x, y1, z0,  u0, v0
                );
            }
            default -> {}
        }
    }

    private static void quad(
            VertexConsumer vc, Matrix4f mat, int overlay, int light,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3
    ) {
        int r = 255, g = 255, b = 255, a = 255;

        vc.vertex(mat, x0, y0, z0).color(r,g,b,a).texture(u0, v0).overlay(overlay).light(light).normal(0,0,0);
        vc.vertex(mat, x1, y1, z1).color(r,g,b,a).texture(u1, v1).overlay(overlay).light(light).normal(0,0,0);
        vc.vertex(mat, x2, y2, z2).color(r,g,b,a).texture(u2, v2).overlay(overlay).light(light).normal(0,0,0);
        vc.vertex(mat, x3, y3, z3).color(r,g,b,a).texture(u3, v3).overlay(overlay).light(light).normal(0,0,0);
    }
}
