package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.api.CardItemRegistry;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.client.render.CardFoilUtil;
import com.spider.mtgcard.util.StackData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/** Draws cached card artwork in storage slots without changing their item data. */
public final class CardSlotArtRenderer {
    public static boolean isArtSlot(AbstractContainerScreen<?> screen, Slot slot) {
        int index = screen.getMenu().slots.indexOf(slot);
        if (screen instanceof DeckboxScreen) {
            return index >= 0 && index < com.spider.mtgcard.deckbox.DeckboxBlockEntity.INVENTORY_SIZE;
        }
        if (screen instanceof CardDatabaseScreen) {
            int windowSlots = com.spider.mtgcard.db.CardDatabaseScreenHandler.DB_ROWS
                    * com.spider.mtgcard.db.CardDatabaseScreenHandler.DB_COLS;
            return index >= 0 && (index < windowSlots || index >= windowSlots + 36);
        }
        return false;
    }

    /** Draw only the item image; vanilla retains counts, splitting and drag previews. */
    public static boolean renderArt(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack,
                                    int itemX, int itemY) {
        if (stack.isEmpty() || !CardItemRegistry.isCard(stack)) return false;
        int face = StackData.readCustom(stack).getCompound("mtg_meta").getInt("mtg_face");
        var art = CardArtManager.getOrRequestFace(stack, face);
        if (art == null || art.id() == null || art.texW() <= 0 || art.texH() <= 0) return false;

        float fit = Math.min(16f / art.texW(), 16f / art.texH());
        int width = Math.max(1, Math.round(art.texW() * fit));
        int height = Math.max(1, Math.round(art.texH() * fit));
        int x = itemX + (16-width)/2, y = itemY + (16-height)/2;
        graphics.blit(art.id(), x, y, width, height, 0f, 0f,
                art.texW(), art.texH(), art.texW(), art.texH());
        if (CardFoilUtil.isFoil(stack)) {
            var sweep = CardFoilUtil.computeSweep(System.currentTimeMillis(), width);
            if (sweep != null) graphics.fill(x+sweep.drawU(), y, x+sweep.drawU()+sweep.clipW(),
                    y+height, CardFoilUtil.guiShimmerColor(1f));
        }
        return true;
    }
    private CardSlotArtRenderer() {}
}
