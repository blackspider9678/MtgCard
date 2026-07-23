package com.spider.mtgcard.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

public class DiceItem extends Item {
    private static final double ANNOUNCE_RANGE = 50.0;
    private static final double ANNOUNCE_RANGE_SQ = ANNOUNCE_RANGE * ANNOUNCE_RANGE;

    private final int sides;

    public DiceItem(Properties settings, int sides) {
        super(settings);
        this.sides = sides;
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(world instanceof ServerLevel sw) || !(user instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = user.getItemInHand(hand);
        int rolls = stack.getCount();
        boolean detailed = sp.isShiftKeyDown();

        int total = 0;

        // Build optional [r1, r2, ...]
        MutableComponent detailList = Component.literal("[");
        for (int i = 0; i < rolls; i++) {
            int r = sw.getRandom().nextInt(sides) + 1;
            total += r;

            if (detailed) {
                if (i > 0) detailList.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                detailList.append(Component.literal(String.valueOf(r)).withStyle(ChatFormatting.WHITE));
            }
        }
        if (detailed) detailList.append(Component.literal("]"));

        MutableComponent msg = Component.literal("🎲 ")
                .append(sp.getDisplayName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" rolled ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(rolls + "d" + sides).withStyle(ChatFormatting.WHITE));

        if (detailed) {
            msg.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(detailList.withStyle(ChatFormatting.DARK_GRAY));
        }

        msg.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.GOLD));

        // Send to players within 50 blocks (same dimension)
        for (ServerPlayer other : sw.getPlayers(p -> p.distanceToSqr(sp) <= ANNOUNCE_RANGE_SQ)) {
            other.sendSystemMessage(msg, false);
        }

        return InteractionResult.SUCCESS;
    }
}