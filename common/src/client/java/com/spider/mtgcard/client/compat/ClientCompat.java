package com.spider.mtgcard.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientCompat {
    public static boolean isKeyDown(int key) {
        try {
            Method method = InputConstants.class.getMethod("isKeyDown", int.class);
            return (boolean) method.invoke(null, key);
        } catch (Throwable ignored) {
        }

        try {
            Window window = Minecraft.getInstance().getWindow();
            Method method = InputConstants.class.getMethod("isKeyDown", Window.class, int.class);
            return (boolean) method.invoke(null, window, key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int keyConstant(String primaryName, String fallbackName) {
        Integer value = keyConstant(primaryName);
        if (value != null) return value;
        value = keyConstant(fallbackName);
        return value != null ? value : 0;
    }

    public static InputConstants.Type keyboardType() {
        for (String name : new String[] { "KEYBOARD", "KEYSYM" }) {
            try {
                return InputConstants.Type.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return InputConstants.Type.values()[0];
    }

    public static RenderType itemGlint(Identifier texture) {
        try {
            Method method = RenderTypes.class.getMethod("itemCutoutGlint", Identifier.class);
            return (RenderType) method.invoke(null, texture);
        } catch (Throwable ignored) {
        }

        try {
            Method method = RenderTypes.class.getMethod("glint");
            return (RenderType) method.invoke(null);
        } catch (Throwable ignored) {
            return RenderTypes.itemCutout(texture);
        }
    }

    private static Integer keyConstant(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            Field field = InputConstants.class.getField(name);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ClientCompat() {}
}
