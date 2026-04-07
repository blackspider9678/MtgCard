package com.spider.mtgcard.guidebook.chapters.home;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class HomeChapter extends BasicGuideChapter {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/home");

    public HomeChapter() {
        super(
                ID,
                GuideCategory.HOME,
                "guide.mtgcard.home.title",
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/gui/guidebook/book.png")
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Heading(Component.translatable("guide.mtgcard.home.h_what")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.home.p_what")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.home.h_smp")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.home.p_smp")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.home.b_smp_economy"),
                        Component.translatable("guide.mtgcard.home.b_smp_trading"),
                        Component.translatable("guide.mtgcard.home.b_smp_social")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.home.h_design")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.home.b_design_builders"),
                        Component.translatable("guide.mtgcard.home.b_design_survival"),
                        Component.translatable("guide.mtgcard.home.b_design_modular")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.home.h_guide")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.home.p_guide")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.home.b_nav_categories"),
                        Component.translatable("guide.mtgcard.home.b_nav_chapters"),
                        Component.translatable("guide.mtgcard.home.b_nav_progression")
                ))
        );
    }
}
