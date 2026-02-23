package com.spider.mtgcard.client.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;

public final class ModKeybinds {
    private ModKeybinds() {}

    // Translation key for your category name (lang file)
    private static final String CATEGORY_KEY = "key.categories.mtgcard";

    public static KeyBinding TOGGLE_CARD_PEEK;
    public static KeyBinding FLIP_CARD_FACE;

    // Session-only toggle (resets on game restart)
    private static boolean cardPeekEnabled = false;

    public static void init() {
        KeyBinding.Category cat = getOrCreateCategory();

        // IMPORTANT: your constructor order is:
        // (translationKey, InputUtil.Type, keyCode, Category)
        TOGGLE_CARD_PEEK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mtgcard.toggle_card_peek",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                cat
        ));

        FLIP_CARD_FACE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mtgcard.flip_card_face",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                cat
        ));

        ClientTickEvents.END_CLIENT_TICK.register(ModKeybinds::tick);
    }

    private static KeyBinding.Category getOrCreateCategory() {
        // Fallback that always exists
        KeyBinding.Category fallback = KeyBinding.Category.MISC;

        try {
            // Create a stable identifier for the category
            Identifier id = Identifier.of("mtgcard", "mtgcard");

            // KeyBinding.Category.create(Identifier) exists but is private in your mappings
            Method m = KeyBinding.Category.class.getDeclaredMethod("create", Identifier.class);
            m.setAccessible(true);
            Object out = m.invoke(null, id);

            if (out instanceof KeyBinding.Category c) {
                return c;
            }
        } catch (Throwable ignored) {}

        return fallback;
    }

    private static void tick(MinecraftClient client) {
        while (TOGGLE_CARD_PEEK.wasPressed()) {
            cardPeekEnabled = !cardPeekEnabled;

            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Card Peek: " + (cardPeekEnabled ? "ON" : "OFF")),
                        true
                );
            }
        }
        while (FLIP_CARD_FACE.wasPressed()) {
            if (client.player == null) continue;

            // Optional: don't flip while a GUI is open
            if (client.currentScreen != null) continue;

            // Prefer main hand card, else offhand card
            var main = client.player.getMainHandStack();
            var off  = client.player.getOffHandStack();

            net.minecraft.util.Hand hand = null;
            if (main.getItem() instanceof com.spider.mtgcard.item.CardItem && isDoubleFacedClient(main)) hand = net.minecraft.util.Hand.MAIN_HAND;
            else if (off.getItem() instanceof com.spider.mtgcard.item.CardItem && isDoubleFacedClient(off)) hand = net.minecraft.util.Hand.OFF_HAND;

            if (hand != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.spider.mtgcard.net.payload.FlipHeldCardFacePayload(hand)
                );
            }
        }
    }

    private static boolean isDoubleFacedClient(net.minecraft.item.ItemStack st) {
        var comp = st.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        net.minecraft.nbt.NbtCompound root = (comp == null) ? new net.minecraft.nbt.NbtCompound() : comp.copyNbt();
        var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.NbtCompound::new);
        var el = meta.get("card_faces");
        return el instanceof net.minecraft.nbt.NbtList list && list.size() >= 2;
    }

    public static boolean isCardPeekEnabled() {
        return cardPeekEnabled;
    }
}