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

public final class LifePointChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.of(Mtgcard.MOD_ID, "guide/life_point");
    private static final Identifier ICON_ITEM = Identifier.of(Mtgcard.MOD_ID, "life_point");

    public LifePointChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.life_point.title", () -> {
            var item = Registries.ITEM.get(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Paragraph(Text.translatable("guide.mtgcard.life_point.p_overview")),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_open")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_open_right_click"),
                        Text.translatable("guide.mtgcard.life_point.b_open_name_field"),
                        Text.translatable("guide.mtgcard.life_point.b_open_tabs")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_life_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_life_type_to_set"),
                        Text.translatable("guide.mtgcard.life_point.b_life_scroll"),
                        Text.translatable("guide.mtgcard.life_point.b_life_buttons"),
                        Text.translatable("guide.mtgcard.life_point.b_life_counters_grid")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_commander_damage")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_cmd_select_target"),
                        Text.translatable("guide.mtgcard.life_point.b_cmd_edit_methods"),
                        Text.translatable("guide.mtgcard.life_point.b_cmd_updates_life"),
                        Text.translatable("guide.mtgcard.life_point.b_cmd_lethal_warning")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_counters_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_ctr_add_custom"),
                        Text.translatable("guide.mtgcard.life_point.b_ctr_edit_value"),
                        Text.translatable("guide.mtgcard.life_point.b_ctr_icons"),
                        Text.translatable("guide.mtgcard.life_point.b_ctr_builtin")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_pods_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_pod_create_select"),
                        Text.translatable("guide.mtgcard.life_point.b_pod_scan_nearby"),
                        Text.translatable("guide.mtgcard.life_point.b_pod_add_remove"),
                        Text.translatable("guide.mtgcard.life_point.b_pod_reorder"),
                        Text.translatable("guide.mtgcard.life_point.b_pod_save_delete"),
                        Text.translatable("guide.mtgcard.life_point.b_pod_locked_members")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_game_controls")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_game_start"),
                        Text.translatable("guide.mtgcard.life_point.b_game_pass"),
                        Text.translatable("guide.mtgcard.life_point.b_game_reset"),
                        Text.translatable("guide.mtgcard.life_point.b_game_dead_alive")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_redstone")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_rs_comparator_active"),
                        Text.translatable("guide.mtgcard.life_point.b_rs_pulse_pass_turn"),
                        Text.translatable("guide.mtgcard.life_point.b_rs_automation_tip")
                )),

                new GuideSection.Heading(Text.translatable("guide.mtgcard.life_point.h_edit_tab")),
                new GuideSection.Bullets(List.of(
                        Text.translatable("guide.mtgcard.life_point.b_edit_colors"),
                        Text.translatable("guide.mtgcard.life_point.b_edit_format"),
                        Text.translatable("guide.mtgcard.life_point.b_edit_icon_and_swap"),
                        Text.translatable("guide.mtgcard.life_point.b_edit_presets")
                ))
        );
    }
}
