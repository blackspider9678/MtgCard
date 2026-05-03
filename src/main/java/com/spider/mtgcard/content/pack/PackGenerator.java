package com.spider.mtgcard.content.pack;

import com.spider.mtgcard.advancement.ModAdvancements;
import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.content.pack.cache.ScryfallCache;
import com.spider.mtgcard.content.pack.cache.ScryfallModels;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.net.ModPayloads;

import com.spider.mtgcard.util.CardStackBuilders;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PackGenerator {
        private static final long PACK_OPEN_TIMEOUT_SECONDS = 75L;

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
        public static void openPackAsync(MinecraftServer server, ServerPlayer player, String desiredSet) {
                final int TOTAL = 15;
                final long startedAtMs = System.currentTimeMillis();
                final String setLabel = describePackSet(desiredSet);
                final String playerName = player.getName().getString();

                final PackOpenManager.Active active = PackOpenManager.get(player);
                final String packUid = active == null ? "missing" : active.packUid;
                final boolean packDebug = MtgcardConfig.packDebugEnabled();

                final Set<String> seenCardKeys = Collections.newSetFromMap(new ConcurrentHashMap<>());
                final CustomMode customMode = customModeForPack(server, desiredSet);

                if (packDebug) {
                        Mtgcard.LOGGER.info(
                                "[MTGCard][PackDebug] Pack async open started player={} uid={} set={} customMode={} preferredSlot={} inventory={}",
                                playerName,
                                packUid,
                                setLabel,
                                customMode,
                                preferredReturnSlot(active),
                                PackInventoryUtil.describeInventoryState(player, preferredReturnSlot(active))
                        );
                        if (active == null) {
                                Mtgcard.LOGGER.warn(
                                        "[MTGCard][PackDebug] Pack async open missing active state for player={} uid={} set={}",
                                        playerName,
                                        packUid,
                                        setLabel
                                );
                        }
                }

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

                                return makeCardAsyncNoDupe(server, player, slot, desiredSet, customMode, seenCardKeys, 6)
                                        .thenApply(st -> requireResolvedCard(slot, st))
                                        .thenAccept(st -> {
                                                if (active != null && active.cancelled) return;

                                                out.add(st);

                                                int percent = Math.min(99, (int) Math.round((out.size() * 100.0) / TOTAL));
                                                ModPayloads.sendUnpackProgress(player, percent);
                                                if (packDebug) {
                                                        Mtgcard.LOGGER.info(
                                                                "[MTGCard][PackDebug] Pack slot resolved player={} uid={} slot={} index={}/{} card={} urls={} progress={}%",
                                                                playerName,
                                                                packUid,
                                                                slot,
                                                                out.size(),
                                                                TOTAL,
                                                                describeCard(st),
                                                                describeCardUrls(st),
                                                                percent
                                                        );
                                                }
                                });
                        });
                }

                chain = chain.orTimeout(PACK_OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Attach completion handling ONCE
                chain.whenComplete((ok, ex) -> server.execute(() -> {
                        boolean delivered = false;
                        long elapsedMs = System.currentTimeMillis() - startedAtMs;
                        try {
                                if (active != null && active.cancelled) {
                                        if (packDebug) {
                                                Mtgcard.LOGGER.info(
                                                        "[MTGCard][PackDebug] Pack completion aborted because active state was cancelled player={} uid={} set={} elapsedMs={}",
                                                        playerName,
                                                        packUid,
                                                        setLabel,
                                                        elapsedMs
                                                );
                                        }
                                        PackOpenManager.finish(player);
                                        ModPayloads.clearUnpackProgress(player);
                                        return;
                                }

                                Throwable failure = unwrapCompletion(ex);
                                if (failure != null) {
                                        markCancelled(active);

                                        PackInventoryUtil.DeliveryResult refundResult = refundPackIfNeeded(player, active);

                                        PackOpenManager.finish(player);
                                        ModPayloads.clearUnpackProgress(player);

                                        Mtgcard.LOGGER.error(
                                                "[MTGCard] Pack opening failed for player={} set={} after {} ms",
                                                playerName,
                                                setLabel,
                                                elapsedMs,
                                                failure
                                        );
                                        logFailureOutcome(playerName, failure, refundResult);
                                        return;
                                }

                                if (out.size() != TOTAL || out.stream().anyMatch(st -> !isResolvedPackCard(st))) {
                                        markCancelled(active);
                                        PackInventoryUtil.DeliveryResult refundResult = refundPackIfNeeded(player, active);

                                        PackOpenManager.finish(player);
                                        ModPayloads.clearUnpackProgress(player);
                                        logFailureOutcome(playerName, null, refundResult);
                                        Mtgcard.LOGGER.error(
                                                "[MTGCard] Pack opening produced unresolved cards for player={} set={} after {} ms: {}",
                                                playerName,
                                                setLabel,
                                                elapsedMs,
                                                out.size()
                                        );
                                        return;
                                }

                                // Success path
                                int foilIndex = slots.indexOf(RaritySlot.FOIL_RANDOM);
                                if (foilIndex >= 0 && foilIndex < out.size() && !out.get(foilIndex).isEmpty()) {
                                        out.get(foilIndex).set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                                }

                                ItemStack bundle = new ItemStack(Items.BUNDLE);

                                List<ItemStackTemplate> templates = out.stream()
                                        .map(ItemStack::copy)
                                        .filter(st -> !st.isEmpty())
                                        .map(ItemStackTemplate::fromNonEmptyStack)
                                        .toList();

                                bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(templates));
                                if (packDebug) {
                                        Mtgcard.LOGGER.info(
                                                "[MTGCard][PackDebug] Built reward bundle player={} uid={} set={} templateCount={} bundle={} rare={} foil={} inventoryBeforeDelivery={}",
                                                playerName,
                                                packUid,
                                                setLabel,
                                                templates.size(),
                                                PackInventoryUtil.describeStack(bundle),
                                                describeCardAtSlot(out, slots, RaritySlot.RARE_OR_MYTHIC),
                                                describeCardAtSlot(out, slots, RaritySlot.FOIL_RANDOM),
                                                PackInventoryUtil.describeInventoryState(player, preferredReturnSlot(active))
                                        );
                                }

                                PackInventoryUtil.DeliveryResult delivery =
                                        PackInventoryUtil.ejectLikeBundle(
                                                player,
                                                bundle,
                                                "pack_reward uid=" + packUid + " set=" + setLabel
                                        );
                                if (!delivery.success()) {
                                        throw new IllegalStateException("Pack reward delivery failed: " + delivery.mode());
                                }
                                delivered = true;
                                player.level().playSound(
                                        null,
                                        player.getX(),
                                        player.getY(),
                                        player.getZ(),
                                        SoundEvents.PLAYER_LEVELUP,
                                        SoundSource.PLAYERS,
                                        0.75f,
                                        1.0f
                                );

                                try {
                                        ModAdvancements.onBoosterPackOpened(player);
                                } catch (Throwable t) {
                                        com.spider.mtgcard.Mtgcard.LOGGER.error("[MTGCard] Pack advancement update failed", t);
                                }

                                ModPayloads.sendUnpackProgress(player, 100);
                                PackOpenManager.finish(player);
                                Mtgcard.LOGGER.info(
                                        "[MTGCard] Pack delivered for player={} uid={} set={} in {} ms via {} inventoryAfter={} rare={} foil={} contents={}",
                                        playerName,
                                        packUid,
                                        setLabel,
                                        elapsedMs,
                                        delivery.mode(),
                                        PackInventoryUtil.describeInventoryState(player, preferredReturnSlot(active)),
                                        describeCardAtSlot(out, slots, RaritySlot.RARE_OR_MYTHIC),
                                        describeCardAtSlot(out, slots, RaritySlot.FOIL_RANDOM),
                                        describeCardList(out)
                                );
                        } catch (Throwable t) {
                                markCancelled(active);
                                if (!delivered) {
                                        PackInventoryUtil.DeliveryResult refundResult = refundPackIfNeeded(player, active);
                                        logFailureOutcome(playerName, t, refundResult);
                                        ModPayloads.clearUnpackProgress(player);
                                } else {
                                        ModPayloads.sendUnpackProgress(player, 100);
                                }

                                PackOpenManager.finish(player);
                                Mtgcard.LOGGER.error(
                                        "[MTGCard] Pack finalization failed for player={} set={} after {} ms",
                                        playerName,
                                        setLabel,
                                        elapsedMs,
                                        t
                                );
                        }
                }));
        }

        private static ItemStack placeholderCard() {
                ItemStack st = new ItemStack(ModItems.CARD);
                // Optional: make it obvious it’s a fallback
                // st.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Unknown Card"));
                return st;
        }

        private static PackInventoryUtil.DeliveryResult refundPackIfNeeded(ServerPlayer player, PackOpenManager.Active active) {
                if (player == null || active == null || active.refundPackOne == null || active.refundPackOne.isEmpty()) {
                        return PackInventoryUtil.DeliveryResult.failed("no_refund");
                }

                ItemStack refund = active.refundPackOne.copy();
                if (refund.isEmpty()) return PackInventoryUtil.DeliveryResult.failed("empty_refund");

                boolean packDebug = MtgcardConfig.packDebugEnabled();
                if (packDebug) {
                        Mtgcard.LOGGER.info(
                                "[MTGCard][PackDebug] Attempting immediate refund player={} uid={} preferredSlot={} refund={} inventoryBefore={}",
                                player.getName().getString(),
                                active.packUid,
                                preferredReturnSlot(active),
                                PackInventoryUtil.describeStack(refund),
                                PackInventoryUtil.describeInventoryState(player, preferredReturnSlot(active))
                        );
                }
                PackInventoryUtil.DeliveryResult result =
                        PackInventoryUtil.giveOrDrop(
                                player,
                                refund,
                                preferredReturnSlot(active),
                                "pack_refund uid=" + active.packUid
                        );
                if (result.success()) {
                        if (packDebug) {
                                Mtgcard.LOGGER.info(
                                        "[MTGCard][PackDebug] Immediate refund delivered player={} uid={} mode={} inventoryAfter={}",
                                        player.getName().getString(),
                                        active.packUid,
                                        result.mode(),
                                        PackInventoryUtil.describeInventoryState(player, preferredReturnSlot(active))
                                );
                        }
                        return result;
                }

                var server = player.level().getServer();
                if (server != null) {
                        PackRefundState.get(server).addRefund(player.getUUID(), active.refundPackOne);
                        Mtgcard.LOGGER.error(
                                "[MTGCard] Immediate refund delivery failed for player={} uid={}, queued for rejoin. mode={} refund={}",
                                player.getName().getString(),
                                active.packUid,
                                result.mode(),
                                PackInventoryUtil.describeStack(active.refundPackOne)
                        );
                        return new PackInventoryUtil.DeliveryResult(true, "queued_refund");
                }

                return result;
        }

        private static void markCancelled(PackOpenManager.Active active) {
                if (active != null) {
                        active.cancelled = true;
                }
        }

        private static Throwable unwrapCompletion(Throwable throwable) {
                if (throwable == null) return null;

                Throwable current = throwable;
                while (current instanceof CompletionException && current.getCause() != null) {
                        current = current.getCause();
                }
                return current;
        }

        private static String failureMessageFor(Throwable failure, PackInventoryUtil.DeliveryResult refundResult) {
                boolean queued = refundResult != null && "queued_refund".equals(refundResult.mode());
                String suffix = queued
                        ? " Your pack refund was queued and will be returned when you rejoin."
                        : " Your pack was refunded.";

                if (failure instanceof TimeoutException) {
                        return "[MTGCard] Pack opening timed out on the server." + suffix;
                }
                return "[MTGCard] Pack opening failed." + suffix;
        }

        private static void logFailureOutcome(
                String playerName,
                Throwable failure,
                PackInventoryUtil.DeliveryResult refundResult
        ) {
                String message = failureMessageFor(failure, refundResult);
                Mtgcard.LOGGER.info("[MTGCard] {}", message.replace(" Your", " player=" + playerName + " | Your"));
        }

        private static String describePackSet(String desiredSet) {
                if (desiredSet == null || desiredSet.isBlank()) return "random";
                return desiredSet.trim().toLowerCase(Locale.ROOT);
        }

        private static int preferredReturnSlot(PackOpenManager.Active active) {
                return active == null ? -1 : active.preferredReturnSlot;
        }

        private static String deliveryDescription(PackInventoryUtil.DeliveryResult delivery) {
                if (delivery == null) return "was delivered";

                return switch (delivery.mode()) {
                        case "bundle_eject" -> "was ejected in front of you";
                        case "preferred_slot" -> "was returned to the slot you opened it from";
                        case "inventory", "inventory_unknown" -> "was added to your inventory";
                        case "drop", "inventory_and_drop" -> "was dropped near you";
                        case "queued_refund" -> "was queued for return";
                        default -> "was delivered";
                };
        }

        private static String describeCardAtSlot(
                List<ItemStack> cards,
                List<RaritySlot> slots,
                RaritySlot target
        ) {
                int idx = slots.indexOf(target);
                if (idx < 0 || idx >= cards.size()) return "unknown";
                return describeCard(cards.get(idx));
        }

        private static String describeCardList(List<ItemStack> cards) {
                if (cards == null || cards.isEmpty()) return "[]";

                ArrayList<String> out = new ArrayList<>(cards.size());
                for (ItemStack card : cards) {
                        out.add(describeCard(card));
                }
                return out.toString();
        }

        private static String describeCard(ItemStack stack) {
                if (stack == null || stack.isEmpty()) return "<empty>";

                String name = stack.getHoverName().getString();
                if (name == null || name.isBlank()) {
                        name = readMtgId(stack);
                }
                if (name == null || name.isBlank()) {
                        name = "<unknown>";
                }

                String set = readMetaString(stack, "set");
                String cn = readMetaString(stack, "collector_number");
                if (set.isBlank() && cn.isBlank()) return name;

                return name + " [" + set.toUpperCase(Locale.ROOT) + "/" + cn + "]";
        }

        private static String describeCardUrls(ItemStack stack) {
                if (stack == null || stack.isEmpty()) return "none";

                ArrayList<String> parts = new ArrayList<>(5);
                addUrlPart(parts, "scryfall", readMetaString(stack, "scryfall_uri"));
                addUrlPart(parts, "image", readMetaString(stack, "image_png"));

                ArrayList<String> faceUrls = readFaceImageUrls(stack);
                if (!faceUrls.isEmpty()) {
                        parts.add("faces=" + faceUrls);
                }

                String worldArtFront = readMetaString(stack, "world_art_front");
                String worldArtBack = readMetaString(stack, "world_art_back");
                String worldArt = readMetaString(stack, "world_art");
                if (!worldArtFront.isBlank() || !worldArtBack.isBlank() || !worldArt.isBlank()) {
                        parts.add(
                                "custom_art={front=" + blankToPlaceholder(worldArtFront)
                                        + ", back=" + blankToPlaceholder(worldArtBack)
                                        + ", active=" + blankToPlaceholder(worldArt) + "}"
                        );
                }

                return parts.isEmpty() ? "none" : String.join(", ", parts);
        }

        private static ArrayList<String> readFaceImageUrls(ItemStack stack) {
                ArrayList<String> faceUrls = new ArrayList<>();
                if (stack == null || stack.isEmpty()) return faceUrls;

                try {
                        var cd = stack.get(DataComponents.CUSTOM_DATA);
                        if (cd == null) return faceUrls;

                        CompoundTag root = cd.copyTag();
                        CompoundTag meta = root.getCompound("mtg_meta").orElse(null);
                        if (meta == null) return faceUrls;

                        var facesOpt = meta.getList("card_faces");
                        if (facesOpt.isEmpty()) return faceUrls;

                        ListTag faces = facesOpt.get();
                        for (int i = 0; i < faces.size(); i++) {
                                CompoundTag face = faces.getCompound(i).orElse(null);
                                if (face == null) continue;

                                String image = face.getString("image_png").orElse("");
                                if (!image.isBlank()) {
                                        faceUrls.add(i + ":" + image);
                                }
                        }
                } catch (Throwable ignored) {
                        return faceUrls;
                }

                return faceUrls;
        }

        private static void addUrlPart(List<String> parts, String label, String value) {
                if (parts == null || label == null || label.isBlank()) return;
                if (value == null || value.isBlank()) return;
                parts.add(label + "=" + value);
        }

        private static String blankToPlaceholder(String value) {
                return (value == null || value.isBlank()) ? "-" : value;
        }

        private static String readMetaString(ItemStack stack, String key) {
                if (stack == null || stack.isEmpty() || key == null || key.isBlank()) return "";

                try {
                        var cd = stack.get(DataComponents.CUSTOM_DATA);
                        if (cd == null) return "";

                        CompoundTag root = cd.copyTag();
                        CompoundTag meta = root.getCompound("mtg_meta").orElse(null);
                        if (meta == null) return "";
                        return meta.getString(key).orElse("");
                } catch (Throwable ignored) {
                        return "";
                }
        }

        private static CompletableFuture<ItemStack> makeCardAsyncNoDupe(
                MinecraftServer server,
                ServerPlayer player,
                RaritySlot slot,
                String desiredSet,
                CustomMode customMode,
                Set<String> seenCardKeys,
                int attemptsLeft
        ) {
                return makeCardAsync(server, player, slot, desiredSet, customMode)
                        .thenCompose(st -> {
                                if (!isResolvedPackCard(st)) {
                                        if (attemptsLeft <= 0) {
                                                return CompletableFuture.failedFuture(new IllegalStateException("Could not resolve " + slot + " pack card"));
                                        }
                                        return makeCardAsyncNoDupe(server, player, slot, desiredSet, customMode, seenCardKeys, attemptsLeft - 1);
                                }

                                String cardKey = readCanonicalCardKey(st);
                                if (cardKey != null && seenCardKeys.contains(cardKey)) {
                                        if (attemptsLeft <= 0) return CompletableFuture.completedFuture(st);
                                        return makeCardAsyncNoDupe(server, player, slot, desiredSet, customMode, seenCardKeys, attemptsLeft - 1);
                                }

                                if (cardKey != null) {
                                        seenCardKeys.add(cardKey);
                                }
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

                        if (isResolvedPackCard(custom)) {
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

                return fetchResolvedScryfallStackAsync(world, slot, q, finalScrySet, foilVisual);
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

        private static String readCanonicalCardKey(ItemStack st) {
                String oracleId = readMetaString(st, "oracle_id");
                if (!oracleId.isBlank()) {
                        return "oracle:" + oracleId.trim().toLowerCase(Locale.ROOT);
                }

                String name = readMetaString(st, "name");
                if (!name.isBlank()) {
                        return "name:" + name.trim().toLowerCase(Locale.ROOT);
                }

                String id = readMtgId(st);
                if (id != null && !id.isBlank()) {
                        return "id:" + id.trim().toLowerCase(Locale.ROOT);
                }

                return null;
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

        private static boolean isResolvedPackCard(ItemStack st) {
                return st != null && !st.isEmpty() && readMtgId(st) != null;
        }

        private static ItemStack requireResolvedCard(RaritySlot slot, ItemStack st) {
                if (isResolvedPackCard(st)) return st;
                throw new IllegalStateException("Pack slot " + slot + " produced an unresolved card stack");
        }

        private static CompletableFuture<ItemStack> fetchResolvedScryfallStackAsync(
                ServerLevel world,
                RaritySlot slot,
                ScryfallCache.Query q,
                @org.jetbrains.annotations.Nullable String preferredSet,
                boolean foilVisual
        ) {
                return fetchOneCardAsync(world, preferredSet, q, slot)
                        .thenCompose(card -> {
                                ItemStack built = buildResolvedScryfallStack(card, slot, foilVisual);
                                if (isResolvedPackCard(built)) {
                                        return CompletableFuture.completedFuture(built);
                                }

                                boolean alreadyGlobal = preferredSet == null || preferredSet.isBlank();
                                if (alreadyGlobal) {
                                        return CompletableFuture.failedFuture(new IllegalStateException("Could not build " + slot + " card from Scryfall"));
                                }

                                return fetchOneCardAsync(world, null, q, slot)
                                        .thenApply(globalCard -> requireResolvedCard(slot, buildResolvedScryfallStack(globalCard, slot, foilVisual)));
                        });
        }

        private static ItemStack buildResolvedScryfallStack(
                ScryfallModels.Card card,
                RaritySlot slot,
                boolean foilVisual
        ) {
                if (!scryfallMatchesSlot(card, slot)) {
                        if (card != null) {
                                Mtgcard.LOGGER.warn(
                                        "[MTGCard] Rejected Scryfall card for slot {}: name='{}' set={} rarity={} layout={} typeLine={}",
                                        slot,
                                        card.name,
                                        card.set,
                                        card.rarity,
                                        card.layout,
                                        card.typeLine
                                );
                        }
                        return ItemStack.EMPTY;
                }

                if (card != null && isExtraLikeScryfallCard(card)) {
                        card.isTokenLike = true;
                }

                ItemStack built = CardStackBuilders.buildScryfallStackFromModel(card, false);
                if (!isResolvedPackCard(built)) return ItemStack.EMPTY;

                // Safety: if token slipped, only allow it in the token slot.
                if (slot != RaritySlot.TOKEN_OR_ART && isTokenLike(built)) {
                        return ItemStack.EMPTY;
                }

                if (foilVisual) built.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                return built;
        }

        private static boolean scryfallMatchesSlot(ScryfallModels.Card card, RaritySlot slot) {
                if (card == null) return false;

                String typeLine = lower(card.typeLine);
                boolean isBasic = typeLine.contains("basic land");
                boolean isExtra = isExtraLikeScryfallCard(card);

                if (slot == RaritySlot.BASIC_LAND) return isBasic;
                if (slot == RaritySlot.TOKEN_OR_ART) return isExtra;
                if (isBasic || isExtra) return false;

                String rarity = normalizeRarity(card.rarity);
                return switch (slot) {
                        case COMMON -> rarity.equals("c");
                        case WILDCARD_C_OR_U -> rarity.equals("c") || rarity.equals("u");
                        case UNCOMMON -> rarity.equals("u");
                        case RARE_OR_MYTHIC -> rarity.equals("r") || rarity.equals("m");
                        case RANDOM, FOIL_RANDOM -> true;
                        case BASIC_LAND, TOKEN_OR_ART -> false;
                };
        }

        private static boolean isExtraLikeScryfallCard(ScryfallModels.Card card) {
                if (card == null) return false;

                String typeLine = lower(card.typeLine);
                String layout = lower(card.layout);

                return card.isTokenLike
                        || layout.contains("art_series")
                        || layout.contains("token")
                        || layout.contains("emblem")
                        || layout.contains("planar")
                        || layout.contains("scheme")
                        || layout.contains("vanguard")
                        || hasTypeWord(typeLine, "token")
                        || hasTypeWord(typeLine, "emblem")
                        || hasTypeWord(typeLine, "dungeon")
                        || hasTypeWord(typeLine, "attraction")
                        || hasTypeWord(typeLine, "sticker")
                        || hasTypeWord(typeLine, "contraption")
                        || hasTypeWord(typeLine, "scheme")
                        || hasTypeWord(typeLine, "plane")
                        || hasTypeWord(typeLine, "phenomenon")
                        || hasTypeWord(typeLine, "vanguard");
        }

        private static String normalizeRarity(String rarity) {
                if (rarity == null) return "";
                return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
                        case "c", "common" -> "c";
                        case "u", "uncommon" -> "u";
                        case "r", "rare" -> "r";
                        case "m", "mythic", "mythic rare" -> "m";
                        default -> rarity.trim().toLowerCase(Locale.ROOT);
                };
        }

        private static String lower(String s) {
                return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        }

        private static boolean hasTypeWord(String typeLine, String word) {
                if (typeLine == null || typeLine.isBlank()) return false;
                for (String token : typeLine.split("[^a-z]+")) {
                        if (token.equals(word)) return true;
                }
                return false;
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
