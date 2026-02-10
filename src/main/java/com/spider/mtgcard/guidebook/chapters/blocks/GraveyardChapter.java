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

public final class GraveyardChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/graveyard");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "graveyard");

    public GraveyardChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.graveyard.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.graveyard.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.graveyard.h_storage")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.graveyard.b_two_grids"),
                        Text.translatable("guide.mtgcard.graveyard.b_card_only"),
                        Text.translatable("guide.mtgcard.graveyard.b_open_state")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.graveyard.h_buttons")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.graveyard.b_exile_all"),
                        Text.translatable("guide.mtgcard.graveyard.b_return_all"),
                        Text.translatable("guide.mtgcard.graveyard.b_overflow_ejects")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.graveyard.h_hover_preview")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.graveyard.b_preview_cards_only"),
                        Text.translatable("guide.mtgcard.graveyard.b_preview_debounce"),
                        Text.translatable("guide.mtgcard.graveyard.b_preview_foil_shimmer")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.graveyard.h_hoppers")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.graveyard.b_hopper_insert_grave_only"),
                        Text.translatable("guide.mtgcard.graveyard.b_hopper_extract_blocked"),
                        Text.translatable("guide.mtgcard.graveyard.b_comparator_updates")
                ))
        );
    }
}
