package com.spider.mtgcard.guidebook.chapters.general;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ConfigChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/config");

    public ConfigChapter() {
        super(
                ID,
                GuideCategory.GENERAL,
                "guide.mtgcard.config.title",
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/gui/guidebook/config.png")
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_location")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_location")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_location_toml"),
                        Component.translatable("guide.mtgcard.config.b_location_legacy")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_import")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_import")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_import_anyone"),
                        Component.translatable("guide.mtgcard.config.b_import_whitelist")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_cards")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_cards")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_cards_language"),
                        Component.translatable("guide.mtgcard.config.b_cards_language_examples")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_pack")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_pack")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_pack_common"),
                        Component.translatable("guide.mtgcard.config.b_pack_uncommon"),
                        Component.translatable("guide.mtgcard.config.b_pack_wildcard"),
                        Component.translatable("guide.mtgcard.config.b_pack_rare"),
                        Component.translatable("guide.mtgcard.config.b_pack_random"),
                        Component.translatable("guide.mtgcard.config.b_pack_foil"),
                        Component.translatable("guide.mtgcard.config.b_pack_basic"),
                        Component.translatable("guide.mtgcard.config.b_pack_token")
                )),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_pack_note")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_price")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.config.p_price")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_price_item"),
                        Component.translatable("guide.mtgcard.config.b_price_basis")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.config.h_tips")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.config.b_tips_restart"),
                        Component.translatable("guide.mtgcard.config.b_tips_servers"),
                        Component.translatable("guide.mtgcard.config.b_tips_ranges")
                ))
        );
    }
}
