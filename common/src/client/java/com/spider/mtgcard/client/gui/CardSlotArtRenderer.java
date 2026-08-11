package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.compat.GuiGraphics;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.render.CardFoilUtil;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class CardSlotArtRenderer {
    private static final int SLOT_ICON_SIZE = 16;

    public static boolean renderSlotArt(GuiGraphics ctx, Font font, Slot slot) {
        if (slot == null || !slot.hasItem()) return false;

        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty() || !stack.is(ModItemTags.TCG_CARD)) return false;

        int face = TcgCardMeta.read(stack).face();
        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(stack, face);
        if (ref == null || ref.id() == null || ref.texW() <= 0 || ref.texH() <= 0) return false;

        int texW = ref.texW();
        int texH = ref.texH();
        float aspect = (float) texW / (float) texH;

        int drawW = SLOT_ICON_SIZE;
        int drawH = Math.round(drawW / aspect);
        if (drawH > SLOT_ICON_SIZE) {
            drawH = SLOT_ICON_SIZE;
            drawW = Math.round(drawH * aspect);
        }

        int x = slot.x + (SLOT_ICON_SIZE - drawW) / 2;
        int y = slot.y + (SLOT_ICON_SIZE - drawH) / 2;

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                x, y,
                0f, 0f,
                drawW, drawH,
                texW, texH,
                texW, texH
        );

        if (CardFoilUtil.isFoil(stack)) {
            CardFoilUtil.Sweep sweep = CardFoilUtil.computeSweep(System.currentTimeMillis(), drawW);
            if (sweep != null) {
                ctx.fill(
                        x + sweep.drawU(),
                        y,
                        x + sweep.drawU() + sweep.clipW(),
                        y + drawH,
                        CardFoilUtil.guiShimmerColor(1f)
                );
            }
        }

        ctx.renderItemDecorations(font, stack, slot.x, slot.y);
        return true;
    }

    private CardSlotArtRenderer() {
    }
}
