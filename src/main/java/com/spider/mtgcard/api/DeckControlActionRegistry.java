package com.spider.mtgcard.api;

import com.spider.mtgcard.db.search.CardMeta;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.deckcontrol.DeckControlPackets;
import com.spider.mtgcard.item.ModItemTags;
import com.spider.mtgcard.registry.ModRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DeckControlActionRegistry {
    public static final Identifier MTG_DRAW = ModRegistry.id("deck_draw");
    public static final Identifier MTG_SHUFFLE = ModRegistry.id("deck_shuffle");
    public static final Identifier MTG_SCRY = ModRegistry.id("deck_scry");
    public static final Identifier MTG_SURVEIL = ModRegistry.id("deck_surveil");
    public static final Identifier MTG_PEEK = ModRegistry.id("deck_peek");
    public static final Identifier MTG_MILL = ModRegistry.id("deck_mill");
    public static final Identifier MTG_CASCADE = ModRegistry.id("deck_cascade");
    public static final Identifier MTG_PLACE_SELECTED = ModRegistry.id("deck_place_selected");
    public static final Identifier MTG_SHUFFLE_GRAVEYARD = ModRegistry.id("deck_shuffle_graveyard_to_library");
    public static final Identifier MTG_RESET = ModRegistry.id("deck_reset");

    private static final Map<Identifier, Entry> ACTIONS = new LinkedHashMap<>();

    static {
        register(simple(TcgGameRegistry.MTG, MTG_DRAW, Component.literal("Draw"), 10,
                ctx -> ctx.deckControl().drawTopAndEject()));
        register(simple(TcgGameRegistry.MTG, MTG_SHUFFLE, Component.literal("Shuffle"), 20,
                ctx -> ctx.deckControl().shuffle()));
        register(numbered(TcgGameRegistry.MTG, MTG_SCRY, Component.literal("Scry"), 1, 1, 31, 30,
                ctx -> ctx.sendOverlay(DeckControlPackets.OverlayKind.SCRY, ctx.value())));
        register(numbered(TcgGameRegistry.MTG, MTG_SURVEIL, Component.literal("Surveil"), 1, 1, 31, 40,
                ctx -> ctx.sendOverlay(DeckControlPackets.OverlayKind.SURVEIL, ctx.value())));
        register(numbered(TcgGameRegistry.MTG, MTG_PEEK, Component.literal("Peek N"), 1, 1, 31, 50,
                ctx -> ctx.sendOverlay(DeckControlPackets.OverlayKind.REVEAL, ctx.value())));
        register(numbered(TcgGameRegistry.MTG, MTG_MILL, Component.literal("Mill"), 1, 1, 99, 60,
                ctx -> ctx.deckControl().millTop(ctx.value())));
        register(selectedCard(TcgGameRegistry.MTG, MTG_CASCADE, Component.literal("Start Cascade"), 70,
                ctx -> {
                    ItemStack selected = ctx.selectedPlayerStack();
                    if (!selected.isEmpty()) ctx.deckControl().startCascade(ctx.player(), CardMeta.read(selected).mv());
                }));
        register(new Entry(
                TcgGameRegistry.MTG,
                MTG_PLACE_SELECTED,
                Component.literal("Place Selected..."),
                Kind.PLACE_SELECTED,
                Activation.SELECTED_CARD,
                0,
                0,
                0,
                80,
                ctx -> {
                }
        ));
        register(simple(TcgGameRegistry.MTG, MTG_SHUFFLE_GRAVEYARD, Component.literal("Shuffle GY -> Library"), 90,
                ctx -> ctx.deckControl().shuffleGraveyardIntoLibrary(ctx.player())));
        register(simple(TcgGameRegistry.MTG, MTG_RESET, Component.literal("Reset Deck"), 100,
                ctx -> ctx.deckControl().resetDeck(ctx.player())));
    }

    public static Entry simple(String game, Identifier id, Component label, int sortOrder, Handler handler) {
        return new Entry(game, id, label, Kind.SIMPLE, Activation.LINKED, 0, 0, 0, sortOrder, handler);
    }

    public static Entry numbered(String game, Identifier id, Component label, int initialValue, int minValue,
                                 int maxValue, int sortOrder, Handler handler) {
        return new Entry(game, id, label, Kind.NUMBERED, Activation.LINKED, initialValue, minValue, maxValue,
                sortOrder, handler);
    }

    public static Entry selectedCard(String game, Identifier id, Component label, int sortOrder, Handler handler) {
        return new Entry(game, id, label, Kind.SELECTED_CARD, Activation.SELECTED_CARD, 0, 0, 0, sortOrder, handler);
    }

    public static synchronized Entry register(Entry entry) {
        Entry safe = Objects.requireNonNull(entry, "entry");
        if (!TcgGameRegistry.containsGame(safe.game())) {
            TcgGameRegistry.register(safe.game(), TcgGameRegistry.labelForGame(safe.game()));
        }
        ACTIONS.put(safe.id(), safe);
        return safe;
    }

    public static synchronized Optional<Entry> get(Identifier id) {
        return Optional.ofNullable(ACTIONS.get(id));
    }

    public static synchronized List<Entry> entriesForGame(String game) {
        String normalized = TcgGameRegistry.normalizeGameId(game);
        ArrayList<Entry> entries = new ArrayList<>();
        for (Entry entry : ACTIONS.values()) {
            if (entry.game().equals(normalized)) entries.add(entry);
        }
        entries.sort(Comparator
                .comparingInt(Entry::sortOrder)
                .thenComparing(e -> e.label().getString())
                .thenComparing(e -> e.id().toString()));
        return List.copyOf(entries);
    }

    public static synchronized List<TcgGameRegistry.Entry> gameEntriesWithActions() {
        ArrayList<TcgGameRegistry.Entry> entries = new ArrayList<>();
        for (TcgGameRegistry.Entry game : TcgGameRegistry.gameEntries()) {
            if (!entriesForGame(game.id()).isEmpty()) entries.add(game);
        }
        return List.copyOf(entries);
    }

    public static boolean hasMultipleGameActionSets() {
        return gameEntriesWithActions().size() > 1;
    }

    public static boolean run(String actionId, ServerPlayer player, DeckControlBlockEntity deckControl, int value,
                              int playerInventorySlot) {
        Identifier id = parseId(actionId).orElse(null);
        if (id == null) return false;

        Entry entry = get(id).orElse(null);
        if (entry == null) return false;
        if (!entry.game().equals(TcgGameRegistry.normalizeGameId(deckControl.getSelectedGame()))) return false;
        if (entry.kind() == Kind.PLACE_SELECTED) return false;
        if (!isActivationAllowed(entry, player, deckControl, playerInventorySlot)) return false;

        int safeValue = entry.kind() == Kind.NUMBERED
                ? clamp(value, entry.minValue(), entry.maxValue())
                : value;
        entry.handler().run(new Context(player, deckControl, safeValue, playerInventorySlot));
        return true;
    }

    private static boolean isActivationAllowed(Entry entry, ServerPlayer player, DeckControlBlockEntity deckControl,
                                               int playerInventorySlot) {
        return switch (entry.activation()) {
            case ALWAYS -> true;
            case LINKED -> deckControl.hasLinkedDeckbox();
            case SELECTED_CARD -> {
                if (!deckControl.hasLinkedDeckbox()) yield false;
                if (playerInventorySlot < 0 || playerInventorySlot >= player.getInventory().getContainerSize()) {
                    yield false;
                }
                ItemStack stack = player.getInventory().getItem(playerInventorySlot);
                yield !stack.isEmpty() && stack.is(ModItemTags.TCG_CARD);
            }
        };
    }

    private static Optional<Identifier> parseId(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            return Optional.of(Identifier.parse(raw.trim()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static List<ItemStack> noEmptyCopies(List<ItemStack> in, int max) {
        ArrayList<ItemStack> out = new ArrayList<>(Math.min(max, in.size()));
        for (int i = 0; i < in.size() && out.size() < max; i++) {
            ItemStack st = in.get(i);
            if (st == null || st.isEmpty()) continue;
            ItemStack c = st.copy();
            c.setCount(1);
            out.add(c);
        }
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Kind {
        SIMPLE,
        NUMBERED,
        SELECTED_CARD,
        PLACE_SELECTED
    }

    public enum Activation {
        ALWAYS,
        LINKED,
        SELECTED_CARD
    }

    @FunctionalInterface
    public interface Handler {
        void run(Context context);
    }

    public record Context(ServerPlayer player, DeckControlBlockEntity deckControl, int value, int playerInventorySlot) {
        public ItemStack selectedPlayerStack() {
            if (playerInventorySlot < 0 || playerInventorySlot >= player.getInventory().getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return player.getInventory().getItem(playerInventorySlot);
        }

        public void sendOverlay(DeckControlPackets.OverlayKind kind, int requestedCount) {
            int n = Math.max(1, requestedCount);
            List<ItemStack> cards = noEmptyCopies(deckControl.peekTopCopies(n), n);
            ServerPlayNetworking.send(player, new DeckControlPackets.OverlayS2C(
                    deckControl.getBlockPos(),
                    kind.ordinal(),
                    cards.size(),
                    cards
            ));
        }
    }

    public record Entry(
            String game,
            Identifier id,
            Component label,
            Kind kind,
            Activation activation,
            int initialValue,
            int minValue,
            int maxValue,
            int sortOrder,
            Handler handler
    ) {
        public Entry {
            game = TcgGameRegistry.normalizeGameId(game);
            if (game.isBlank()) throw new IllegalArgumentException("Deck Control action game must not be blank");
            id = Objects.requireNonNull(id, "id");
            label = label == null ? Component.literal(id.getPath()) : label;
            kind = kind == null ? Kind.SIMPLE : kind;
            activation = activation == null ? Activation.LINKED : activation;
            if (kind == Kind.NUMBERED) {
                minValue = Math.max(0, minValue);
                maxValue = Math.max(minValue, maxValue);
                initialValue = clamp(initialValue, minValue, maxValue);
            } else {
                initialValue = 0;
                minValue = 0;
                maxValue = 0;
            }
            handler = handler == null ? ctx -> {
            } : handler;
        }
    }

    private DeckControlActionRegistry() {
    }
}
