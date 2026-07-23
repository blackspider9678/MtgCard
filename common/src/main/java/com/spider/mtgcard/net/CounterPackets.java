package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.net.payload.SetCounterMetaPayload;
import com.spider.mtgcard.net.payload.SetCounterValuePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class CounterPackets {

    public static void registerReceivers() {
        Mtgcard.LOGGER.info("[CounterPackets] registering receivers...");
        ServerPlayNetworking.registerGlobalReceiver(SetCounterValuePayload.ID, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                InteractionHand hand = (payload.hand() == 1) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack st = player.getItemInHand(hand);
                if (st.isEmpty()) return;

                applyCounterValue(st, payload.key(), payload.value());

                // force inventory sync
                player.containerMenu.broadcastChanges();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCounterMetaPayload.ID, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                InteractionHand hand = (payload.hand() == 1) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack st = player.getItemInHand(hand);
                if (st.isEmpty()) return;

                applyCounterMeta(st, payload.key(), payload.displayName(), payload.iconKey());

                // force inventory sync
                player.containerMenu.broadcastChanges();
            });
        });
    }

    private static void applyCounterValue(ItemStack st, String key, int value) {
        CompoundTag root = getRoot(st);
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        CompoundTag counters = meta.getCompound("counters").orElseGet(CompoundTag::new);

        if (value <= 0) counters.remove(key);
        else counters.putInt(key, value);

        meta.put("counters", counters);
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void applyCounterMeta(ItemStack st, String key, String display, String icon) {
        CompoundTag root = getRoot(st);
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
        if (display == null || display.isBlank()) names.remove(key);
        else names.putString(key, display.trim());

        CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
        if (icon == null || icon.isBlank() || icon.equals("none")) icons.remove(key);
        else icons.putString(key, icon);

        meta.put("counter_names", names);
        meta.put("counter_icons", icons);
        root.put("mtg_meta", meta);

        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static CompoundTag getRoot(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        return (comp == null) ? new CompoundTag() : comp.copyTag();
    }

    private CounterPackets() {}
}
