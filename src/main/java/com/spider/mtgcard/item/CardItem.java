package com.spider.mtgcard.item;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.util.TcgCardMeta;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CardItem extends Item {
    public static CardItem INSTANCE;

    public CardItem(Properties settings) {
        super(settings.stacksTo(64));
        INSTANCE = this;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return TcgCardMeta.read(stack).foil() || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom != null) return custom;

        String name = TcgCardMeta.read(stack).name();
        if (!name.isEmpty()) return Component.literal(name);

        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent,
                                Consumer<Component> textConsumer, TooltipFlag type) {
        TcgCardMeta.Info meta = TcgCardMeta.read(stack);
        String set = meta.set();
        String num = meta.collectorNumber();
        boolean foil = meta.foil();

        if (!set.isEmpty() || !num.isEmpty()) {
            String line = (set.isEmpty() ? "" : set.toUpperCase())
                    + (num.isEmpty() ? "" : " \u2022 #" + num)
                    + (foil ? " \u2022 Foil" : "");
            textConsumer.accept(Component.literal(line));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level world = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;

        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), face, clicked, false);

        return placeDisplay(world, player, ctx.getHand(), ctx.getItemInHand(), clicked, face, ctx.getClickLocation(), hit);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (!user.isShiftKeyDown()) {
            if (world.isClientSide()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", Player.class, InteractionHand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return InteractionResult.SUCCESS;
        }

        var hit = getPlayerPOVHitResult(world, user, net.minecraft.world.level.ClipContext.Fluid.NONE);

        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            if (world.isClientSide()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", Player.class, InteractionHand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return InteractionResult.SUCCESS;
        }

        if (world.isClientSide()) return InteractionResult.SUCCESS;

        if (!(hit instanceof BlockHitResult bhr)) {
            return InteractionResult.PASS;
        }

        return placeDisplay(world, user, hand, stack, bhr.getBlockPos(), bhr.getDirection(), bhr.getLocation(), bhr);
    }

    private InteractionResult placeDisplay(Level world, Player player, InteractionHand hand, ItemStack held,
                                           BlockPos clicked, Direction face, Vec3 hitLocation, BlockHitResult hit) {
        CardDisplayEntity frame = createDisplayEntity(world, player, hand, held, clicked, face, hitLocation, hit);
        if (frame == null) {
            return InteractionResult.FAIL;
        }

        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(player);
        }

        ItemStack one = held.copy();
        one.setCount(1);
        frame.setStack(one);

        if (!frame.survives()) {
            return InteractionResult.FAIL;
        }

        boolean ok = world.addFreshEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] spawn ok? {} at {} facing {}", ok, frame.blockPosition(), face);
        if (!ok) return InteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    private CardDisplayEntity createDisplayEntity(Level world, Player player, InteractionHand hand, ItemStack held,
                                                  BlockPos clicked, Direction face, Vec3 hitLocation, BlockHitResult hit) {
        OptionalDouble surface = CardDisplayEntity.findSurfaceOffset(world, clicked, face, hitLocation);
        if (surface.isEmpty()) {
            return null;
        }

        return new CardDisplayEntity(
                world,
                clicked,
                face,
                surface.getAsDouble(),
                hitLocation.x - clicked.getX(),
                hitLocation.y - clicked.getY(),
                hitLocation.z - clicked.getZ()
        );
    }
}
