package com.spider.mtgcard.guidebook.chapters.items;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;

public final class DiceChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/dice");

    public DiceChapter() {
        super(
                ID,
                GuideCategory.ITEMS,
                "guide.mtgcard.dice.title",
                () -> new ItemStack(ModItems.D20_DICE)
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.p_overview")
                ),

                new GuideSection.Heading(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.h_using")
                ),
                new GuideSection.Bullets(List.of(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_right_click"),
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_stack_count"),
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_result")
                )),

                new GuideSection.Heading(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.h_detailed")
                ),
                new GuideSection.Bullets(List.of(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_sneak"),
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_individual_rolls")
                )),

                new GuideSection.Heading(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.h_visibility")
                ),
                new GuideSection.Bullets(List.of(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_range"),
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_dimension"),
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.b_chat")
                )),

                new GuideSection.Heading(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.h_types")
                ),
                new GuideSection.Paragraph(
                        net.minecraft.text.Text.translatable("guide.mtgcard.dice.p_types")
                )
        );
    }
}
