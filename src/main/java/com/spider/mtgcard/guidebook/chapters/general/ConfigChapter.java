package com.spider.mtgcard.guidebook.chapters.general;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ConfigChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/config");

    public ConfigChapter() {
        super(
                ID,
                GuideCategory.GENERAL,
                "guide.mtgcard.config.title",
                Identifier.of(Mtgcard.MOD_ID, "textures/gui/guidebook/config.png")
        );
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_location")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_location")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_location_toml"),
                        Text.translatable("guide.mtgcard.config.b_location_legacy")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_import")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_import")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_import_anyone"),
                        Text.translatable("guide.mtgcard.config.b_import_whitelist")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_cards")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_cards")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_cards_language"),
                        Text.translatable("guide.mtgcard.config.b_cards_language_examples")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_pack")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_pack")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_pack_common"),
                        Text.translatable("guide.mtgcard.config.b_pack_uncommon"),
                        Text.translatable("guide.mtgcard.config.b_pack_wildcard"),
                        Text.translatable("guide.mtgcard.config.b_pack_rare"),
                        Text.translatable("guide.mtgcard.config.b_pack_random"),
                        Text.translatable("guide.mtgcard.config.b_pack_foil"),
                        Text.translatable("guide.mtgcard.config.b_pack_basic"),
                        Text.translatable("guide.mtgcard.config.b_pack_token")
                )),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_pack_note")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_price")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.config.p_price")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_price_item"),
                        Text.translatable("guide.mtgcard.config.b_price_basis")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.config.h_tips")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.config.b_tips_restart"),
                        Text.translatable("guide.mtgcard.config.b_tips_servers"),
                        Text.translatable("guide.mtgcard.config.b_tips_ranges")
                ))
        );
    }
}
