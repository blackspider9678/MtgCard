package com.spider.mtgcard.item;

import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.util.StackData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class CardItem extends Item {
    public static CardItem INSTANCE;

    public CardItem(Properties settings) {
        super(settings.stacksTo(64));
        INSTANCE = this;
    }

    // --- helpers to unwrap optionals from your NBT API ---
    private static boolean getBool(CompoundTag tag, String key) {
        return tag.getBoolean(key);
    }
    private static String getStr(CompoundTag tag, String key) {
        return tag.getString(key);
    }
    private static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key);
    }
    private static CompoundTag getCmp(CompoundTag tag, String key) {
        return tag.getCompound(key);
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
        CompoundTag meta = root.getCompound("mtg_meta");

        String name = meta.getString("name");
        if (!name.isEmpty()) return Component.literal(name);

        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {

        CompoundTag root = StackData.readCustom(stack);
        CompoundTag meta = root.getCompound("mtg_meta");

        String name = meta.getString("name");
        String set  = meta.getString("set");
        String num  = meta.getString("collector_number");

        boolean foil = root.getBoolean("mtg_foil");

        if (!set.isEmpty() || !num.isEmpty()) {
            String line = (set.isEmpty() ? "" : set.toUpperCase())
                    + (num.isEmpty() ? "" : " • #" + num)
                    + (foil ? " • Foil" : "");
            tooltip.add(Component.literal(line));
        }
    }


    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var player = ctx.getPlayer();
        var world = ctx.getLevel();
        if (player == null) return InteractionResult.PASS;

        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();

        var hitLocation = ctx.getClickLocation();
        var surface = CardDisplayEntity.findSurfaceOffset(world, clicked, face, hitLocation);
        if (surface.isEmpty()) return InteractionResult.FAIL;
        CardDisplayEntity frame = new CardDisplayEntity(world, clicked, face, surface.getAsDouble(),
                hitLocation.x - clicked.getX(), hitLocation.y - clicked.getY(), hitLocation.z - clicked.getZ());
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(player);
        }

        ItemStack held = ctx.getItemInHand();
        ItemStack one = held.copy();
        one.setCount(1);
        frame.setStack(one);

        if (!frame.survives()) return InteractionResult.FAIL;
        boolean ok = world.addFreshEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] spawn ok? {} at {} facing {}", ok, frame.blockPosition(), face);
        if (!ok) return InteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        return InteractionResult.CONSUME;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // Normal click: open large view (client only)
        if (!user.isShiftKeyDown()) {
            if (world.isClientSide()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", Player.class, InteractionHand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return InteractionResultHolder.success(stack);
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
            return InteractionResultHolder.success(stack);
        }

        // If client and we hit a block, just say success (server will actually place)
        if (world.isClientSide()) return InteractionResultHolder.success(stack);

        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bhr)) {
            return InteractionResultHolder.pass(stack); // safety
        }

        BlockPos clicked = bhr.getBlockPos();
        Direction face = bhr.getDirection();

        var hitLocation = bhr.getLocation();
        var surface = CardDisplayEntity.findSurfaceOffset(world, clicked, face, hitLocation);
        if (surface.isEmpty()) return InteractionResultHolder.fail(stack);
        CardDisplayEntity frame = new CardDisplayEntity(world, clicked, face, surface.getAsDouble(),
                hitLocation.x - clicked.getX(), hitLocation.y - clicked.getY(), hitLocation.z - clicked.getZ());
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(user);
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        frame.setStack(one);

        if (!frame.survives()) return InteractionResultHolder.fail(stack);
        boolean ok = world.addFreshEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] raycast spawn ok? {} at {} facing {}", ok, frame.blockPosition(), face);
        if (!ok) return InteractionResultHolder.fail(stack);

        if (!user.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.consume(stack);
    }

}
