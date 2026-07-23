package com.spider.mtgcard.client.compat.mousetweaks;

import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "yalter.mousetweaks.handlers.GuiContainerHandler", remap = false)
public abstract class MouseTweaksGuiContainerHandlerMixin {
    private static final int DB_WINDOW_SLOTS = 54;

    @Inject(method = "isIgnored", at = @At("HEAD"), cancellable = true, remap = false)
    private void mtgcard$ignoreCardDatabaseWindowSlots(Object slot, CallbackInfoReturnable<Boolean> cir) {
        Object screen = readField(this, "screen");
        if (!(screen instanceof CardDatabaseScreen)) {
            return;
        }

        int slotIndex = readIntField(slot, "index", -1);
        if (slotIndex >= 0 && slotIndex < DB_WINDOW_SLOTS) {
            cir.setReturnValue(true);
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;

        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static int readIntField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Integer i ? i : fallback;
    }
}
