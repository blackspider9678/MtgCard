package com.spider.mtgcard.guidebook.chapters.general;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class CommandsChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/commands");
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/gui/guidebook/commands.png");

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
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.commands.p_overview")),

                // =====================
                // Art Cache Commands
                // =====================
                new GuideSection.Heading(Component.translatable("guide.mtgcard.commands.h_art")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.commands.p_art")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.commands.b_art_root"),
                        Component.translatable("guide.mtgcard.commands.b_art_find"),
                        Component.translatable("guide.mtgcard.commands.b_art_purge"),
                        Component.translatable("guide.mtgcard.commands.b_art_rebuild")
                )),

                // =====================
                // Custom Card Commands
                // =====================
                new GuideSection.Heading(Component.translatable("guide.mtgcard.commands.h_custom")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.commands.p_custom")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.commands.b_custom_card"),
                        Component.translatable("guide.mtgcard.commands.b_custom_import"),
                        Component.translatable("guide.mtgcard.commands.b_custom_remove_card"),
                        Component.translatable("guide.mtgcard.commands.b_custom_remove_set"),
                        Component.translatable("guide.mtgcard.commands.b_custom_sets")
                )),

                // =====================
                // Deck Commands
                // =====================
                new GuideSection.Heading(Component.translatable("guide.mtgcard.commands.h_deck")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.commands.b_deck_list"),
                        Component.translatable("guide.mtgcard.commands.b_deck_export")
                )),

                // =====================
                // Tips
                // =====================
                new GuideSection.Heading(Component.translatable("guide.mtgcard.commands.h_tips")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.commands.b_tips_permissions"),
                        Component.translatable("guide.mtgcard.commands.b_tips_server"),
                        Component.translatable("guide.mtgcard.commands.b_tips_troubleshoot")
                ))
        );
    }
}
