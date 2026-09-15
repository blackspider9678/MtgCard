package com.spider.mtgcard.api;

import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import com.spider.mtgcard.util.StackData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** Server-side sleeve application for a card or UUID-backed Deckbox item. */
public final class SleeveApplication {
    public enum CurrentState { NONE, ONE_SLEEVE, MIXED, EMPTY }
    public record Selection(CurrentState state, Optional<Identifier> sleeveId) {}

    public static boolean isCompatibleInput(ItemStack stack) {
        return CardItemRegistry.isCard(stack) || (stack != null && stack.getItem() instanceof DeckboxBlockItem);
    }

    /** A null/empty sleeve ID means No Sleeve and removes data rather than assigning a sentinel. */
    public static int apply(MinecraftServer server, HolderLookup.Provider registries,
                            ItemStack input, Identifier sleeveId) {
        if (input == null || input.isEmpty()) return 0;
        if (sleeveId != null && SleeveRegistry.get(sleeveId).isEmpty()) return 0;

        if (CardItemRegistry.isCard(input)) {
            applyOne(input, sleeveId);
            return 1;
        }
        if (!(input.getItem() instanceof DeckboxBlockItem) || server == null) return 0;

        UUID id = deckboxId(input).orElse(null);
        if (id == null) return 0;
        DeckboxStorage.Record record = DeckboxStorage.load(server, registries, id).orElse(null);
        if (record == null) return 0;

        int changed = 0;
        for (ItemStack stack : record.items()) {
            if (!CardItemRegistry.isCard(stack)) continue;
            applyOne(stack, sleeveId);
            changed++;
        }
        if (changed > 0) {
            DeckboxStorage.save(server, registries, id, record.type(), record.name(), record.items());
        }
        return changed;
    }

    public static Selection current(MinecraftServer server, HolderLookup.Provider registries, ItemStack input) {
        if (CardItemRegistry.isCard(input)) {
            Optional<Identifier> id = CardSleeves.id(input).filter(value -> SleeveRegistry.get(value).isPresent());
            return new Selection(id.isPresent() ? CurrentState.ONE_SLEEVE : CurrentState.NONE, id);
        }
        if (input == null || !(input.getItem() instanceof DeckboxBlockItem) || server == null) {
            return new Selection(CurrentState.EMPTY, Optional.empty());
        }
        UUID id = deckboxId(input).orElse(null);
        DeckboxStorage.Record record = id == null ? null : DeckboxStorage.load(server, registries, id).orElse(null);
        if (record == null) return new Selection(CurrentState.EMPTY, Optional.empty());

        boolean found = false;
        Optional<Identifier> first = Optional.empty();
        for (ItemStack stack : record.items()) {
            if (!CardItemRegistry.isCard(stack)) continue;
            Optional<Identifier> current = CardSleeves.id(stack).filter(value -> SleeveRegistry.get(value).isPresent());
            if (!found) { found = true; first = current; }
            else if (!first.equals(current)) return new Selection(CurrentState.MIXED, Optional.empty());
        }
        if (!found) return new Selection(CurrentState.EMPTY, Optional.empty());
        return new Selection(first.isPresent() ? CurrentState.ONE_SLEEVE : CurrentState.NONE, first);
    }

    private static void applyOne(ItemStack stack, Identifier sleeveId) {
        if (sleeveId == null) CardSleeves.clear(stack);
        else CardSleeves.set(stack, sleeveId);
    }

    private static Optional<UUID> deckboxId(ItemStack stack) {
        CompoundTag root = StackData.readCustom(stack);
        String raw = root.getString(DeckboxStorage.ITEM_ID_KEY).orElse("");
        try { return raw.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(raw)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    private SleeveApplication() {}
}
