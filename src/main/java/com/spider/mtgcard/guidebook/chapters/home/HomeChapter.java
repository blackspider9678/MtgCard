package com.spider.mtgcard.guidebook.chapters.home;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class HomeChapter extends BasicGuideChapter {

    private static final Identifier ID =
            Identifier.of(Mtgcard.MOD_ID, "guide/home");

    public HomeChapter() {
        super(
                ID,
                GuideCategory.HOME,
                "guide.mtgcard.home.title",
                Identifier.of(Mtgcard.MOD_ID, "textures/gui/guidebook/book.png")
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Heading(Text.translatable("guide.mtgcard.home.h_what")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.home.p_what")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.home.h_smp")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.home.p_smp")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.home.b_smp_economy"),
                        Text.translatable("guide.mtgcard.home.b_smp_trading"),
                        Text.translatable("guide.mtgcard.home.b_smp_social")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.home.h_design")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.home.b_design_builders"),
                        Text.translatable("guide.mtgcard.home.b_design_survival"),
                        Text.translatable("guide.mtgcard.home.b_design_modular")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.home.h_guide")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.home.p_guide")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.home.b_nav_categories"),
                        Text.translatable("guide.mtgcard.home.b_nav_chapters"),
                        Text.translatable("guide.mtgcard.home.b_nav_progression")
                ))
        );
    }
}
