package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class PackItem extends Item {
    public PackItem(Properties s){ super(s); }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;

        ItemStack stack = user.getItemInHand(hand);
        final String desiredSet = PackGenerator.detectPackSetPublic(stack);

        // ---- NEW: lock out if already opening ----
        if (PackOpenManager.isActive(player)) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Pack use blocked because player already has an active opening player={} hand={} desiredSet={} held={}",
                    player.getName().getString(),
                    hand,
                    desiredSet == null ? "random" : desiredSet,
                    PackInventoryUtil.describeStack(stack)
            );
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You're already opening a pack."), true);
            return InteractionResult.FAIL;
        }

        // Stamp a UID (kept for correlation/logging)
        var comp = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = comp.copyTag();
        CompoundTag tag  = root.getCompound("mtg_pack").orElseGet(CompoundTag::new);

        String uid = tag.getString("uid").orElse("");
        if (uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            tag.putString("uid", uid);
            root.put("mtg_pack", tag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }

        // ---- NEW: snapshot EXACT pack for refund (count=1, includes custom name, custom data, etc.) ----
        ItemStack refundOne = player.isCreative() ? ItemStack.EMPTY : stack.copy();
        if (!refundOne.isEmpty()) {
            refundOne.setCount(1);
        }

        int preferredReturnSlot = hand == InteractionHand.OFF_HAND
                ? Inventory.SLOT_OFFHAND
                : player.getInventory().getSelectedSlot();

        // ---- NEW: acquire the active lock BEFORE decrement/async ----
        if (!PackOpenManager.tryStart(player, uid, refundOne, preferredReturnSlot)) {
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Pack use lost active-lock race player={} uid={} desiredSet={} hand={} preferredSlot={} held={}",
                    player.getName().getString(),
                    uid,
                    desiredSet == null ? "random" : desiredSet,
                    hand,
                    preferredReturnSlot,
                    PackInventoryUtil.describeStack(stack)
            );
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You're already opening a pack."), true);
            return InteractionResult.FAIL;
        }

        Mtgcard.LOGGER.info(
                "[MTGCard][PackDebug] Pack use accepted player={} uid={} desiredSet={} hand={} preferredSlot={} creative={} heldBefore={} refund={} inventory={}",
                player.getName().getString(),
                uid,
                desiredSet == null ? "random" : desiredSet,
                hand,
                preferredReturnSlot,
                player.isCreative(),
                PackInventoryUtil.describeStack(stack),
                PackInventoryUtil.describeStack(refundOne),
                PackInventoryUtil.describeInventoryState(player, preferredReturnSlot)
        );

        // Consume immediately (unless Creative)
        if (!player.isCreative()) {
            stack.shrink(1);
            Mtgcard.LOGGER.info(
                    "[MTGCard][PackDebug] Consumed pack item player={} uid={} remainingInHand={} heldAfter={}",
                    player.getName().getString(),
                    uid,
                    stack.getCount(),
                    PackInventoryUtil.describeStack(stack)
            );
        }

        ServerLevel sw = (ServerLevel) player.level();
        Mtgcard.LOGGER.info(
                "[MTGCard][PackDebug] Dispatching async pack open player={} uid={} set={} world={}",
                player.getName().getString(),
                uid,
                desiredSet == null ? "random" : desiredSet,
                sw.dimension()
        );
        PackGenerator.openPackAsync(sw.getServer(), player, desiredSet);

        player.playSound(SoundEvents.UI_TOAST_IN, 1f, 1f);
        return InteractionResult.SUCCESS;
    }
}
