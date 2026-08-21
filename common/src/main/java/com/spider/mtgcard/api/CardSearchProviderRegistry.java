package com.spider.mtgcard.api;

import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Game-specific Card Database query hooks supplied by TCG add-ons. */
public final class CardSearchProviderRegistry {
    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();
    public static synchronized Provider register(Provider provider) {
        Provider safe = Objects.requireNonNull(provider, "provider");
        String game = TcgGameRegistry.normalizeGameId(safe.game());
        if (game.isBlank()) throw new IllegalArgumentException("Card search provider game must not be blank");
        PROVIDERS.put(game, safe);
        return safe;
    }
    public static synchronized Optional<Provider> get(String game) {
        return Optional.ofNullable(PROVIDERS.get(TcgGameRegistry.normalizeFilterId(game)));
    }
    public interface Provider { String game(); boolean matches(ItemStack stack, String query); }
    private CardSearchProviderRegistry() {}
}
