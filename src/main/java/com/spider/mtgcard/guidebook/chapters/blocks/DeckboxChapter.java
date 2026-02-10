package com.spider.mtgcard.guidebook.chapters.blocks;

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

public final class DeckboxChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/deckbox");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "deckbox");

    public DeckboxChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.deckbox.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.deckbox.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_open")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_open_right_click"),
                        Text.translatable("guide.mtgcard.deckbox.b_open_state"),
                        Text.translatable("guide.mtgcard.deckbox.b_waterloggable")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_storage")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_main_grid"),
                        Text.translatable("guide.mtgcard.deckbox.b_cards_only"),
                        Text.translatable("guide.mtgcard.deckbox.b_side_slots")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_side_slots")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_bundle_slot"),
                        Text.translatable("guide.mtgcard.deckbox.b_commander_slot"),
                        Text.translatable("guide.mtgcard.deckbox.b_partner_slot")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_shift_click")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_quick_move_cards"),
                        Text.translatable("guide.mtgcard.deckbox.b_quick_move_bundle"),
                        Text.translatable("guide.mtgcard.deckbox.b_quick_move_blocked")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_tinting")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_dye_any_hand"),
                        Text.translatable("guide.mtgcard.deckbox.b_consumes_dye"),
                        Text.translatable("guide.mtgcard.deckbox.b_tint_bar")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_drops")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_keeps_contents"),
                        Text.translatable("guide.mtgcard.deckbox.b_picks_up_tint"),
                        Text.translatable("guide.mtgcard.deckbox.b_creative_break")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_redstone")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.deckbox.b_comparator_main_only"),
                        Text.translatable("guide.mtgcard.deckbox.b_comparator_scale"),
                        Text.translatable("guide.mtgcard.deckbox.b_deck_control_notify")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.deckbox.h_preview")),
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.deckbox.p_preview"))
        );
    }
}
