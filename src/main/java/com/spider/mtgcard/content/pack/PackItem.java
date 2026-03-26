package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.net.ModPayloads;
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
import net.minecraft.world.level.Level;

import java.util.UUID;

public class PackItem extends Item {
    public PackItem(Properties s){ super(s); }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (!(user instanceof ServerPlayer player)) return InteractionResult.PASS;

        ItemStack stack = user.getItemInHand(hand);

        // ---- NEW: lock out if already opening ----
        if (PackOpenManager.isActive(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You're already opening a pack."), true);
            return InteractionResult.FAIL;
        }

        // Read desired set BEFORE consuming
        final String desiredSet = PackGenerator.detectPackSetPublic(stack);

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
        ItemStack refundOne = stack.copy();
        refundOne.setCount(1);

        // ---- NEW: acquire the active lock BEFORE decrement/async ----
        if (!PackOpenManager.tryStart(player, refundOne)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You're already opening a pack."), true);
            return InteractionResult.FAIL;
        }

        // Start HUD
        ModPayloads.sendUnpackProgress(player, 0);

        // Consume immediately (unless Creative)
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        ServerLevel sw = (ServerLevel) player.level();
        PackGenerator.openPackAsync(sw.getServer(), player, uid);

        player.playSound(SoundEvents.UI_TOAST_IN, 1f, 1f);
        return InteractionResult.SUCCESS;
    }
}
