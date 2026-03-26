package com.spider.mtgcard.guidebook.chapters.blocks;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class CardStoreChapter extends BasicGuideChapter {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/card_store");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_store");

    public CardStoreChapter() {
        super(
                ID,
                GuideCategory.BLOCKS,
                "guide.mtgcard.card_store.title",
                () -> new ItemStack(BuiltInRegistries.ITEM.getValue(ICON_ITEM))
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card_store.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_store.h_store_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_store.b_search"),
                        Component.translatable("guide.mtgcard.card_store.b_scryfall_syntax"),
                        Component.translatable("guide.mtgcard.card_store.b_results_grid"),
                        Component.translatable("guide.mtgcard.card_store.b_sorting"),
                        Component.translatable("guide.mtgcard.card_store.b_preview")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_store.h_cart_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_store.b_cart_add"),
                        Component.translatable("guide.mtgcard.card_store.b_cart_qty"),
                        Component.translatable("guide.mtgcard.card_store.b_cart_total"),
                        Component.translatable("guide.mtgcard.card_store.b_cart_buy")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_store.h_printing_delivery")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_store.b_printing_ticks"),
                        Component.translatable("guide.mtgcard.card_store.b_deliver_deckboxes"),
                        Component.translatable("guide.mtgcard.card_store.b_eject_front"),
                        Component.translatable("guide.mtgcard.card_store.b_break_flush")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_store.h_import")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_store.b_drag_drop"),
                        Component.translatable("guide.mtgcard.card_store.b_txt_format"),
                        Component.translatable("guide.mtgcard.card_store.b_csv_format")
                ))
        );
    }
}
