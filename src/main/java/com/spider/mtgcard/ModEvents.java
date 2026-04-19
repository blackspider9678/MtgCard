package com.spider.mtgcard;

public final class ModEvents {
    private ModEvents() {
    }

    public static void register() {
        // Intentionally empty. Card database interaction is handled by the block class,
        // so sneak-place and item use can follow the normal Minecraft interaction flow.
    }
}
