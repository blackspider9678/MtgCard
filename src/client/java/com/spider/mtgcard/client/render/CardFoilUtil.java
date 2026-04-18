package com.spider.mtgcard.client.render;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class CardFoilUtil {

    public static final int WORLD_SWEEP_ALPHA = 0x40;

    private static final int SWEEP_MIN_PX = 6;
    private static final float SWEEP_WIDTH_RATIO = 0.22f;
    private static final float SWEEP_SPEED = 0.22f;
    private static final int GUI_ALPHA_BASE = 0x42;
    private static final int GUI_ALPHA_RANGE = 0x16;

    public record Sweep(int drawU, int clipW, float u0, float u1) {}

    public static boolean isFoil(ItemStack st) {
        if (st == null || st.isEmpty()) {
            return false;
        }

        Boolean glint = st.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (glint != null && glint) {
            return true;
        }

        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getBoolean("mtg_foil").orElse(false);
    }

    public static Sweep computeSweep(long nowMs, int texW) {
        if (texW <= 0) {
            return null;
        }

        int stripePx = Math.max(SWEEP_MIN_PX, Math.round(texW * SWEEP_WIDTH_RATIO));
        float travel = texW + stripePx * 2f;
        float pos = ((nowMs / 16f) * SWEEP_SPEED) % travel - stripePx;

        int sweepStart = Math.round(pos);
        int visibleStart = Math.max(0, sweepStart);
        int visibleEnd = Math.min(texW, sweepStart + stripePx);
        int clipW = visibleEnd - visibleStart;
        if (clipW <= 0) {
            return null;
        }

        float u0 = visibleStart / (float) texW;
        float u1 = visibleEnd / (float) texW;
        return new Sweep(visibleStart, clipW, u0, u1);
    }

    public static int guiShimmerColor(float ease) {
        int alpha = (int) (GUI_ALPHA_BASE + GUI_ALPHA_RANGE * ease);
        return (alpha << 24) | 0x00FFFFFF;
    }

    private CardFoilUtil() {}
}
