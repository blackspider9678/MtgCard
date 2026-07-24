package com.spider.mtgcard.api;

import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DeckboxRemovalCallbackRegistry {
    private static final List<Callback> CALLBACKS = new ArrayList<>();

    public static synchronized void register(Callback callback) {
        CALLBACKS.add(Objects.requireNonNull(callback, "callback"));
    }

    public static void fire(Level level, BlockPos pos, DeckboxBlockEntity deckbox) {
        List<Callback> callbacks;
        synchronized (DeckboxRemovalCallbackRegistry.class) {
            callbacks = List.copyOf(CALLBACKS);
        }

        for (Callback callback : callbacks) {
            try {
                callback.onDeckboxRemoved(level, pos, deckbox);
            } catch (Throwable ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface Callback {
        void onDeckboxRemoved(Level level, BlockPos pos, DeckboxBlockEntity deckbox);
    }

    private DeckboxRemovalCallbackRegistry() {
    }
}
