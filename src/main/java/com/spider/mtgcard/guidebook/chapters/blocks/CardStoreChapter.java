package com.spider.mtgcard.guidebook.chapters.blocks;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class CardStoreChapter extends BasicGuideChapter {

    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/card_store");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "card_store");

    public CardStoreChapter() {
        super(
                ID,
                GuideCategory.BLOCKS,
                "guide.mtgcard.card_store.title",
                () -> new ItemStack(Registries.ITEM.get(ICON_ITEM))
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card_store.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_store.h_store_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_store.b_search"),
                        Text.translatable("guide.mtgcard.card_store.b_scryfall_syntax"),
                        Text.translatable("guide.mtgcard.card_store.b_results_grid"),
                        Text.translatable("guide.mtgcard.card_store.b_sorting"),
                        Text.translatable("guide.mtgcard.card_store.b_preview")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_store.h_cart_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_store.b_cart_add"),
                        Text.translatable("guide.mtgcard.card_store.b_cart_qty"),
                        Text.translatable("guide.mtgcard.card_store.b_cart_total"),
                        Text.translatable("guide.mtgcard.card_store.b_cart_buy")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_store.h_printing_delivery")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_store.b_printing_ticks"),
                        Text.translatable("guide.mtgcard.card_store.b_deliver_deckboxes"),
                        Text.translatable("guide.mtgcard.card_store.b_eject_front"),
                        Text.translatable("guide.mtgcard.card_store.b_break_flush")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_store.h_import")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_store.b_drag_drop"),
                        Text.translatable("guide.mtgcard.card_store.b_txt_format"),
                        Text.translatable("guide.mtgcard.card_store.b_csv_format")
                ))
        );
    }
}
