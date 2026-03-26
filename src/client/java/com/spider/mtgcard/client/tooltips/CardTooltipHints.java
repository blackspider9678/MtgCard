package com.spider.mtgcard.client.tooltips;

import com.spider.mtgcard.client.input.ModKeybinds;
import com.spider.mtgcard.item.CardItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

public final class CardTooltipHints {
    private CardTooltipHints() {}

    public static void init() {
        ItemTooltipCallback.EVENT.register(CardTooltipHints::onTooltip);
    }

    // NOTE: Fabric API for your version uses 4 params:
    // (ItemStack, TooltipContext, TooltipType, List<Text>)
    private static void onTooltip(ItemStack stack, Object context, TooltipFlag type, List<Component> lines) {
        if (!(stack.getItem() instanceof CardItem)) return;

        // --- Peek hint (F7) ---
        KeyMapping peek = ModKeybinds.TOGGLE_CARD_PEEK;
        Component peekKey = (peek != null) ? peek.getTranslatedKeyMessage() : Component.literal("F7");

        lines.add(Component.translatable("tooltip.mtgcard.peek_hint", peekKey)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        // --- Flip hint (F8) only if double-faced ---
        if (isDoubleFaced(stack)) {
            KeyMapping flip = ModKeybinds.FLIP_CARD_FACE;
            Component flipKey = (flip != null) ? flip.getTranslatedKeyMessage() : Component.literal("F8");

            lines.add(Component.translatable("tooltip.mtgcard.flip_hint", flipKey)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static boolean isDoubleFaced(ItemStack st) {
        CompoundTag meta = getMeta(st);
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.ListTag list && list.size() >= 2;
    }

    private static CompoundTag getMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }
}