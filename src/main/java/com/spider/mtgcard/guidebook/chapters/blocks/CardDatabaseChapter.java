package com.spider.mtgcard.guidebook.chapters.blocks;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class CardDatabaseChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/card_database");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "card_database");

    public CardDatabaseChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.card_database.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card_database.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_database.h_storage")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_database.b_personal"),
                        Text.translatable("guide.mtgcard.card_database.b_cards_only")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_database.h_deckbox")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card_database.p_deckbox")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_database.b_adjacent"),
                        Text.translatable("guide.mtgcard.card_database.b_tabs"),
                        Text.translatable("guide.mtgcard.card_database.b_tab_names")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_database.h_tools")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_database.b_store_all"),
                        Text.translatable("guide.mtgcard.card_database.b_sort"),
                        Text.translatable("guide.mtgcard.card_database.b_route")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_database.h_search")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card_database.p_search")),
                new GuideSection.Heading(Text.translatable("guide.mtgcard.card_database.h_shift_click")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card_database.b_from_db"),
                        Text.translatable("guide.mtgcard.card_database.b_to_db")
                ))
        );
    }
}
