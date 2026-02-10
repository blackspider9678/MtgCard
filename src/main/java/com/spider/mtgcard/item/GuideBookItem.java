package com.spider.mtgcard.item;

import com.spider.mtgcard.net.GuideBookPackets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class GuideBookItem extends Item {
    public GuideBookItem(Settings settings) { super(settings); }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
            GuideBookPackets.sendOpen(sp);
        }
        return ActionResult.SUCCESS;
    }
}
