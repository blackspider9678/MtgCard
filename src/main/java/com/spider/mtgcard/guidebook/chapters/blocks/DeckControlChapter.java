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

public final class DeckControlChapter extends BasicGuideChapter {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide/deck_control");
    private static final Identifier ICON_ITEM = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "deck_control_stone");

    public DeckControlChapter() {
        super(ID, GuideCategory.BLOCKS, "guide.mtgcard.deck_control.title", () -> {
            var item = BuiltInRegistries.ITEM.getValue(ICON_ITEM);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    @Override
    public List<GuideSection> sections() {
        return List.of(
                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.what_is.title")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.what_is.body")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.variants.title")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.variants.body")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.variants.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.variants.bullet.2")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.window.title")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.window.body")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.window.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.window.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.window.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.window.bullet.4")
                )),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.window.note")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.linking.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.linking.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.linking.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.linking.bullet.3")
                )),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.linking.note")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.redstone_draw.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.redstone_draw.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.redstone_draw.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.redstone_draw.bullet.3")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.selecting.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.selecting.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.selecting.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.selecting.bullet.3")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.main_actions.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.4"),
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.5"),
                        Component.translatable("guide.mtgcard.deck_control.main_actions.bullet.6")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.peek_overlay.title")),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.peek_overlay.body")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.bullet.4")
                )),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.scry_surveil_overlay.tip")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.place_selected.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.place_selected.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.place_selected.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.place_selected.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.place_selected.bullet.4"),
                        Component.translatable("guide.mtgcard.deck_control.place_selected.bullet.5")
                )),
                new GuideSection.Paragraph(Component.translatable("guide.mtgcard.deck_control.place_selected.note")),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.cascade.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.cascade.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.cascade.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.cascade.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.cascade.bullet.4"),
                        Component.translatable("guide.mtgcard.deck_control.cascade.bullet.5")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.gy_reset.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.gy_reset.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.gy_reset.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.gy_reset.bullet.3"),
                        Component.translatable("guide.mtgcard.deck_control.gy_reset.bullet.4")
                )),

                new GuideSection.Heading(Component.translatable("guide.mtgcard.deck_control.notes.title")),
                new GuideSection.Bullets(List.of(
                        Component.translatable("guide.mtgcard.deck_control.notes.bullet.1"),
                        Component.translatable("guide.mtgcard.deck_control.notes.bullet.2"),
                        Component.translatable("guide.mtgcard.deck_control.notes.bullet.3")
                ))
        );
    }
}
