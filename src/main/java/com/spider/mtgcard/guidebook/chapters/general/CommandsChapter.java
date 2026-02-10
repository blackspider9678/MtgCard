package com.spider.mtgcard.guidebook.chapters.general;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class CommandsChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/commands");
    private static final Identifier ICON = Identifier.of(Mtgcard.MOD_ID, "textures/gui/guidebook/commands.png");

    public CommandsChapter() {
        super(
                ID,
                GuideCategory.GENERAL,
                "guide.mtgcard.commands.title",
                ICON
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                // Overview
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.commands.p_overview")),

                // =====================
                // Art Cache Commands
                // =====================
                new GuideSection.Heading(Text.translatable("guide.mtgcard.commands.h_art")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.commands.p_art")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.commands.b_art_root"),
                        Text.translatable("guide.mtgcard.commands.b_art_find"),
                        Text.translatable("guide.mtgcard.commands.b_art_purge"),
                        Text.translatable("guide.mtgcard.commands.b_art_rebuild")
                )),

                // =====================
                // Custom Card Commands
                // =====================
                new GuideSection.Heading(Text.translatable("guide.mtgcard.commands.h_custom")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.commands.p_custom")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.commands.b_custom_card"),
                        Text.translatable("guide.mtgcard.commands.b_custom_import"),
                        Text.translatable("guide.mtgcard.commands.b_custom_remove_card"),
                        Text.translatable("guide.mtgcard.commands.b_custom_remove_set"),
                        Text.translatable("guide.mtgcard.commands.b_custom_sets")
                )),

                // =====================
                // Deck Commands
                // =====================
                new GuideSection.Heading(Text.translatable("guide.mtgcard.commands.h_deck")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.commands.b_deck_list"),
                        Text.translatable("guide.mtgcard.commands.b_deck_export")
                )),

                // =====================
                // Tips
                // =====================
                new GuideSection.Heading(Text.translatable("guide.mtgcard.commands.h_tips")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.commands.b_tips_permissions"),
                        Text.translatable("guide.mtgcard.commands.b_tips_server"),
                        Text.translatable("guide.mtgcard.commands.b_tips_troubleshoot")
                ))
        );
    }
}
