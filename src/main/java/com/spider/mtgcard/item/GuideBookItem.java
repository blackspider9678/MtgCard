package com.spider.mtgcard.item;

import com.spider.mtgcard.net.GuideBookPackets;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

public class GuideBookItem extends Item {
    public GuideBookItem(Properties settings) { super(settings); }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide() && user instanceof ServerPlayer sp) {
            GuideBookPackets.sendOpen(sp);
        }
        return InteractionResult.SUCCESS;
    }
}
