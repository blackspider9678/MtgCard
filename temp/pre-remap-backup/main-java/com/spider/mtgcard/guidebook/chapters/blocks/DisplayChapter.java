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

public final class DisplayChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/display");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "display_block");

    public DisplayChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.display.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.display.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.display.h_linking")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.display.b_link_sneak_use_item"),
                        Component.translatable("guide.mtgcard.display.b_link_sound"),
                        Component.translatable("guide.mtgcard.display.b_link_persists_item")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.display.h_placing")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.display.b_place_applies_link"),
                        Component.translatable("guide.mtgcard.display.b_place_inherit_from_neighbors"),
                        Component.translatable("guide.mtgcard.display.b_place_propagates_to_component")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.display.h_multiblock")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.display.b_multi_same_facing"),
                        Component.translatable("guide.mtgcard.display.b_multi_same_link_only"),
                        Component.translatable("guide.mtgcard.display.b_multi_largest_rectangle"),
                        Component.translatable("guide.mtgcard.display.b_multi_controller_only")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.display.h_rendering")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.display.b_render_same_dimension_only"),
                        Component.translatable("guide.mtgcard.display.b_render_turn_pip"),
                        Component.translatable("guide.mtgcard.display.b_render_dead_skull"),
                        Component.translatable("guide.mtgcard.display.b_render_icon_swap_color"),
                        Component.translatable("guide.mtgcard.display.b_render_commander_threshold")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.display.h_updates")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.display.b_update_controller_sync"),
                        Component.translatable("guide.mtgcard.display.b_update_missing_life_clears"),
                        Component.translatable("guide.mtgcard.display.b_update_no_chunk_load")
                ))
        );
    }
}
