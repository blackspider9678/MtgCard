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

public final class DisplayChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/display");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "display_block");

    public DisplayChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.display.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.display.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.display.h_linking")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.display.b_link_sneak_use_item"),
                        Text.translatable("guide.mtgcard.display.b_link_sound"),
                        Text.translatable("guide.mtgcard.display.b_link_persists_item")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.display.h_placing")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.display.b_place_applies_link"),
                        Text.translatable("guide.mtgcard.display.b_place_inherit_from_neighbors"),
                        Text.translatable("guide.mtgcard.display.b_place_propagates_to_component")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.display.h_multiblock")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.display.b_multi_same_facing"),
                        Text.translatable("guide.mtgcard.display.b_multi_same_link_only"),
                        Text.translatable("guide.mtgcard.display.b_multi_largest_rectangle"),
                        Text.translatable("guide.mtgcard.display.b_multi_controller_only")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.display.h_rendering")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.display.b_render_same_dimension_only"),
                        Text.translatable("guide.mtgcard.display.b_render_turn_pip"),
                        Text.translatable("guide.mtgcard.display.b_render_dead_skull"),
                        Text.translatable("guide.mtgcard.display.b_render_icon_swap_color"),
                        Text.translatable("guide.mtgcard.display.b_render_commander_threshold")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.display.h_updates")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.display.b_update_controller_sync"),
                        Text.translatable("guide.mtgcard.display.b_update_missing_life_clears"),
                        Text.translatable("guide.mtgcard.display.b_update_no_chunk_load")
                ))
        );
    }
}
