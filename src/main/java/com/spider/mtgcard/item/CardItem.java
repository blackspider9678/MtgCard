package com.spider.mtgcard.item;

import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.util.StackData;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class CardItem extends Item {
    public static CardItem INSTANCE;

    public CardItem(Properties settings) {
        super(settings.stacksTo(64));
        INSTANCE = this;
    }

    // --- helpers to unwrap optionals from your NBT API ---
    private static boolean getBool(CompoundTag tag, String key) {
        return tag.getBoolean(key).orElse(false);
    }
    private static String getStr(CompoundTag tag, String key) {
        return tag.getString(key).orElse("");
    }
    private static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key).orElse(0);
    }
    private static CompoundTag getCmp(CompoundTag tag, String key) {
        return tag.getCompound(key).orElseGet(CompoundTag::new);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag root = StackData.readCustom(stack);
        return getBool(root, "mtg_foil") || super.isFoil(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom != null) return custom;

        // ✅ Use the same normalized reader as the rest of your codebase
        CompoundTag root = StackData.readCustom(stack);
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        String name = meta.getString("name").orElse("");
        if (!name.isEmpty()) return Component.literal(name);

        return super.getName(stack);
    }

    /*
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent,
                                Consumer<Component> textConsumer, TooltipFlag type) {

        CompoundTag root = StackData.readCustom(stack);
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        String name = meta.getString("name").orElse("");
        String set  = meta.getString("set").orElse("");
        String num  = meta.getString("collector_number").orElse("");

        boolean foil = root.getBoolean("mtg_foil").orElse(false);

        if (!set.isEmpty() || !num.isEmpty()) {
            String line = (set.isEmpty() ? "" : set.toUpperCase())
                    + (num.isEmpty() ? "" : " • #" + num)
                    + (foil ? " • Foil" : "");
            textConsumer.accept(Component.literal(line));
        }
    }
    */

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var player = ctx.getPlayer();
        var world = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;

        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();

        BlockPos attachment = clicked.relative(face); // ✅ air block in front of the face

        // require replaceable at attachment for ALL directions (including up/down)
        if (!world.getBlockState(attachment).canBeReplaced(
                new BlockPlaceContext(player, ctx.getHand(), ctx.getItemInHand(),
                        new net.minecraft.world.phys.BlockHitResult(ctx.getClickLocation(), face, clicked, false)
                )
        )) {
            return InteractionResult.FAIL;
        }

        CardDisplayEntity frame = new CardDisplayEntity(world, attachment, face);
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(player);
        }

        ItemStack held = ctx.getItemInHand();
        ItemStack one = held.copy();
        one.setCount(1);
        frame.setStack(one);

        boolean ok = world.addFreshEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] spawn ok? {} at {} facing {}", ok, frame.blockPosition(), face);
        if (!ok) return InteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        return InteractionResult.CONSUME;
    }


    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // Normal click: open large view (client only)
        if (!user.isShiftKeyDown()) {
            if (world.isClientSide()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", Player.class, InteractionHand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return InteractionResult.SUCCESS;
        }

        // Sneak + right-click:
        // - If NOT pointing at a block (MISS), open large view instead of trying to place.
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

        // If client and we hit a block, just say success (server will actually place)
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
            return InteractionResult.PASS; // safety
        }

        BlockPos clicked = bhr.getBlockPos();
        Direction face = bhr.getDirection();

        BlockPos attachment = clicked.relative(face);

        if (!world.getBlockState(attachment).canBeReplaced(new BlockPlaceContext(user, hand, stack, bhr))) {
            return InteractionResult.FAIL;
        }

        CardDisplayEntity frame = new CardDisplayEntity(world, attachment, face);
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(user);
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        frame.setStack(one);

        boolean ok = world.addFreshEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] raycast spawn ok? {} at {} facing {}", ok, frame.blockPosition(), face);
        if (!ok) return InteractionResult.FAIL;

        if (!user.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.CONSUME;
    }

}
