package com.spider.mtgcard.client.tooltips;

import com.spider.mtgcard.client.input.ModKeybinds;
import com.spider.mtgcard.item.CardItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class CardTooltipHints {
    private CardTooltipHints() {}

    public static void init() {
        ItemTooltipCallback.EVENT.register(CardTooltipHints::onTooltip);
    }

    // NOTE: Fabric API for your version uses 4 params:
    // (ItemStack, TooltipContext, TooltipType, List<Text>)
    private static void onTooltip(ItemStack stack, Object context, TooltipType type, List<Text> lines) {
        if (!(stack.getItem() instanceof CardItem)) return;

        // --- Peek hint (F7) ---
        KeyBinding peek = ModKeybinds.TOGGLE_CARD_PEEK;
        Text peekKey = (peek != null) ? peek.getBoundKeyLocalizedText() : Text.literal("F7");

        lines.add(Text.translatable("tooltip.mtgcard.peek_hint", peekKey)
                .formatted(Formatting.GRAY, Formatting.ITALIC));

        // --- Flip hint (F8) only if double-faced ---
        if (isDoubleFaced(stack)) {
            KeyBinding flip = ModKeybinds.FLIP_CARD_FACE;
            Text flipKey = (flip != null) ? flip.getBoundKeyLocalizedText() : Text.literal("F8");

            lines.add(Text.translatable("tooltip.mtgcard.flip_hint", flipKey)
                    .formatted(Formatting.GRAY, Formatting.ITALIC));
        }
    }

    private static boolean isDoubleFaced(ItemStack st) {
        NbtCompound meta = getMeta(st);
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.NbtList list && list.size() >= 2;
    }

    private static NbtCompound getMeta(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        return root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
    }
}