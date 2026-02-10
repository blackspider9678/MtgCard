package com.spider.mtgcard.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class DiceItem extends Item {
    private static final double ANNOUNCE_RANGE = 50.0;
    private static final double ANNOUNCE_RANGE_SQ = ANNOUNCE_RANGE * ANNOUNCE_RANGE;

    private final int sides;

    public DiceItem(Settings settings, int sides) {
        super(settings);
        this.sides = sides;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) {
            return ActionResult.SUCCESS;
        }

        ItemStack stack = user.getStackInHand(hand);
        int rolls = stack.getCount();
        boolean detailed = sp.isSneaking();

        int total = 0;

        // Build optional [r1, r2, ...]
        MutableText detailList = Text.literal("[");
        for (int i = 0; i < rolls; i++) {
            int r = sw.random.nextInt(sides) + 1;
            total += r;

            if (detailed) {
                if (i > 0) detailList.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                detailList.append(Text.literal(String.valueOf(r)).formatted(Formatting.WHITE));
            }
        }
        if (detailed) detailList.append(Text.literal("]"));

        MutableText msg = Text.literal("🎲 ")
                .append(sp.getDisplayName().copy().formatted(Formatting.AQUA))
                .append(Text.literal(" rolled ").formatted(Formatting.GRAY))
                .append(Text.literal(rolls + "d" + sides).formatted(Formatting.WHITE));

        if (detailed) {
            msg.append(Text.literal(": ").formatted(Formatting.GRAY))
                    .append(detailList.formatted(Formatting.DARK_GRAY));
        }

        msg.append(Text.literal(" → ").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(total)).formatted(Formatting.GOLD));

        // Send to players within 50 blocks (same dimension)
        for (ServerPlayerEntity other : sw.getPlayers(p -> p.squaredDistanceTo(sp) <= ANNOUNCE_RANGE_SQ)) {
            other.sendMessage(msg, false);
        }

        return ActionResult.SUCCESS;
    }
}