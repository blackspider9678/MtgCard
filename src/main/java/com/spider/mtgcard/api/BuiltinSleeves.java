package com.spider.mtgcard.api;

import com.spider.mtgcard.Mtgcard;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** MTGcard's built-in sleeves. Add-ons should call {@link SleeveRegistry#register(CardSleeve)}. */
public final class BuiltinSleeves {
    private static final String[] DYE_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        for (int i = 0; i < DYE_COLORS.length; i++) {
            String color = DYE_COLORS[i];
            SleeveRegistry.register(new CardSleeve(
                    id(color), Component.translatable("sleeve.mtgcard." + color),
                    id("textures/sleeve/" + color + ".png"), null, "basic", i
            ));
        }
        SleeveRegistry.register(new CardSleeve(
                id("moons_frog"), Component.translatable("sleeve.mtgcard.moons_frog"),
                id("textures/sleeve/moons_frog.png"), Component.translatable("sleeve.mtgcard.moons_frog.credit"),
                "artist", 1000
        ));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, path);
    }

    private BuiltinSleeves() {}
}
