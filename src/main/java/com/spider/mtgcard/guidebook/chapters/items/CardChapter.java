package com.spider.mtgcard.guidebook.chapters.items;

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

public final class CardChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/card");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "card");

    public CardChapter() {
        super(ID, GuideCategory.ITEMS, "guide.mtgcard.card.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card.h_large_view")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card.b_open_view"),
                        Text.translatable("guide.mtgcard.card.b_info"),
                        Text.translatable("guide.mtgcard.card.b_rotate"),
                        Text.translatable("guide.mtgcard.card.b_flip"),
                        Text.translatable("guide.mtgcard.card.b_facedown")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card.h_counters")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.card.p_counters")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card.b_hud"),
                        Text.translatable("guide.mtgcard.card.b_adjust"),
                        Text.translatable("guide.mtgcard.card.b_shift"),
                        Text.translatable("guide.mtgcard.card.b_editor")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.card.h_display")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.card.b_place"),
                        Text.translatable("guide.mtgcard.card.b_air"),
                        Text.translatable("guide.mtgcard.card.b_display_interact"),
                        Text.translatable("guide.mtgcard.card.b_display_break")
                ))
        );
    }
}
