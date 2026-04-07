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

public final class LifePointChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/life_point");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "life_point");

    public LifePointChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.life_point.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.life_point.p_overview")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_open")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_open_right_click"),
                        Component.translatable("guide.mtgcard.life_point.b_open_name_field"),
                        Component.translatable("guide.mtgcard.life_point.b_open_tabs")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_life_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_life_type_to_set"),
                        Component.translatable("guide.mtgcard.life_point.b_life_scroll"),
                        Component.translatable("guide.mtgcard.life_point.b_life_buttons"),
                        Component.translatable("guide.mtgcard.life_point.b_life_counters_grid")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_commander_damage")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_cmd_select_target"),
                        Component.translatable("guide.mtgcard.life_point.b_cmd_edit_methods"),
                        Component.translatable("guide.mtgcard.life_point.b_cmd_updates_life"),
                        Component.translatable("guide.mtgcard.life_point.b_cmd_lethal_warning")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_counters_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_ctr_add_custom"),
                        Component.translatable("guide.mtgcard.life_point.b_ctr_edit_value"),
                        Component.translatable("guide.mtgcard.life_point.b_ctr_icons"),
                        Component.translatable("guide.mtgcard.life_point.b_ctr_builtin")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_pods_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_pod_create_select"),
                        Component.translatable("guide.mtgcard.life_point.b_pod_scan_nearby"),
                        Component.translatable("guide.mtgcard.life_point.b_pod_add_remove"),
                        Component.translatable("guide.mtgcard.life_point.b_pod_reorder"),
                        Component.translatable("guide.mtgcard.life_point.b_pod_save_delete"),
                        Component.translatable("guide.mtgcard.life_point.b_pod_locked_members")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_game_controls")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_game_start"),
                        Component.translatable("guide.mtgcard.life_point.b_game_pass"),
                        Component.translatable("guide.mtgcard.life_point.b_game_reset"),
                        Component.translatable("guide.mtgcard.life_point.b_game_dead_alive")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_redstone")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_rs_comparator_active"),
                        Component.translatable("guide.mtgcard.life_point.b_rs_pulse_pass_turn"),
                        Component.translatable("guide.mtgcard.life_point.b_rs_automation_tip")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.life_point.h_edit_tab")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.life_point.b_edit_colors"),
                        Component.translatable("guide.mtgcard.life_point.b_edit_format"),
                        Component.translatable("guide.mtgcard.life_point.b_edit_icon_and_swap"),
                        Component.translatable("guide.mtgcard.life_point.b_edit_presets")
                ))
        );
    }
}
