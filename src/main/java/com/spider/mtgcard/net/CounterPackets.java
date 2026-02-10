package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.net.payload.SetCounterMetaPayload;
import com.spider.mtgcard.net.payload.SetCounterValuePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public final class CounterPackets {

    public static void registerReceivers() {
        Mtgcard.LOGGER.info("[CounterPackets] registering receivers...");
        ServerPlayNetworking.registerGlobalReceiver(SetCounterValuePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                Hand hand = (payload.hand() == 1) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack st = player.getStackInHand(hand);
                if (st.isEmpty()) return;

                applyCounterValue(st, payload.key(), payload.value());

                // force inventory sync
                player.currentScreenHandler.sendContentUpdates();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCounterMetaPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                Hand hand = (payload.hand() == 1) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                ItemStack st = player.getStackInHand(hand);
                if (st.isEmpty()) return;

                applyCounterMeta(st, payload.key(), payload.displayName(), payload.iconKey());

                // force inventory sync
                player.currentScreenHandler.sendContentUpdates();
            });
        });
    }

    private static void applyCounterValue(ItemStack st, String key, int value) {
        NbtCompound root = getRoot(st);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        NbtCompound counters = meta.getCompound("counters").orElseGet(NbtCompound::new);

        if (value <= 0) counters.remove(key);
        else counters.putInt(key, value);

        meta.put("counters", counters);
        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private static void applyCounterMeta(ItemStack st, String key, String display, String icon) {
        NbtCompound root = getRoot(st);
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        NbtCompound names = meta.getCompound("counter_names").orElseGet(NbtCompound::new);
        if (display == null || display.isBlank()) names.remove(key);
        else names.putString(key, display.trim());

        NbtCompound icons = meta.getCompound("counter_icons").orElseGet(NbtCompound::new);
        if (icon == null || icon.isBlank() || icon.equals("none")) icons.remove(key);
        else icons.putString(key, icon);

        meta.put("counter_names", names);
        meta.put("counter_icons", icons);
        root.put("mtg_meta", meta);

        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private static NbtCompound getRoot(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        return (comp == null) ? new NbtCompound() : comp.copyNbt();
    }

    private CounterPackets() {}
}
