package com.spider.mtgcard.guidebook.chapters.items;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.guidebook.BasicGuideChapter;
import com.spider.mtgcard.guidebook.GuideCategory;
import com.spider.mtgcard.guidebook.GuideSection;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class PackChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/pack");

    public PackChapter() {
        super(ID, GuideCategory.ITEMS, "guide.mtgcard.pack.title", () -> new ItemStack(ModItems.MTG_PACK));
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.p_overview")),

                new GuideSection.Heading(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.h_opening")),
                new GuideSection.Bullets(List.of(
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_use"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_lock"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_hud"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_bundle")
                )),

                new GuideSection.Heading(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.h_set_codes")),
                new GuideSection.Paragraph(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.p_set_codes")),
                new GuideSection.Bullets(List.of(
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_brackets"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_bare"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_data_fallback")
                )),

                new GuideSection.Heading(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.h_contents")),
                new GuideSection.Bullets(List.of(
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_recipe"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_foil_slot"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_token_slot"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_no_dupes")
                )),

                new GuideSection.Heading(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.h_custom")),
                new GuideSection.Paragraph(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.p_custom")),
                new GuideSection.Bullets(List.of(
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_custom_unnamed"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_custom_force"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_custom_set_force"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_official_set_lock")
                )),

                new GuideSection.Heading(net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.h_notes")),
                new GuideSection.Bullets(List.of(
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_refund"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_creative"),
                        net.minecraft.network.chat.Component.translatable("guide.mtgcard.pack.b_sound")
                ))
        );
    }
}
