package com.spider.mtgcard.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.dice.DiceGradientType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DiceTextureCache {
    private static final Map<Key, TextureRef> CACHE = new HashMap<>();
    private static int nextId = 0;

    public record TextureRef(Identifier id, int width, int height) {}

    public static TextureRef getTexture(int sides, DiceAppearance appearance) {
        DiceAppearance safe = appearance == null ? DiceAppearance.DEFAULT : appearance;
        Key key = new Key(sides, safe);
        return CACHE.computeIfAbsent(key, DiceTextureCache::buildTexture);
    }

    private static TextureRef buildTexture(Key key) {
        try (NativeImage base = readImage(baseTextureId(key.sides()))) {
            if (base != null) {
                NativeImage generated = renderFromMask(key.appearance(), base);
                return register("dice_" + key.sides(), generated);
            }
        } catch (IOException ignored) {
            // Fall through to a generated fallback so the item never renders as missing.
        }

        return register("dice_fallback_" + key.sides(), renderFallback(key.appearance()));
    }

    private static NativeImage renderFromMask(DiceAppearance appearance, NativeImage base) {
        int width = base.getWidth();
        int height = base.getHeight();
        NativeImage out = new NativeImage(width, height, true);
        PatternStencil pattern = readPattern(appearance.bannerPattern());
        Bounds bodyBounds = findBodyBounds(base);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int source = base.getPixel(x, y);
                int alpha = alpha(source);
                if (alpha <= 8) {
                    out.setPixel(x, y, 0);
                    continue;
                }

                int r = red(source);
                int g = green(source);
                int b = blue(source);

                if (isBorder(r, g, b)) {
                    out.setPixel(x, y, argb(alpha, shade(appearance.borderColor(), luminance(r, g, b))));
                } else if (isText(r, g, b)) {
                    out.setPixel(x, y, argb(alpha, shade(appearance.textColor(), luminance(r, g, b))));
                } else {
                    int color = gradientColor(appearance, x, y, width, height);
                    color = shade(color, luminance(r, g, b));
                    if (pattern != null) {
                        color = applyPattern(color, appearance.bannerColor(), pattern, x, y, bodyBounds);
                    }
                    out.setPixel(x, y, argb(alpha, color));
                }
            }
        }

        if (pattern != null) {
            pattern.close();
        }

        return out;
    }

    private static NativeImage renderFallback(DiceAppearance appearance) {
        int size = 256;
        NativeImage out = new NativeImage(size, size, true);
        int margin = 24;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean inside = x >= margin && x < size - margin && y >= margin && y < size - margin;
                if (!inside) {
                    out.setPixel(x, y, 0);
                    continue;
                }

                boolean border = x < margin + 8 || x >= size - margin - 8 || y < margin + 8 || y >= size - margin - 8;
                if (border) {
                    out.setPixel(x, y, argb(255, appearance.borderColor()));
                } else {
                    out.setPixel(x, y, argb(255, gradientColor(appearance, x, y, size, size)));
                }
            }
        }

        return out;
    }

    private static TextureRef register(String label, NativeImage image) {
        Identifier id = Identifier.fromNamespaceAndPath(
                Mtgcard.MOD_ID,
                "dynamic/dice/" + label + "_" + Integer.toUnsignedString(nextId++, 36)
        );
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "mtgcard/" + label, image));
        return new TextureRef(id, image.getWidth(), image.getHeight());
    }

    private static NativeImage readImage(Identifier id) throws IOException {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }

    private static PatternStencil readPattern(Optional<Identifier> patternAsset) {
        if (patternAsset.isEmpty()) {
            return null;
        }

        Identifier asset = patternAsset.get();
        Identifier texture = Identifier.fromNamespaceAndPath(
                asset.getNamespace(),
                "textures/entity/banner/" + asset.getPath() + ".png"
        );

        try {
            NativeImage image = readImage(texture);
            return image == null ? null : PatternStencil.create(image);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Identifier baseTextureId(int sides) {
        String name = switch (sides) {
            case 4 -> "d4_dice";
            case 6 -> "d6_dice";
            case 8 -> "d8_dice";
            case 10 -> "d10_dice";
            case 12 -> "d12_dice";
            case 100 -> "d100_dice";
            default -> "d20_dice";
        };
        return Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "textures/item/" + name + ".png");
    }

    private static int gradientColor(DiceAppearance appearance, int x, int y, int width, int height) {
        DiceGradientType type = appearance.gradientType();
        if (type == DiceGradientType.SOLID) {
            return appearance.primaryColor();
        }

        float fx = width <= 1 ? 0.0F : x / (float) (width - 1);
        float fy = height <= 1 ? 0.0F : y / (float) (height - 1);
        float t = switch (type) {
            case VERTICAL -> fy;
            case HORIZONTAL -> fx;
            case DIAGONAL -> (fx + fy) * 0.5F;
            case RADIAL -> {
                float dx = fx - 0.5F;
                float dy = fy - 0.5F;
                yield Math.min(1.0F, (float) Math.sqrt(dx * dx + dy * dy) * 1.65F);
            }
            case SOLID -> 0.0F;
        };
        return lerpColor(appearance.primaryColor(), appearance.secondaryColor(), clamp01(t));
    }

    private static Bounds findBodyBounds(NativeImage base) {
        int minX = base.getWidth();
        int minY = base.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int source = base.getPixel(x, y);
                if (alpha(source) <= 8) {
                    continue;
                }

                int r = red(source);
                int g = green(source);
                int b = blue(source);
                if (isBorder(r, g, b) || isText(r, g, b)) {
                    continue;
                }

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            return new Bounds(0, 0, base.getWidth() - 1, base.getHeight() - 1);
        }

        return new Bounds(minX, minY, maxX, maxY);
    }

    private static int applyPattern(int base, int patternColor, PatternStencil pattern, int x, int y, Bounds bodyBounds) {
        float maxTargetSize = Math.min(bodyBounds.width(), bodyBounds.height()) * 0.52F;
        float scale = maxTargetSize / Math.max(pattern.width(), pattern.height());
        float targetW = Math.max(1.0F, pattern.width() * scale);
        float targetH = Math.max(1.0F, pattern.height() * scale);
        float targetMinX = bodyBounds.centerX() - targetW * 0.5F;
        float targetMinY = bodyBounds.centerY() - targetH * 0.5F;
        float px = x + 0.5F;
        float py = y + 0.5F;
        if (px < targetMinX || px > targetMinX + targetW || py < targetMinY || py > targetMinY + targetH) {
            return base;
        }

        float u = (px - targetMinX) / targetW;
        float v = (py - targetMinY) / targetH;
        int sample = pattern.sample(u, v);
        int pa = alpha(sample);
        if (pa <= 12) {
            return base;
        }
        float strength = Math.min(0.55F, pa / 255.0F * 0.45F);
        return lerpColor(base, patternColor, strength);
    }

    private static boolean isBorder(int r, int g, int b) {
        return r < 70 && g < 65 && b < 85;
    }

    private static boolean isText(int r, int g, int b) {
        return r > 155 && g > 45 && g < 205 && b < 110;
    }

    private static int shade(int rgb, int luminance) {
        float scale = 0.72F + luminance / 255.0F * 0.46F;
        int r = Math.min(255, Math.round(red(rgb) * scale));
        int g = Math.min(255, Math.round(green(rgb) * scale));
        int b = Math.min(255, Math.round(blue(rgb) * scale));
        return (r << 16) | (g << 8) | b;
    }

    private static int luminance(int r, int g, int b) {
        return Math.round(r * 0.2126F + g * 0.7152F + b * 0.0722F);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round(red(a) + (red(b) - red(a)) * t);
        int g = Math.round(green(a) + (green(b) - green(a)) * t);
        int blue = Math.round(blue(a) + (blue(b) - blue(a)) * t);
        return (r << 16) | (g << 8) | blue;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int argb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int red(int argbOrRgb) {
        return (argbOrRgb >>> 16) & 0xFF;
    }

    private static int green(int argbOrRgb) {
        return (argbOrRgb >>> 8) & 0xFF;
    }

    private static int blue(int argbOrRgb) {
        return argbOrRgb & 0xFF;
    }

    private record Key(int sides, DiceAppearance appearance) {}

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private float centerX() {
            return minX + (width() - 1) * 0.5F;
        }

        private float centerY() {
            return minY + (height() - 1) * 0.5F;
        }
    }

    private static final class PatternStencil implements AutoCloseable {
        private final NativeImage image;
        private final int minX;
        private final int minY;
        private final int width;
        private final int height;

        private PatternStencil(NativeImage image, int minX, int minY, int width, int height) {
            this.image = image;
            this.minX = minX;
            this.minY = minY;
            this.width = width;
            this.height = height;
        }

        private static PatternStencil create(NativeImage image) {
            // Vanilla banner pattern textures store multiple horizontal UV copies.
            // The first face is the only one we want for a centered dice stencil.
            int scanMaxX = Math.max(1, image.getWidth() / 3);
            int minX = scanMaxX;
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < scanMaxX; x++) {
                    if (alpha(image.getPixel(x, y)) > 12) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return new PatternStencil(image, 0, 0, scanMaxX, image.getHeight());
            }

            int pad = 0;
            minX = Math.max(0, minX - pad);
            minY = Math.max(0, minY - pad);
            maxX = Math.min(scanMaxX - 1, maxX + pad);
            maxY = Math.min(image.getHeight() - 1, maxY + pad);
            return new PatternStencil(image, minX, minY, maxX - minX + 1, maxY - minY + 1);
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }

        private int sample(float u, float v) {
            int px = minX + Math.min(width - 1, Math.max(0, Math.round(u * (width - 1))));
            int py = minY + Math.min(height - 1, Math.max(0, Math.round(v * (height - 1))));
            return image.getPixel(px, py);
        }

        @Override
        public void close() {
            image.close();
        }
    }

    private DiceTextureCache() {}
}
