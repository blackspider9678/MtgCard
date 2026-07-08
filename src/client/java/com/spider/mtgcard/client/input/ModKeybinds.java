package com.spider.mtgcard.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;

public final class ModKeybinds {
    private ModKeybinds() {}

    // Translation key for your category name (lang file)
    private static final String CATEGORY_KEY = "key.categories.mtgcard";

    public static KeyMapping TOGGLE_CARD_PEEK;
    public static KeyMapping FLIP_CARD_FACE;

    // Session-only toggle (resets on game restart)
    private static boolean cardPeekEnabled = false;

    public static void init() {
        KeyMapping.Category cat = getOrCreateCategory();

        // IMPORTANT: your constructor order is:
        // (translationKey, InputUtil.Type, keyCode, Category)
        TOGGLE_CARD_PEEK = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mtgcard.toggle_card_peek",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                cat
        ));

        FLIP_CARD_FACE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mtgcard.flip_card_face",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                cat
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::tick);
    }

    private static KeyMapping.Category getOrCreateCategory() {
        // Fallback that always exists
        KeyMapping.Category fallback = KeyMapping.Category.MISC;

        try {
            // Create a stable identifier for the category
            Identifier id = Identifier.fromNamespaceAndPath("mtgcard", "mtgcard");

            // KeyBinding.Category.create(Identifier) exists but is private in your mappings
            Method m = KeyMapping.Category.class.getDeclaredMethod("create", Identifier.class);
            m.setAccessible(true);
            Object out = m.invoke(null, id);

            if (out instanceof KeyMapping.Category c) {
                return c;
            }
        } catch (Throwable ignored) {}

        return fallback;
    }

    private static void tick(Minecraft client) {
        while (TOGGLE_CARD_PEEK.consumeClick()) {
            cardPeekEnabled = !cardPeekEnabled;

            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Card Peek: " + (cardPeekEnabled ? "ON" : "OFF")),
                        true
                );
            }
        }
        while (FLIP_CARD_FACE.consumeClick()) {
            if (client.player == null) continue;

            if (client.screen != null) {
                if (GuiCardFaceFlipper.tryFlip(client)) {
                    continue;
                }
                continue;
            }

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
        var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.ListTag list && list.size() >= 2;
    }

    public static boolean isCardPeekEnabled() {
        return cardPeekEnabled;
    }
}
