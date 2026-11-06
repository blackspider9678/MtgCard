package com.spider.mtgcard.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.Arrays;

public final class ModKeybinds {
    private ModKeybinds() {}

    // Translation key for your category name (lang file)
    private static final String CATEGORY_KEY = "key.categories.mtgcard";

    public static KeyMapping TOGGLE_CARD_PEEK;
    public static KeyMapping FLIP_CARD_FACE;

    // Session-only toggle (resets on game restart)
    private static boolean cardPeekEnabled = false;

    public static void init() {
        String cat = getOrCreateCategory();

        TOGGLE_CARD_PEEK = registerKeyMapping(new KeyMapping(
                "key.mtgcard.toggle_card_peek",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                cat
        ));

        FLIP_CARD_FACE = registerKeyMapping(new KeyMapping(
                "key.mtgcard.flip_card_face",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                cat
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::tick);
    }

    private static KeyMapping registerKeyMapping(KeyMapping mapping) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.options == null) return mapping;

            Field field = client.options.getClass().getDeclaredField("keyMappings");
            field.setAccessible(true);
            KeyMapping[] current = (KeyMapping[]) field.get(client.options);
            for (KeyMapping existing : current) {
                if (existing != null && existing.getName().equals(mapping.getName())) {
                    return existing;
                }
            }

            KeyMapping[] next = Arrays.copyOf(current, current.length + 1);
            next[next.length - 1] = mapping;
            field.set(client.options, next);
            KeyMapping.resetMapping();
        } catch (Throwable ignored) {
        }
        return mapping;
    }

    private static String getOrCreateCategory() {
        return CATEGORY_KEY;
    }

    private static void tick(Minecraft client) {
        while (TOGGLE_CARD_PEEK.consumeClick()) {
            cardPeekEnabled = !cardPeekEnabled;

            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("Card Peek: " + (cardPeekEnabled ? "ON" : "OFF")), true);
            }
        }
        while (FLIP_CARD_FACE.consumeClick()) {
            if (client.player == null) continue;

            // Optional: don't flip while a GUI is open
            if (client.screen != null) continue;

            // Prefer main hand card, else offhand card
            var main = client.player.getMainHandItem();
            var off  = client.player.getOffhandItem();

            net.minecraft.world.InteractionHand hand = null;
            if (main.getItem() instanceof com.spider.mtgcard.item.CardItem && isDoubleFacedClient(main)) hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            else if (off.getItem() instanceof com.spider.mtgcard.item.CardItem && isDoubleFacedClient(off)) hand = net.minecraft.world.InteractionHand.OFF_HAND;

            if (hand != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.spider.mtgcard.net.payload.FlipHeldCardFacePayload(hand)
                );
            }
        }
    }

    private static boolean isDoubleFacedClient(net.minecraft.world.item.ItemStack st) {
        var comp = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        var meta = root.getCompound("mtg_meta");
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.ListTag list && list.size() >= 2;
    }

    public static boolean isCardPeekEnabled() {
        return cardPeekEnabled;
    }
}
