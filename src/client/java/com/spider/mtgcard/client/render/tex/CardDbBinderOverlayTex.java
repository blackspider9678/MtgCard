package com.spider.mtgcard.client.render.tex;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

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
                    img.setColorArgb(x, y, 0x00000000);
                }
            }

            drawBinders(img, front, k);

            var name = "mtgcard/carddb_binders_" + (front ? "front_" : "side_") + k;
            var tex = new NativeImageBackedTexture(() -> name, img);

            var id = Identifier.of("mtgcard", "carddb_binders/" + (front ? "front_" : "side_") + k);

            MinecraftClient.getInstance()
                    .getTextureManager()
                    .registerTexture(id, tex);

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

            img.setColorArgb(x, y, color);
            if (y + 1 < 16) img.setColorArgb(x, y + 1, color);
        }
    }

    public static Identifier front(int filled) { return getOrCreate(true, filled); }
    public static Identifier side(int filled)  { return getOrCreate(false, filled); }

    private CardDbBinderOverlayTex() {}
}
