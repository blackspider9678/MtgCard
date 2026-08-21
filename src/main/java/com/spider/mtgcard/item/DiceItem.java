package com.spider.mtgcard.item;

import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.dice.DiceAppearance;
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
import com.spider.mtgcard.dice.DiceEntity;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.Direction;

public class DiceItem extends Item {
    private static final double ANNOUNCE_RANGE = 50.0;
    private static final double ANNOUNCE_RANGE_SQ = ANNOUNCE_RANGE * ANNOUNCE_RANGE;

    private final int sides;

    public DiceItem(Properties settings, int sides) {
        super(settings);
        this.sides = sides;
    }

    public int getSides() {
        return sides;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        DiceAppearance appearance = stack.get(ModDataComponents.DICE_APPEARANCE);
        return (appearance != null && appearance.foil()) || super.isFoil(stack);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(world instanceof ServerLevel sw) || !(user instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = user.getItemInHand(hand);
        if (sides != 4 && sides != 6 && sides != 8 && sides != 10 && sides != 12 && sides != 20 && sides != 100) return InteractionResult.PASS;

        int count = stack.getCount();
        // Placing a single die is always available. The config only selects whether a normal
        // use performs an instant hand-held roll or throws physical dice.
        if (sp.isShiftKeyDown()) {
            ItemStack single = stack.copyWithCount(1);
            DiceEntity die = new DiceEntity(sw, single, sp.getDisplayName(), sides);
            HitResult target = sp.pick(5.0, 0.0f, false);
            if (target instanceof BlockHitResult blockHit) {
                var location = blockHit.getLocation();
                if (blockHit.getDirection() == Direction.UP) {
                    var ground = blockHit.getBlockPos();
                    die.setPos(ground.getX() + 0.5, ground.getY() + 1.001, ground.getZ() + 0.5);
                } else {
                    var placement = blockHit.getBlockPos().relative(blockHit.getDirection());
                    die.setPos(placement.getX() + 0.5, placement.getY(), placement.getZ() + 0.5);
                }
            } else {
                die.setPos(sp.getX(), sp.getY(), sp.getZ());
            }
            die.place(Math.round((sp.getYRot() + 180f) / 90f) * 90f);
            sw.addFreshEntity(die);
            if (!sp.hasInfiniteMaterials()) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }

        if (!MtgcardConfig.get().Physical_Dice_Enabled) {
            return instantRoll(sw, sp, stack);
        }

        for (int i = 0; i < count; i++) {
            ItemStack single = stack.copyWithCount(1);
            DiceEntity die = new DiceEntity(sw, single, sp.getDisplayName(), sides);
            var look = sp.getLookAngle().normalize();
            var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x).normalize();
            double lane = (i - (count - 1) * 0.5);
            double spawnSpread = Math.clamp(lane * 0.035, -0.22, 0.22);
            die.setPos(sp.getX() + look.x * 0.72 + right.x * spawnSpread,
                    sp.getEyeY() - 0.25 + look.y * 0.72,
                    sp.getZ() + look.z * 0.72 + right.z * spawnSpread);
            double velocitySpread = Math.clamp(lane * 0.012, -0.075, 0.075);
            die.setDeltaMovement(look.scale(0.44).add(sp.getDeltaMovement()).add(right.scale(velocitySpread))
                    .add(0, 0.025 + sw.random.nextDouble() * 0.025, 0));
            sw.addFreshEntity(die);
        }
        if (!sp.hasInfiniteMaterials()) stack.shrink(count);

        return InteractionResult.SUCCESS;
    }

    private InteractionResult instantRoll(ServerLevel world, ServerPlayer player, ItemStack stack) {
        int rolls = stack.getCount();
        boolean detailed = player.isShiftKeyDown();
        int total = 0;
        MutableComponent details = Component.literal("[");
        for (int i = 0; i < rolls; i++) {
            int result = world.random.nextInt(sides) + 1;
            total += result;
            if (detailed) {
                if (i > 0) details.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                details.append(Component.literal(Integer.toString(result)).withStyle(ChatFormatting.WHITE));
            }
        }
        if (detailed) details.append(Component.literal("]"));
        MutableComponent message = Component.literal("🎲 ")
                .append(player.getDisplayName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" rolled ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(rolls + "d" + sides).withStyle(ChatFormatting.WHITE));
        if (detailed) message.append(Component.literal(": ").withStyle(ChatFormatting.GRAY)).append(details);
        message.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(total)).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer other : world.getPlayers(p -> p.distanceToSqr(player) <= ANNOUNCE_RANGE_SQ)) {
            other.sendSystemMessage(message, false);
        }
        return InteractionResult.SUCCESS;
    }
}
