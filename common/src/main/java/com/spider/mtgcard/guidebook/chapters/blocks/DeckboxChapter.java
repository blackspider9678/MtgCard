package com.spider.mtgcard.guidebook.chapters.blocks;

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

public final class DeckboxChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/deckbox");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deckbox");

    public DeckboxChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.deckbox.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deckbox.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_open")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_open_right_click"),
                        Component.translatable("guide.mtgcard.deckbox.b_open_state"),
                        Component.translatable("guide.mtgcard.deckbox.b_waterloggable")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_storage")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_main_grid"),
                        Component.translatable("guide.mtgcard.deckbox.b_cards_only"),
                        Component.translatable("guide.mtgcard.deckbox.b_side_slots")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_side_slots")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_bundle_slot"),
                        Component.translatable("guide.mtgcard.deckbox.b_commander_slot"),
                        Component.translatable("guide.mtgcard.deckbox.b_partner_slot")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_shift_click")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_quick_move_cards"),
                        Component.translatable("guide.mtgcard.deckbox.b_quick_move_bundle"),
                        Component.translatable("guide.mtgcard.deckbox.b_quick_move_blocked")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_tinting")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_dye_any_hand"),
                        Component.translatable("guide.mtgcard.deckbox.b_consumes_dye"),
                        Component.translatable("guide.mtgcard.deckbox.b_tint_bar")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_drops")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_keeps_contents"),
                        Component.translatable("guide.mtgcard.deckbox.b_picks_up_tint"),
                        Component.translatable("guide.mtgcard.deckbox.b_creative_break")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_redstone")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deckbox.b_comparator_main_only"),
                        Component.translatable("guide.mtgcard.deckbox.b_comparator_scale"),
                        Component.translatable("guide.mtgcard.deckbox.b_deck_control_notify")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deckbox.h_preview")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deckbox.p_preview"))
        );
    }
}
