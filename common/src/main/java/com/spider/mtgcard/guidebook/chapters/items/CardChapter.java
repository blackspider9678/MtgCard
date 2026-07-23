package com.spider.mtgcard.guidebook.chapters.items;

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

public final class CardChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/card");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card");

    public CardChapter() {
        super(ID, GuideCategory.ITEMS, "guide.mtgcard.card.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card.h_large_view")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card.b_open_view"),
                        Component.translatable("guide.mtgcard.card.b_info"),
                        Component.translatable("guide.mtgcard.card.b_rotate"),
                        Component.translatable("guide.mtgcard.card.b_flip"),
                        Component.translatable("guide.mtgcard.card.b_facedown")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card.h_counters")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.card.p_counters")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card.b_hud"),
                        Component.translatable("guide.mtgcard.card.b_adjust"),
                        Component.translatable("guide.mtgcard.card.b_shift"),
                        Component.translatable("guide.mtgcard.card.b_editor")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.card.h_display")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.card.b_place"),
                        Component.translatable("guide.mtgcard.card.b_air"),
                        Component.translatable("guide.mtgcard.card.b_display_interact"),
                        Component.translatable("guide.mtgcard.card.b_display_break")
                ))
        );
    }
}
