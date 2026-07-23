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

public final class GraveyardChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/graveyard");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "graveyard");

    public GraveyardChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.graveyard.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.graveyard.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.graveyard.h_storage")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.graveyard.b_two_grids"),
                        Component.translatable("guide.mtgcard.graveyard.b_card_only"),
                        Component.translatable("guide.mtgcard.graveyard.b_open_state")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.graveyard.h_buttons")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.graveyard.b_exile_all"),
                        Component.translatable("guide.mtgcard.graveyard.b_return_all"),
                        Component.translatable("guide.mtgcard.graveyard.b_overflow_ejects")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.graveyard.h_hover_preview")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.graveyard.b_preview_cards_only"),
                        Component.translatable("guide.mtgcard.graveyard.b_preview_debounce"),
                        Component.translatable("guide.mtgcard.graveyard.b_preview_foil_shimmer")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.graveyard.h_hoppers")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.graveyard.b_hopper_insert_grave_only"),
                        Component.translatable("guide.mtgcard.graveyard.b_hopper_extract_blocked"),
                        Component.translatable("guide.mtgcard.graveyard.b_comparator_updates")
                ))
        );
    }
}
