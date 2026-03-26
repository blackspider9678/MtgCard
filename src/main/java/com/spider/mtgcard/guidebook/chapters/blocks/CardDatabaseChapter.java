package com.spider.mtgcard.guidebook.chapters.blocks;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class CardDatabaseChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/card_database");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_database");

    public CardDatabaseChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.card_database.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card_database.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_database.h_storage")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_database.b_personal"),
                        Component.translatable("guide.mtgcard.card_database.b_cards_only")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_database.h_deckbox")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card_database.p_deckbox")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_database.b_adjacent"),
                        Component.translatable("guide.mtgcard.card_database.b_tabs"),
                        Component.translatable("guide.mtgcard.card_database.b_tab_names")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_database.h_tools")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_database.b_store_all"),
                        Component.translatable("guide.mtgcard.card_database.b_sort"),
                        Component.translatable("guide.mtgcard.card_database.b_route")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_database.h_search")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card_database.p_search")),
                new GuideSection.Heading(Component.translatable("guide.mtgcard.card_database.h_shift_click")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card_database.b_from_db"),
                        Component.translatable("guide.mtgcard.card_database.b_to_db")
                ))
        );
    }
}
