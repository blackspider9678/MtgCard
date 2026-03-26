package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.content.pack.cache.ScryfallCache;
import com.spider.mtgcard.content.pack.cache.ScryfallModels;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.net.ModPayloads;

import com.spider.mtgcard.util.CardStackBuilders;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PackGenerator {

        // Rarity slots (15 total)
        public enum RaritySlot {
                COMMON, WILDCARD_C_OR_U, UNCOMMON, RARE_OR_MYTHIC, BASIC_LAND, RANDOM, FOIL_RANDOM, TOKEN_OR_ART
        }

        // How this pack is allowed to use custom cards
        private enum CustomMode {
                NONE,         // never pull custom (official set packs, etc.)
                GLOBAL_CHANCE,// unnamed packs: per-slot chance from config (pool = all customs)
                GLOBAL_FORCE, // name == "custom": always try custom first (pool = all customs)
                SET_FORCE     // name == custom set code: always try that set’s customs first
        }

        @FunctionalInterface
        public interface Progress { void onProgress(int made, int total); }

        // ---------- Custom-card access ----------
        private static com.spider.mtgcard.content.pack.custom.CustomCardStore customStore(MinecraftServer server) {
                return com.spider.mtgcard.net.WorldState.get(server).customCards();
        }

        private static boolean isCustomSetCode(MinecraftServer server, String desiredSet) {
                if (desiredSet == null || desiredSet.isBlank()) return false;
                var store = customStore(server);
                if (store == null) return false;

                String want = desiredSet.trim().toLowerCase(Locale.ROOT);
                for (var m : store.all()) {
                        if (m != null && m.set != null && m.set.trim().equalsIgnoreCase(want)) return true;
                }
                return false;
        }

        // Decide how custom behaves for THIS pack name
        private static CustomMode customModeForPack(MinecraftServer server, @org.jetbrains.annotations.Nullable String desiredSet) {
                if (desiredSet == null || desiredSet.isBlank()) {
                        return CustomMode.GLOBAL_CHANCE; // unnamed packs use config-based chance
                }

                if ("custom".equalsIgnoreCase(desiredSet.trim())) {
                        return CustomMode.GLOBAL_FORCE;
                }

                if (isCustomSetCode(server, desiredSet)) {
                        return CustomMode.SET_FORCE;
                }

                return CustomMode.NONE; // official set code: no global custom mixing unless name == "custom"
        }

        // ---------- One-shot fetch ----------
        private static CompletableFuture<ScryfallModels.Card> fetchOneCardAsync(
                ServerLevel world,
                String desiredSet,
                ScryfallCache.Query q,
                RaritySlot slot
        ) {
                final String setCode = (desiredSet == null || desiredSet.isBlank())
                        ? null
                        : desiredSet.trim().toLowerCase(Locale.ROOT);

                if (slot == RaritySlot.TOKEN_OR_ART && setCode != null) {
                        ScryfallCache.Context ctxMain = ScryfallCache.Context.builder()
                                .gamePaper(true)
                                .excludeSets(List.of("4bb","fbb","rin","ren","ps11","psal"))
                                .blacklist(Set.of())
                                .onlySet(setCode)
                                .build();

                        ScryfallCache.Context ctxToken = ScryfallCache.Context.builder()
                                .gamePaper(true)
                                .excludeSets(List.of("4bb","fbb","rin","ren","ps11","psal"))
                                .blacklist(Set.of())
                                .onlySet("t" + setCode)
                                .build();

                        return ScryfallCache.pickRandomAsync(world, ctxMain, q, false, true)
                                .thenCompose(first -> (first != null)
                                        ? CompletableFuture.completedFuture(first)
                                        : ScryfallCache.pickRandomAsync(world, ctxToken, q, false, true));
                }

                ScryfallCache.Context ctx = ScryfallCache.Context.builder()
                        .gamePaper(true)
                        .excludeSets(List.of("4bb","fbb","rin","ren","ps11","psal"))
                        .blacklist(Set.of())
                        .onlySet(setCode)
                        .build();

                return ScryfallCache.pickRandomAsync(world, ctx, q, false, true);
        }

        // ---------- Async pack opening ----------
        public static void openPackAsync(MinecraftServer server, ServerPlayer player, String packUid) {
                final int TOTAL = 15;

                final PackOpenManager.Active active = PackOpenManager.get(player);

                final Set<String> seenIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

                ItemStack pack = (packUid != null && !packUid.isEmpty()) ? findPackByUid(player, packUid) : ItemStack.EMPTY;
                if (pack.isEmpty()) pack = findAnyPackInHandsOrInv(player);

                if (pack.isEmpty()) {
                        player.sendSystemMessage(Component.literal("No MTG pack found to open."), true);
                        PackOpenManager.finish(player);
                        return;
                }

                final String desiredSet = detectPackSet(pack);
                final CustomMode customMode = customModeForPack(server, desiredSet);

                ModPayloads.sendUnpackProgress(player, 0);

                final List<ItemStack> out = new ArrayList<>(TOTAL);

                // Exact slot recipe (15)
                final List<RaritySlot> slots = new ArrayList<>(TOTAL);
                for (int i = 0; i < 6; i++) slots.add(RaritySlot.COMMON);
                slots.add(RaritySlot.WILDCARD_C_OR_U);
                for (int i = 0; i < 3; i++) slots.add(RaritySlot.UNCOMMON);
                slots.add(RaritySlot.BASIC_LAND);
                slots.add(RaritySlot.FOIL_RANDOM);
                slots.add(RaritySlot.RARE_OR_MYTHIC);
                slots.add(RaritySlot.TOKEN_OR_ART);
                slots.add(RaritySlot.RANDOM);

                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

                for (RaritySlot slot : slots) {
                        chain = chain.thenCompose(v -> {
                                if (active != null && active.cancelled) return CompletableFuture.completedFuture(null);

                                // IMPORTANT: if a single card fails, don't blow up the whole pack.
                                return makeCardAsyncNoDupe(server, player, slot, desiredSet, customMode, seenIds, 6)
                                        .exceptionally(ex -> {
                                                server.execute(() -> {
                                                        player.sendSystemMessage(Component.literal("[MTGCard] Pack slot failed: " + ex.getClass().getSimpleName()), true);
                                                });
                                                return placeholderCard();
                                        })
                                        .thenAccept(st -> {
                                                if (active != null && active.cancelled) return;

                                                if (st == null || st.isEmpty()) st = placeholderCard();
                                                out.add(st);

                                                int percent = Math.min(100, (int) Math.round((out.size() * 100.0) / TOTAL));
                                                ModPayloads.sendUnpackProgress(player, percent);
                                        });
                        });
                }

                // Attach completion handling ONCE
                chain.whenComplete((ok, ex) -> server.execute(() -> {
                        if (active != null && active.cancelled) {
                                PackOpenManager.finish(player);
                                ModPayloads.sendUnpackProgress(player, 0);
                                return;
                        }

                        if (ex != null) {
                                // refund exactly once
                                if (active != null) {
                                        ItemStack refund = active.refundPackOne.copy();
                                        if (!player.getInventory().add(refund)) player.drop(refund, false);
                                }

                                PackOpenManager.finish(player);
                                ModPayloads.sendUnpackProgress(player, 0);

                                // Log it so we can see the real cause
                                com.spider.mtgcard.Mtgcard.LOGGER.error("[MTGCard] Pack opening failed", ex);
                                return;
                        }

                        // Success path
                        int foilIndex = slots.indexOf(RaritySlot.FOIL_RANDOM);
                        if (foilIndex >= 0 && foilIndex < out.size() && !out.get(foilIndex).isEmpty()) {
                                out.get(foilIndex).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        }

                        ItemStack bundle = new ItemStack(Items.BUNDLE);

                        List<net.minecraft.world.item.ItemStackTemplate> templates = out.stream()
                                .filter(stack -> stack != null && !stack.isEmpty())
                                .map(net.minecraft.world.item.ItemStackTemplate::fromNonEmptyStack)
                                .toList();

                        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));

                        if (!player.getInventory().add(bundle)) {
                                player.drop(bundle, false);
                        }

                        ModPayloads.sendUnpackProgress(player, 100);
                        PackOpenManager.finish(player);
                }));
        }

        private static ItemStack placeholderCard() {
                ItemStack st = new ItemStack(ModItems.CARD);
                // Optional: make it obvious it’s a fallback
                // st.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Unknown Card"));
                return st;
        }

        private static CompletableFuture<ItemStack> makeCardAsyncNoDupe(
                MinecraftServer server,
                ServerPlayer player,
                RaritySlot slot,
                String desiredSet,
                CustomMode customMode,
                Set<String> seenIds,
                int attemptsLeft
        ) {
                return makeCardAsync(server, player, slot, desiredSet, customMode)
                        .thenCompose(st -> {
                                if (st == null || st.isEmpty()) return CompletableFuture.completedFuture(st);

                                String id = readMtgId(st);
                                if (id == null || id.isBlank()) return CompletableFuture.completedFuture(st);

                                if (seenIds.contains(id)) {
                                        if (attemptsLeft <= 0) return CompletableFuture.completedFuture(st);
                                        return makeCardAsyncNoDupe(server, player, slot, desiredSet, customMode, seenIds, attemptsLeft - 1);
                                }

                                seenIds.add(id);
                                return CompletableFuture.completedFuture(st);
                        });
        }

        // ---------- Core card builder (async) ----------
        private static CompletableFuture<ItemStack> makeCardAsync(
                MinecraftServer server,
                ServerPlayer player,
                RaritySlot slot,
                String desiredSet,
                CustomMode customMode
        ) {
                ServerLevel world = player.level();
                boolean foilVisual = (slot == RaritySlot.FOIL_RANDOM);

                // Query per slot
                final ScryfallCache.Query q = switch (slot) {
                        case COMMON -> ScryfallCache.Query.common();
                        case WILDCARD_C_OR_U -> (Math.random() < 0.5) ? ScryfallCache.Query.common() : ScryfallCache.Query.uncommon();
                        case UNCOMMON -> ScryfallCache.Query.uncommon();
                        case RARE_OR_MYTHIC -> (Math.random() < 0.125) ? ScryfallCache.Query.mythic() : ScryfallCache.Query.rare();
                        case BASIC_LAND -> ScryfallCache.Query.basicLand();
                        case RANDOM, FOIL_RANDOM -> ScryfallCache.Query.randomNonBasic();
                        case TOKEN_OR_ART -> ScryfallCache.Query.tokenOrExtra();
                };

                // Determine if we should attempt a custom pull FIRST for this slot
                boolean tryCustomFirst = switch (customMode) {
                        case GLOBAL_FORCE, SET_FORCE -> true;
                        case GLOBAL_CHANCE -> rollGlobalChance(server, slot);
                        case NONE -> false;
                };

                // If SET_FORCE, pool is restricted to that set. If GLOBAL_* then pool is all customs.
                final boolean restrictToSet = (customMode == CustomMode.SET_FORCE);
                final String setCode = (desiredSet == null) ? null : desiredSet.trim().toLowerCase(Locale.ROOT);

                if (tryCustomFirst) {
                        ItemStack custom = restrictToSet
                                ? tryCustomCard(world, setCode, slot, true)
                                : tryCustomCard(world, null, slot, false);

                        if (!custom.isEmpty()) {
                                if (foilVisual) custom.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                                return CompletableFuture.completedFuture(custom);
                        }
                        // else fall through to Scryfall fallback
                }

                // Scryfall behavior:
                // - Official set packs (customMode NONE) keep desiredSet strict.
                // - Unnamed packs (GLOBAL_CHANCE) pass null -> global.
                // - "custom" packs fallback to global scryfall (null) if customs are missing.
                String scrySet = null;
                if (customMode == CustomMode.NONE && setCode != null && !setCode.isBlank()) {
                        // official set code -> strict
                        scrySet = setCode;
                } else if (customMode == CustomMode.SET_FORCE) {
                        // named custom-set pack fallback: global scryfall (NOT strict to setCode)
                        scrySet = null;
                } else {
                        // unnamed / custom -> global scryfall
                        scrySet = null;
                }

                final String finalScrySet = scrySet;

                return fetchOneCardAsync(world, finalScrySet, q, slot).thenApply(card -> {
                        ItemStack built = CardStackBuilders.buildScryfallStackFromModel(card, foilVisual);

                        // Safety: if token slipped (shouldn’t, but just in case), only allow in TOKEN slot
                        if (slot != RaritySlot.TOKEN_OR_ART && isTokenLike(built)) {
                                return new ItemStack(ModItems.CARD);
                        }
                        if (foilVisual) built.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        return built;
                });
        }

        /** Per-slot roll (only used for unnamed packs). */
        private static boolean rollGlobalChance(MinecraftServer server, RaritySlot slot) {
                try {
                        // Use your config singleton accessor.
                        // If yours differs, replace MtgConfig.get() with whatever you use.
                        var cfg = com.spider.mtgcard.config.MtgcardConfig.get();
                        double p = (cfg == null) ? 0.0 : cfg.customChanceForSlot(slot);
                        return Math.random() < p;
                } catch (Throwable t) {
                        return false;
                }
        }

        // ---------- Custom selection ----------
        /**
         * @param desiredSet If non-null, restrict to that set code (custom set packs). If null, allow ALL sets (global custom pool).
         * @param setRestricted true when desiredSet is meaningful (set-specific), false when global pool.
         */
        private static ItemStack tryCustomCard(ServerLevel world, @org.jetbrains.annotations.Nullable String desiredSet, RaritySlot slot, boolean setRestricted) {
                var server = world.getServer();
                var store = customStore(server);
                if (store == null) return ItemStack.EMPTY;

                var all = store.all();
                if (all == null || all.isEmpty()) return ItemStack.EMPTY;

                // Normalize set code (only if restricted)
                final String setCode = (setRestricted && desiredSet != null) ? desiredSet.trim().toLowerCase(Locale.ROOT) : null;

                // Build candidates
                List<com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta> candidates = all.stream()
                        .filter(m -> m != null)
                        .filter(m -> {
                                if (!setRestricted) return true;
                                if (m.set == null) return false;
                                return m.set.trim().equalsIgnoreCase(setCode);
                        })
                        .filter(m -> customMatchesSlot(m, slot))
                        .toList();

                if (candidates.isEmpty()) return ItemStack.EMPTY;

                var chosen = candidates.get(new Random().nextInt(candidates.size()));
                ItemStack st = CardStackBuilders.buildCustomStackFromMeta(chosen, false);

                // If this is TOKEN slot, mark token-like for downstream code/UI
                if (slot == RaritySlot.TOKEN_OR_ART) {
                        try {
                                var comp = st.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                                CompoundTag root = comp.copyTag();
                                CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
                                meta.putBoolean("is_token_like", true);
                                root.put("mtg_meta", meta);
                                st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                        } catch (Throwable ignored) {}
                }

                return st;
        }

        /** Slot matching rules for custom cards (prevents token/basic leaking into other slots). */
        private static boolean customMatchesSlot(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta m, RaritySlot slot) {
                String tl = (m.typeLine == null) ? "" : m.typeLine.toLowerCase(Locale.ROOT);
                String rRaw = (m.rarity == null) ? "" : m.rarity.trim().toLowerCase(Locale.ROOT);

                boolean isBasic = tl.contains("basic land");
                boolean isTokenLike =
                        tl.contains("token") || tl.contains("emblem") || tl.contains("dungeon") ||
                                tl.contains("art series") || tl.contains("artseries") || tl.contains("marker");

                // Keep token/basic contained to their slot
                if (slot == RaritySlot.BASIC_LAND) return isBasic;
                if (slot == RaritySlot.TOKEN_OR_ART) return isTokenLike;

                if (isBasic || isTokenLike) return false;

                String norm = switch (rRaw) {
                        case "c", "common" -> "c";
                        case "u", "uncommon" -> "u";
                        case "r", "rare" -> "r";
                        case "m", "mythic", "mythic rare" -> "m";
                        default -> rRaw;
                };

                return switch (slot) {
                        case COMMON -> norm.equals("c");
                        case UNCOMMON -> norm.equals("u");
                        case WILDCARD_C_OR_U -> norm.equals("c") || norm.equals("u");
                        case RARE_OR_MYTHIC -> norm.equals("r") || norm.equals("m");
                        case RANDOM -> true;
                        case FOIL_RANDOM -> true;
                        case BASIC_LAND, TOKEN_OR_ART -> false; // handled above
                };
        }

        // ---------- Dedupe / helpers ----------
        private static String readMtgId(ItemStack st) {
                var cd = st.get(DataComponents.CUSTOM_DATA);
                if (cd == null) return null;
                var root = cd.copyTag();
                var meta = root.getCompound("mtg_meta").orElse(null);
                if (meta == null) return null;
                String id = meta.getString("id").orElse("");
                return id.isBlank() ? null : id;
        }

        private static boolean isTokenLike(ItemStack st) {
                try {
                        var cd = st.get(DataComponents.CUSTOM_DATA);
                        if (cd == null) return false;
                        var root = cd.copyTag();
                        var meta = root.getCompound("mtg_meta").orElse(null);
                        if (meta == null) return false;
                        return meta.getBoolean("is_token_like").orElse(false);
                } catch (Throwable t) {
                        return false;
                }
        }

        // ---------- Pack lookup ----------
        private static ItemStack findPackByUid(ServerPlayer player, String packUid) {
                if (packUid == null || packUid.isEmpty()) return ItemStack.EMPTY;

                ItemStack[] hands = { player.getMainHandItem(), player.getOffhandItem() };
                for (ItemStack st : hands) {
                        if (!st.isEmpty()
                                && st.is(ModItems.MTG_PACK)
                                && uidEquals(st, packUid)) {
                                return st;
                        }
                }

                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack st = inv.getItem(i);
                        if (!st.isEmpty()
                                && st.is(ModItems.MTG_PACK)
                                && uidEquals(st, packUid)) {
                                return st;
                        }
                }
                return ItemStack.EMPTY;
        }

        private static boolean uidEquals(ItemStack st, String uid) {
                var comp = st.get(DataComponents.CUSTOM_DATA);
                if (comp == null) return false;
                CompoundTag root = comp.copyTag();
                CompoundTag tag  = root.getCompound("mtg_pack").orElse(null);
                if (tag == null) return false;
                String have = tag.getString("uid").orElse("");
                return !have.isEmpty() && have.equals(uid);
        }

        private static ItemStack findAnyPackInHandsOrInv(ServerPlayer player) {
                for (ItemStack st : new ItemStack[]{ player.getMainHandItem(), player.getOffhandItem() }) {
                        if (!st.isEmpty() && st.is(ModItems.MTG_PACK)) return st;
                }
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack st = inv.getItem(i);
                        if (!st.isEmpty() && st.is(ModItems.MTG_PACK)) return st;
                }
                return ItemStack.EMPTY;
        }

        // ---------- Set detection ----------
        public static String detectPackSetPublic(ItemStack packItem) {
                return detectPackSet(packItem);
        }

        private static String detectPackSet(ItemStack packItem) {
                if (packItem == null || packItem.isEmpty()) return null;

                var name = packItem.getHoverName();
                String disp = (name == null) ? "" : name.getString();
                disp = disp.replaceAll("§.", "").trim();

                // [CODE]
                var m = java.util.regex.Pattern
                        .compile("\\[([A-Za-z0-9]{2,6})]", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(disp);
                if (m.find()) {
                        return m.group(1).toLowerCase(Locale.ROOT);
                }

                // Bare CODE (e.g. "TLA" or "custom")
                String bare = disp.trim();
                if (bare.matches("^[A-Za-z0-9]{2,6}$") || "custom".equalsIgnoreCase(bare)) {
                        return bare.toLowerCase(Locale.ROOT);
                }

                // CUSTOM_DATA fallback
                var comp = packItem.get(DataComponents.CUSTOM_DATA);
                if (comp != null) {
                        var root = comp.copyTag();
                        var tag  = root.getCompound("mtg_pack").orElse(null);
                        String fromData = (tag == null) ? "" : tag.getString("set").orElse("");
                        if (!fromData.isBlank()) return fromData.trim().toLowerCase(Locale.ROOT);
                }

                return null;
        }

        private PackGenerator() {}
}
