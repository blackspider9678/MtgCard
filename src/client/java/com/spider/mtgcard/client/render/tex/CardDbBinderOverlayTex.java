package com.spider.mtgcard.client.render.tex;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

// client-only
public final class CardDbBinderOverlayTex {
    private static final java.util.Map<Integer, Identifier> FRONT = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Identifier> SIDE  = new java.util.HashMap<>();

    private static Identifier getOrCreate(boolean front, int filled) {
        var map = front ? FRONT : SIDE;
        return map.computeIfAbsent(filled, k -> {
            var img = new NativeImage(16, 16, true);
            // transparent background
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    img.setPixel(x, y, 0x00000000);
                }
            }

            drawBinders(img, front, k);

            var name = "mtgcard/carddb_binders_" + (front ? "front_" : "side_") + k;
            var tex = new DynamicTexture(() -> name, img);

            var id = Identifier.fromNamespaceAndPath("mtgcard", "carddb_binders/" + (front ? "front_" : "side_") + k);

            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, tex);

            return id;
        });
    }

    // tweak these coords to match your shelf pixels
    private static void drawBinders(NativeImage img, boolean front, int filled) {
        int width = front ? 7 : 14;
        int x0    = front ? 9 : 1;
        int[] shelfY = { 2, 5, 8, 11, 14 };

        int max = width * 5;
        filled = Math.max(0, Math.min(filled, max));

        int color = 0xFFE8E1D6;

        for (int i = 0; i < filled; i++) {
            int shelf = i / width;
            int col   = i % width;

            int x = x0 + col;
            int y = shelfY[shelf];

            img.setPixel(x, y, color);
            if (y + 1 < 16) img.setPixel(x, y + 1, color);
        }
    }

    public static Identifier front(int filled) { return getOrCreate(true, filled); }
    public static Identifier side(int filled)  { return getOrCreate(false, filled); }

    private CardDbBinderOverlayTex() {}
}
