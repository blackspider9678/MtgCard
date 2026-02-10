package com.spider.mtgcard.item;

import com.spider.mtgcard.registry.ModBlocks;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.util.StackData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.function.Consumer;

public class CardItem extends Item {
    public static CardItem INSTANCE;

    public CardItem(Settings settings) {
        super(settings.maxCount(64));
        INSTANCE = this;
    }

    // --- helpers to unwrap optionals from your NBT API ---
    private static boolean getBool(NbtCompound tag, String key) {
        return tag.getBoolean(key).orElse(false);
    }
    private static String getStr(NbtCompound tag, String key) {
        return tag.getString(key).orElse("");
    }
    private static int getInt(NbtCompound tag, String key) {
        return tag.getInt(key).orElse(0);
    }
    private static NbtCompound getCmp(NbtCompound tag, String key) {
        return tag.getCompound(key).orElseGet(NbtCompound::new);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        NbtCompound root = StackData.readCustom(stack);
        return getBool(root, "mtg_foil") || super.hasGlint(stack);
    }

    @Override
    public Text getName(ItemStack stack) {
        var custom = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        if (custom != null) return custom;

        // ✅ Use the same normalized reader as the rest of your codebase
        NbtCompound root = StackData.readCustom(stack);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        String name = meta.getString("name").orElse("");
        if (!name.isEmpty()) return Text.literal(name);

        return super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
                              Consumer<Text> textConsumer, TooltipType type) {

        NbtCompound root = StackData.readCustom(stack);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        String name = meta.getString("name").orElse("");
        String set  = meta.getString("set").orElse("");
        String num  = meta.getString("collector_number").orElse("");

        boolean foil = root.getBoolean("mtg_foil").orElse(false); // ✅ correct location

        if (!name.isEmpty()) textConsumer.accept(Text.literal(name));
        if (!set.isEmpty() || !num.isEmpty()) {
            String line = (set.isEmpty() ? "" : set.toUpperCase())
                    + (num.isEmpty() ? "" : " • #" + num)
                    + (foil ? " • Foil" : "");
            textConsumer.accept(Text.literal(line));
        }
    }


    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        var player = ctx.getPlayer();
        var world = ctx.getWorld();
        if (player == null) return ActionResult.PASS;

        if (!player.isSneaking()) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.SUCCESS;

        BlockPos clicked = ctx.getBlockPos();
        Direction face = ctx.getSide();

        BlockPos attachment = clicked.offset(face); // ✅ air block in front of the face

        // require replaceable at attachment for ALL directions (including up/down)
        if (!world.getBlockState(attachment).canReplace(
                new ItemPlacementContext(player, ctx.getHand(), ctx.getStack(),
                        new net.minecraft.util.hit.BlockHitResult(ctx.getHitPos(), face, clicked, false)
                )
        )) {
            return ActionResult.FAIL;
        }

        CardDisplayEntity frame = new CardDisplayEntity(world, attachment, face);
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(player);
        }

        ItemStack held = ctx.getStack();
        ItemStack one = held.copy();
        one.setCount(1);
        frame.setStack(one);

        boolean ok = world.spawnEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] spawn ok? {} at {} facing {}", ok, frame.getBlockPos(), face);
        if (!ok) return ActionResult.FAIL;

        if (!player.getAbilities().creativeMode) {
            held.decrement(1);
        }

        return ActionResult.CONSUME;
    }


    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Normal click: open large view (client only)
        if (!user.isSneaking()) {
            if (world.isClient()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", PlayerEntity.class, Hand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return ActionResult.SUCCESS;
        }

        // Sneak + right-click:
        // - If NOT pointing at a block (MISS), open large view instead of trying to place.
        var hit = raycast(world, user, net.minecraft.world.RaycastContext.FluidHandling.NONE);

        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
            if (world.isClient()) {
                try {
                    Class<?> c = Class.forName("com.spider.mtgcard.client.CardClientHooks");
                    c.getMethod("openLargeViewFromHand", PlayerEntity.class, Hand.class).invoke(null, user, hand);
                } catch (Throwable ignored) {}
            }
            return ActionResult.SUCCESS;
        }

        // If client and we hit a block, just say success (server will actually place)
        if (world.isClient()) return ActionResult.SUCCESS;

        if (!(hit instanceof net.minecraft.util.hit.BlockHitResult bhr)) {
            return ActionResult.PASS; // safety
        }

        BlockPos clicked = bhr.getBlockPos();
        Direction face = bhr.getSide();

        BlockPos attachment = clicked.offset(face);

        if (!world.getBlockState(attachment).canReplace(new ItemPlacementContext(user, hand, stack, bhr))) {
            return ActionResult.FAIL;
        }

        CardDisplayEntity frame = new CardDisplayEntity(world, attachment, face);
        if (face == Direction.UP || face == Direction.DOWN) {
            frame.setFlatYawTowardPlayer(user);
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        frame.setStack(one);

        boolean ok = world.spawnEntity(frame);
        Mtgcard.LOGGER.info("[CardDisplay] raycast spawn ok? {} at {} facing {}", ok, frame.getBlockPos(), face);
        if (!ok) return ActionResult.FAIL;

        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        return ActionResult.CONSUME;
    }

}
