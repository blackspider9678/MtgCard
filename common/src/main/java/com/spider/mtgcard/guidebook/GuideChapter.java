package com.spider.mtgcard.guidebook;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.List;

public interface GuideChapter {

    Identifier id();                 // mtgcard:guide/card_store
    GuideCategory category();        // GENERAL / BLOCKS / ITEMS

    String titleKey();               // guide.mtgcard.card_store.title
    List<GuideSection> sections();   // content sections (blank for now)

    /**
     * Prefer an ItemStack icon for blocks/items. If empty, UI will fall back to textureIcon or default.
     */
    default ItemStack itemIcon() { return ItemStack.EMPTY; }

    /**
     * Optional texture icon (ex: commands icon).
     * Expected path: mtgcard:textures/gui/guidebook/<name>.png
     */
    default Identifier textureIcon() { return null; }
}
