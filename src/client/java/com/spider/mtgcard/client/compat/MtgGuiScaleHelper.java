package com.spider.mtgcard.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class MtgGuiScaleHelper {
    public static final int BLOCK_GUI_SCALE = 5;

    private static final Map<Screen, Integer> ORIGINAL_GUI_SCALES = new IdentityHashMap<>();
    private static final Set<Screen> RESTORING_SCREENS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public static boolean applyPreferredGuiScale(Screen screen, int preferredScale) {
        if (screen == null || preferredScale <= 0) {
            return false;
        }

        if (RESTORING_SCREENS.contains(screen)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return false;
        }

        int currentScale = minecraft.options.guiScale().get();
        if (currentScale == preferredScale) {
            return false;
        }

        ORIGINAL_GUI_SCALES.putIfAbsent(screen, currentScale);
        minecraft.options.guiScale().set(preferredScale);
        minecraft.resizeDisplay();
        return true;
    }

    public static void restoreGuiScale(Screen screen) {
        if (screen == null) {
            return;
        }

        Integer originalScale = ORIGINAL_GUI_SCALES.remove(screen);
        if (originalScale == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return;
        }

        RESTORING_SCREENS.add(screen);
        try {
            if (minecraft.options.guiScale().get() == originalScale) {
                return;
            }

            minecraft.options.guiScale().set(originalScale);
            minecraft.resizeDisplay();
        } finally {
            RESTORING_SCREENS.remove(screen);
        }
    }

    private MtgGuiScaleHelper() {}
}
