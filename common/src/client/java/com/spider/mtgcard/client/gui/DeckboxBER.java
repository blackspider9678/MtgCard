package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.deckbox.DeckboxBlockEntity;


public final class DeckboxBER {
    private DeckboxBER() {}
}
/*
public class DeckboxBER implements BlockEntityRenderer<DeckboxBlockEntity> {





    private final net.minecraft.client.font.TextRenderer font;

    public DeckboxBER(BlockEntityRendererFactory.Context ctx) {
        this.font = ctx.getTextRenderer();
    }

    @Override
    public void render(DeckboxBlockEntity be, float tickDelta,
                       net.minecraft.client.util.math.MatrixStack matrices,
                       net.minecraft.client.render.VertexConsumerProvider vcp,
                       int light, int overlay) {
        String name = be.getCommanderName();
        if (name.isEmpty()) return;
        matrices.push();
        matrices.translate(0.5, 0.65, 0.501);
        matrices.scale(0.01f, -0.01f, 0.01f);
        font.draw(name, -font.getWidth(name)/2f, 0, 0xFFFFFF, false,
                matrices.peek().getPositionMatrix(), vcp,
                net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }*/
