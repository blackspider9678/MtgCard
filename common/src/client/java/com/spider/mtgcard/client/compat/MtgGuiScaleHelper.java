package com.spider.mtgcard.client.compat;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class MtgGuiScaleHelper {
    public static final int BLOCK_GUI_SCALE = 5;
    public static final int SIDE_PREVIEW_GAP = 12;
    public static final int SIDE_PREVIEW_MARGIN = 8;

    private static final Map<Screen, Integer> ORIGINAL_GUI_SCALES = new IdentityHashMap<>();
    private static final Set<Screen> RESTORING_SCREENS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public static boolean applyPreferredGuiScale(Screen screen, int preferredScale) {
        return applyGuiScale(screen, preferredScale);
    }

    public static boolean applyFixedGuiScale(Screen screen, int fixedScale) {
        return applyGuiScale(screen, fixedScale);
    }

    public static boolean applyAutoFitGuiScale(Screen screen, int preferredScale, int guiWidth, int guiHeight) {
        if (screen == null || preferredScale <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return false;
        }

        Window window = minecraft.getWindow();
        if (window == null) {
            return false;
        }

        boolean forceUnicode = minecraft.isEnforceUnicode();
        int targetScale = resolveAutoFitScale(window, forceUnicode, preferredScale, guiWidth, guiHeight);
        return applyGuiScale(screen, targetScale);
    }

    public static boolean applyAutoFitGuiScaleWithSidePreview(
            Screen screen,
            int preferredScale,
            int guiWidth,
            int guiHeight,
            int previewWidth,
            int previewHeight
    ) {
        if (previewWidth <= 0 || previewHeight <= 0) {
            return applyAutoFitGuiScale(screen, preferredScale, guiWidth, guiHeight);
        }

        int requiredWidth = guiWidth + 2 * (previewWidth + SIDE_PREVIEW_GAP + SIDE_PREVIEW_MARGIN);
        int requiredHeight = Math.max(guiHeight, previewHeight + SIDE_PREVIEW_MARGIN * 2);
        return applyAutoFitGuiScale(screen, preferredScale, requiredWidth, requiredHeight);
    }

    private static boolean applyGuiScale(Screen screen, int requestedScale) {
        if (screen == null || requestedScale <= 0) {
            return false;
        }

        if (RESTORING_SCREENS.contains(screen)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return false;
        }

        Window window = minecraft.getWindow();
        if (window == null) {
            return false;
        }

        int currentScale = minecraft.options.guiScale().get();
        boolean forceUnicode = minecraft.isEnforceUnicode();
        int resolvedCurrentScale = window.calculateScale(currentScale, forceUnicode);
        int resolvedPreferredScale = window.calculateScale(requestedScale, forceUnicode);
        if (resolvedCurrentScale == resolvedPreferredScale) {
            return false;
        }

        ORIGINAL_GUI_SCALES.putIfAbsent(screen, currentScale);
        minecraft.options.guiScale().set(resolvedPreferredScale);
        minecraft.resizeGui();
        return true;
    }

    private static int resolveAutoFitScale(Window window, boolean forceUnicode, int preferredScale, int guiWidth, int guiHeight) {
        int maxCandidateScale = Math.max(1, window.calculateScale(preferredScale, forceUnicode));
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();

        for (int scale = maxCandidateScale; scale >= 1; scale--) {
            if (window.calculateScale(scale, forceUnicode) != scale) {
                continue;
            }

            if ((windowWidth / scale) >= guiWidth && (windowHeight / scale) >= guiHeight) {
                return scale;
            }
        }

        return 1;
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
            minecraft.resizeGui();
        } finally {
            RESTORING_SCREENS.remove(screen);
        }
    }

    private MtgGuiScaleHelper() {}
}
