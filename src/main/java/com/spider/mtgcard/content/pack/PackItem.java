package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.net.ModPayloads;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;

public class PackItem extends Item {
    public PackItem(Settings s){ super(s); }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(user instanceof ServerPlayerEntity player)) return ActionResult.PASS;

        ItemStack stack = user.getStackInHand(hand);

        // ---- NEW: lock out if already opening ----
        if (PackOpenManager.isActive(player)) {
            player.sendMessage(net.minecraft.text.Text.literal("You're already opening a pack."), true);
            return ActionResult.FAIL;
        }

        // Read desired set BEFORE consuming
        final String desiredSet = PackGenerator.detectPackSetPublic(stack);

        // Stamp a UID (kept for correlation/logging)
        var comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound root = comp.copyNbt();
        NbtCompound tag  = root.getCompound("mtg_pack").orElseGet(NbtCompound::new);

        String uid = tag.getString("uid").orElse("");
        if (uid.isEmpty()) {
            uid = UUID.randomUUID().toString();
            tag.putString("uid", uid);
            root.put("mtg_pack", tag);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        }

        // ---- NEW: snapshot EXACT pack for refund (count=1, includes custom name, custom data, etc.) ----
        ItemStack refundOne = stack.copy();
        refundOne.setCount(1);

        // ---- NEW: acquire the active lock BEFORE decrement/async ----
        if (!PackOpenManager.tryStart(player, refundOne)) {
            player.sendMessage(net.minecraft.text.Text.literal("You're already opening a pack."), true);
            return ActionResult.FAIL;
        }

        // Start HUD
        ModPayloads.sendUnpackProgress(player, 0);

        // Consume immediately (unless Creative)
        if (!player.isCreative()) {
            stack.decrement(1);
        }

        ServerWorld sw = (ServerWorld) player.getEntityWorld();
        PackGenerator.openPackAsync(sw.getServer(), player, uid);

        player.playSound(SoundEvents.UI_TOAST_IN, 1f, 1f);
        return ActionResult.SUCCESS;
    }
}
